package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.ContentOrigin;
import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.domain.ClientClock;
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
    private final ContentSanitizer sanitizer;
    private final ClientClock clock;

    // ponytail: pratinjau di memori — topologi v1 satu instance (TC-42); pindah ke tabel bila mendatar.
    private final Map<String, PendingPreview> previews = new ConcurrentHashMap<>();

    public QuestionImportService(QuestionImportParser parser,
                                 QuestionRepository questions,
                                 QuestionOptionRepository options,
                                 TopicRepository topics,
                                 ContentSanitizer sanitizer,
                                 ClientClock clock) {
        this.parser = parser;
        this.questions = questions;
        this.options = options;
        this.topics = topics;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    public record Preview(String token, int validCount, List<QuestionImportParser.RowFailure> failures) {}

    public record ImportSummary(int saved) {}

    private record PendingPreview(UUID clientId, List<QuestionImportParser.RawRow> rows, OffsetDateTime expiresAt) {}

    /**
     * Menguraikan dan memvalidasi berkas tanpa menyimpan apa pun. Baris dihitung LEBIH DULU,
     * sebelum diuraikan isinya, supaya berkas raksasa ditolak murah tanpa membebani parser
     * (AC-Q06).
     */
    public Preview preview(String filename, byte[] content, UUID clientId) {
        int rowCount = parser.countRows(filename, content);
        if (rowCount > MAX_ROWS) {
            throw new UnprocessableException(
                    "Berkas berisi " + rowCount + " baris data, melebihi batas " + MAX_ROWS
                            + " baris per impor. Pecah berkas menjadi beberapa bagian, lalu unggah ulang satu per satu (TC-45).");
        }

        QuestionImportParser.ParseResult result = parser.parse(filename, content);
        String token = UUID.randomUUID().toString();
        previews.put(token, new PendingPreview(clientId, result.valid(), clock.now().plus(PREVIEW_TTL)));
        return new Preview(token, result.valid().size(), result.failures());
    }

    /**
     * Menyimpan hasil pratinjau. Hanya baris valid yang tersimpan (FR-022): baris yang topic-nya
     * tak dikenali Client ini dilewati tanpa membatalkan baris lain, dan tidak ikut terhitung di
     * {@link ImportSummary#saved()}.
     */
    @Transactional
    public ImportSummary commit(String token, UUID clientId, UUID author) {
        PendingPreview pending = previews.get(token);
        if (pending == null || !pending.clientId().equals(clientId)) {
            throw new IllegalArgumentException("Pratinjau sudah kedaluwarsa, unggah ulang berkasnya");
        }
        if (pending.expiresAt().isBefore(clock.now())) {
            previews.remove(token);
            throw new IllegalArgumentException("Pratinjau sudah kedaluwarsa, unggah ulang berkasnya");
        }
        previews.remove(token);

        int saved = 0;
        for (QuestionImportParser.RawRow row : pending.rows()) {
            TopicEntity topic = findVisibleTopicByName(row.topic(), clientId);
            if (topic == null) {
                continue;
            }
            saveQuestion(row, topic, clientId, author);
            saved++;
        }
        return new ImportSummary(saved);
    }

    private void saveQuestion(QuestionImportParser.RawRow row, TopicEntity topic, UUID clientId, UUID author) {
        QuestionType type = "PG".equals(row.type()) ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;

        // Konten impor melewati sanitasi yang SAMA dengan editor manual (TC-22) — tidak ada
        // jalur pintas untuk berkas. Rumus matematika masuk sebagai LaTeX berdelimiter dan
        // tidak dirender di server (TC-24); sanitasi HTML tidak menyentuhnya.
        QuestionEntity question = new QuestionEntity(clientId, topic.getId(), type,
                sanitizer.sanitize(row.body()), sanitizer.toPlainText(row.body()));
        question.setCreatedBy(author);
        if (row.explanation() != null && !row.explanation().isBlank()) {
            question.setExplanationHtml(sanitizer.sanitize(row.explanation()));
            question.setExplanationText(sanitizer.toPlainText(row.explanation()));
        }
        questions.save(question);

        if (type == QuestionType.MULTIPLE_CHOICE) {
            char correctLetter = row.answerKey().charAt(0);
            List<String> raw = row.options();
            int position = 0;
            for (int i = 0; i < raw.size(); i++) {
                String value = raw.get(i);
                if (value == null || value.isBlank()) {
                    continue;
                }
                boolean correct = (char) ('A' + i) == correctLetter;
                options.save(new QuestionOptionEntity(question.getId(),
                        sanitizer.sanitize(value), sanitizer.toPlainText(value), correct, position));
                position++;
            }
        }
    }

    /**
     * Topic yang "terlihat" satu Client = GLOBAL milik Eduscreen atau miliknya sendiri,
     * dicocokkan berdasarkan nama tanpa peduli huruf besar/kecil (§Impor massal).
     *
     * <p>Tidak ada method repository siap pakai untuk mencari Topic lintas Subject hanya
     * berdasarkan nama tanpa {@code subjectId} — kolom {@code topic} pada berkas impor tidak
     * membawa Subject. {@code TopicRepository} sengaja tidak diubah di sini (di luar cakupan
     * tugas ini), sehingga penyaringan dilakukan di lapisan service memakai {@code findAll()}
     * bawaan; batas tenant (TC-36) tetap ditegakkan secara eksplisit lewat filter di bawah,
     * bukan diserahkan ke query.
     */
    private TopicEntity findVisibleTopicByName(String name, UUID clientId) {
        return topics.findAll().stream()
                .filter(t -> t.getOrigin() == ContentOrigin.GLOBAL || clientId.equals(t.getClientId()))
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
