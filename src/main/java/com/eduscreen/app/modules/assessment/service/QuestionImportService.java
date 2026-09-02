package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.shared.web.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Impor massal soal dari berkas Excel/CSV, dalam dua langkah: {@link #preview} menguraikan dan
 * memvalidasi tanpa menulis apa pun, {@link #commit} menyimpan hanya baris yang valid.
 *
 * <p>Sejak ADR-0018, tujuan impor dipilih EKSPLISIT di layar impor: seluruh baris valid mendarat
 * di satu Paket dan satu Topic milik Paket itu (AC-B02). Kolom {@code topic} pada berkas tidak
 * lagi menentukan tujuan — pencocokan nama Topic lintas Paket membuat hasil impor bergantung
 * pada kebetulan judul, bukan pada niat pengunggah.
 *
 * <p>Diproses sepenuhnya SINKRON, tanpa antrean pekerjaan latar (TC-45, ADR-0014). Batas
 * {@value #MAX_ROWS} baris per berkas ADA justru supaya itu tetap benar: ia menjamin satu
 * request HTTP selesai dalam waktu wajar. Jangan "perbaiki" keterbatasan ini dengan memasukkan
 * kembali infrastruktur pekerjaan latar — itu menghidupkan kembali persis apa yang ADR-0014
 * sengaja hindari.
 */
@Service
public class QuestionImportService {

    private static final int MAX_ROWS = 500;
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    private final QuestionImportParser parser;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final TopicRepository topics;
    private final PaketService pakets;
    private final ContentSanitizer sanitizer;
    private final ClientClock clock;

    // ponytail: pratinjau di memori — topologi v1 satu instance (TC-42); pindah ke tabel bila mendatar.
    private final Map<String, PendingPreview> previews = new ConcurrentHashMap<>();

    public QuestionImportService(QuestionImportParser parser,
                                 QuestionRepository questions,
                                 QuestionOptionRepository options,
                                 TopicRepository topics,
                                 PaketService pakets,
                                 ContentSanitizer sanitizer,
                                 ClientClock clock) {
        this.parser = parser;
        this.questions = questions;
        this.options = options;
        this.topics = topics;
        this.pakets = pakets;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    public record Preview(String token, int validCount, List<QuestionImportParser.RowFailure> failures) {}

    public record ImportSummary(int saved) {}

    private record PendingPreview(UUID clientId, UUID paketId, List<QuestionImportParser.RawRow> rows,
                                  OffsetDateTime expiresAt) {}

    /**
     * Menguraikan dan memvalidasi berkas tanpa menyimpan apa pun. Paket tujuan diperiksa lebih
     * dulu: milik Client lain dijawab 404, bukan 403 (TC-36), sebelum satu byte pun berkasnya
     * diproses. Baris dihitung sebelum diuraikan isinya, supaya berkas raksasa ditolak murah
     * tanpa membebani parser (AC-Q06).
     */
    public Preview preview(String filename, byte[] content, UUID paketId, UUID clientId) {
        pakets.require(paketId, clientId);

        int rowCount = parser.countRows(filename, content);
        if (rowCount > MAX_ROWS) {
            throw new UnprocessableException(
                    "Berkas berisi " + rowCount + " baris data, melebihi batas " + MAX_ROWS
                            + " baris per impor. Pecah berkas menjadi beberapa bagian, lalu unggah ulang satu per satu (TC-45).");
        }

        QuestionImportParser.ParseResult result = parser.parse(filename, content);
        String token = UUID.randomUUID().toString();
        previews.put(token, new PendingPreview(clientId, paketId, result.valid(), clock.now().plus(PREVIEW_TTL)));
        return new Preview(token, result.valid().size(), result.failures());
    }

    /**
     * Menyimpan hasil pratinjau ke Topic tujuan. Hanya baris valid yang sampai ke sini —
     * baris gagal sudah tersaring dan dilaporkan saat pratinjau, tanpa membatalkan baris lain
     * (FR-022, AC-Q03).
     *
     * <p>Paket diperiksa ulang lewat {@code PaketService.require} walau pratinjau sudah
     * memeriksanya: pratinjau hidup 30 menit, cukup lama untuk Paket-nya keburu dihapus atau
     * URL-nya diutak-atik. Topic tujuan wajib milik Paket tujuan (AC-B02) — tanpa gerbang itu,
     * Topic dari Paket lain milik Client yang sama bisa lolos dan soal mendarat di luar Paket
     * tempat pengunggah sedang bekerja.
     */
    @Transactional
    public ImportSummary commit(String token, UUID paketId, UUID topicId, UUID clientId, UUID author) {
        pakets.require(paketId, clientId);

        PendingPreview pending = previews.get(token);
        if (pending == null || !pending.clientId().equals(clientId) || !pending.paketId().equals(paketId)) {
            throw new IllegalArgumentException("Pratinjau sudah kedaluwarsa, unggah ulang berkasnya");
        }
        if (pending.expiresAt().isBefore(clock.now())) {
            previews.remove(token);
            throw new IllegalArgumentException("Pratinjau sudah kedaluwarsa, unggah ulang berkasnya");
        }
        previews.remove(token);

        TopicEntity topic = topics.findWritable(topicId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic tidak ditemukan"));
        if (!topic.getPaketId().equals(paketId)) {
            throw new IllegalArgumentException("Topic bukan milik Paket ini");
        }

        // Posisi awal dihitung sekali untuk seluruh berkas, lalu berjalan naik per baris:
        // soal impor mendarat berurutan di ekor Topic, tidak menumpuk di posisi soal yang
        // sudah ada (AC-B08).
        int position = questions.nextPosition(topic.getId());
        int saved = 0;
        for (QuestionImportParser.RawRow row : pending.rows()) {
            saveQuestion(row, paketId, topic.getId(), clientId, author, position++);
            saved++;
        }
        return new ImportSummary(saved);
    }

    private void saveQuestion(QuestionImportParser.RawRow row, UUID paketId, UUID topicId,
                              UUID clientId, UUID author, int position) {
        QuestionType type = "PG".equals(row.type()) ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;

        // Konten impor melewati sanitasi yang SAMA dengan editor manual (TC-22) — tidak ada
        // jalur pintas untuk berkas. Rumus matematika masuk sebagai LaTeX berdelimiter dan
        // tidak dirender di server (TC-24); sanitasi HTML tidak menyentuhnya.
        QuestionEntity question = new QuestionEntity(clientId, paketId, topicId, type,
                sanitizer.sanitize(row.body()), sanitizer.toPlainText(row.body()));
        question.moveTo(position);
        question.setCreatedBy(author);
        if (row.explanation() != null && !row.explanation().isBlank()) {
            question.setExplanationHtml(sanitizer.sanitize(row.explanation()));
            question.setExplanationText(sanitizer.toPlainText(row.explanation()));
        }
        questions.save(question);

        if (type == QuestionType.MULTIPLE_CHOICE) {
            char correctLetter = row.answerKey().charAt(0);
            List<String> raw = row.options();
            int optionPosition = 0;
            for (int i = 0; i < raw.size(); i++) {
                String value = raw.get(i);
                if (value == null || value.isBlank()) {
                    continue;
                }
                boolean correct = (char) ('A' + i) == correctLetter;
                options.save(new QuestionOptionEntity(question.getId(),
                        sanitizer.sanitize(value), sanitizer.toPlainText(value), correct, optionPosition));
                optionPosition++;
            }
        }
    }
}
