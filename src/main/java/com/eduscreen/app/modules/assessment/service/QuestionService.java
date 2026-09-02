package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.StatusTerbit;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bank soal: pembuatan, pengubahan, pencarian, dan penghapusan lunak Question beserta
 * Option-nya.
 */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final TaxonomyService taxonomy;
    private final ContentSanitizer sanitizer;
    private final ClientClock clock;

    public QuestionService(QuestionRepository questions,
                           QuestionOptionRepository options,
                           TaxonomyService taxonomy,
                           ContentSanitizer sanitizer,
                           ClientClock clock) {
        this.questions = questions;
        this.options = options;
        this.taxonomy = taxonomy;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    /** Satu pilihan jawaban mentah dari editor, sebelum disanitasi. */
    public record OptionDraft(String bodyHtml, boolean correct) {
    }

    /** Isi Question mentah dari editor, sebelum disanitasi (dipakai create dan update). */
    public record QuestionDraft(UUID topicId, QuestionType type, String bodyHtml,
                                 String explanationHtml, List<OptionDraft> options) {
    }

    /**
     * Pencarian menyentuh {@code body_text}, turunan teks polos dari {@code body_html}, bukan
     * kolom HTML itu sendiri (TC-25) — mencari markup akan salah memunculkan setiap soal
     * bergambar, dan kata yang terpotong tag tidak akan pernah ketemu.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> search(UUID clientId, UUID topicId, String q, Pageable pageable) {
        String pattern = ExerciseService.likePattern(q);
        return clientId == null
                ? questions.searchMaster(null, topicId, pattern, pageable)
                : questions.search(clientId, topicId, pattern, pageable);
    }

    /**
     * Pencarian panel perakit Exercise: seperti {@link #search}, ditambah saringan Paket, tipe
     * soal, dan pengecualian soal yang sudah terpasang di Exercise yang sedang dirakit.
     *
     * <p>{@code paketId} null berarti tidak difilter Paket sama sekali — perakit boleh menelusuri
     * lintas Paket dan Topic mana pun di dalam Client (BR-E01, FR-024); saringan ini hanya
     * mempersempit tampilan panel, bukan membatasi soal apa yang boleh masuk Exercise.
     *
     * <p>Practice hanya boleh memuat soal pilihan ganda (BR-M04), jadi Guru yang merakit untuk
     * Practice perlu menyingkirkan esai sebelum merakit, bukan setelah penerbitannya ditolak.
     *
     * <p>{@code excluded} kosong diganti UUID nil, bukan dibelokkan ke query lain: {@code not in ()}
     * tidak sah, sedangkan UUIDv7 tidak pernah nol sehingga sentinel itu tidak menyaring apa pun.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchForBuilder(UUID clientId, UUID paketId, UUID topicId, QuestionType type,
                                                 Collection<UUID> excluded, String q, Pageable pageable) {
        Collection<UUID> excludeIds = excluded == null || excluded.isEmpty()
                ? List.of(new UUID(0L, 0L))
                : excluded;
        return questions.searchForBuilder(
                clientId, paketId, topicId, type, excludeIds, ExerciseService.likePattern(q), pageable);
    }

    /**
     * Ruang kerja Eduscreen Admin. {@code status} null berarti draf dan terbit ditampilkan
     * berdampingan — keadaan bawaan ruang kerja, yang memang harus melihat keduanya.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchMaster(UUID subjectId, UUID topicId, String q,
                                             StatusTerbit status, Pageable pageable) {
        String pattern = ExerciseService.likePattern(q);
        return switch (status) {
            case null -> questions.searchMaster(subjectId, topicId, pattern, pageable);
            case DRAF -> questions.searchUnpublishedMaster(subjectId, topicId, pattern, pageable);
            case TERBIT -> questions.searchPublishedMaster(subjectId, topicId, pattern, pageable);
        };
    }

    /**
     * Soal satu Paket dikelompokkan per Topic, terurut {@code position} — halaman isi Paket.
     *
     * <p>Query di baliknya tidak menyaring Paket yang sudah ter-soft-delete, jadi pemanggil
     * wajib memastikan Paket-nya masih hidup lebih dulu lewat {@code PaketService.require}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<QuestionEntity>> groupByTopic(UUID paketId) {
        return questions.findByPaketIdOrderByPositionAsc(paketId).stream()
                .collect(Collectors.groupingBy(QuestionEntity::getTopicId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Merakit {@link QuestionDraft} dari parameter form editor; satu tempat, dipakai lebih dari
     * satu controller.
     *
     * <p>Tipe soal yang menentukan Option ikut atau tidak, bukan keberadaan parameternya:
     * editor menyembunyikan kolom pilihan lewat {@code x-show} saat tipenya esai, dan kolom
     * tersembunyi tetap terkirim oleh peramban.
     */
    public static QuestionDraft draftOf(UUID topicId, QuestionType type, String bodyHtml,
                                        String explanationHtml, List<String> optionBody,
                                        int correctIndex) {
        if (type == QuestionType.ESSAY || optionBody == null) {
            return new QuestionDraft(topicId, type, bodyHtml, explanationHtml, List.of());
        }
        List<OptionDraft> opsi = new ArrayList<>();
        for (int i = 0; i < optionBody.size(); i++) {
            opsi.add(new OptionDraft(optionBody.get(i), i == correctIndex));
        }
        return new QuestionDraft(topicId, type, bodyHtml, explanationHtml, opsi);
    }

    @Transactional(readOnly = true)
    public QuestionEntity require(UUID id, UUID clientId) {
        return questions.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public List<QuestionOptionEntity> optionsOf(UUID questionId) {
        return options.findByQuestionIdOrderByPositionAsc(questionId);
    }

    /**
     * Menulis soal ke dalam satu Paket.
     *
     * <p>Pemilik ({@code clientId}) datang sebagai parameter, bukan disimpulkan dari principal:
     * null berarti konten master milik Eduscreen (FR-060). Controller-lah yang memutuskan
     * nilainya — bank soal Client mengirim {@code requireClientId()}, ruang kerja master mengirim
     * null — sehingga kepemilikan terlihat di tanda tangan, sejalan dengan TC-36.
     *
     * <p>{@code paketId} adalah Paket TUJUAN, dan Topic yang ditunjuk draft wajib berada di dalam
     * Paket yang sama (AC-B02). Tanpa pemeriksaan ini, Topic dari Paket lain milik Client yang
     * sama bisa lolos — soal itu lalu tersimpan sewadah dengan Topic-nya tapi bukan di Paket
     * tempat penulis sedang bekerja, dan urutannya di dalam Paket kehilangan makna.
     */
    @Transactional
    public QuestionEntity create(QuestionDraft draft, UUID clientId, UUID paketId) {
        TopicEntity topic = requireTopicOf(draft.topicId(), paketId, clientId);

        String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
        if (bodyHtml.isBlank()) {
            throw new IllegalArgumentException("Isi soal tidak boleh kosong");
        }
        validateOptions(draft.type(), draft.options());

        QuestionEntity question = new QuestionEntity(
                clientId, paketId, topic.getId(), draft.type(),
                bodyHtml, sanitizer.toPlainText(bodyHtml));
        // Soal baru mendarat di ekor Topic-nya. Tanpa ini setiap soal lahir di posisi 0 dan
        // urutan yang dilihat penulis ditentukan kebetulan.
        question.moveTo(questions.nextPosition(topic.getId()));
        applyExplanation(question, draft.explanationHtml());
        question = questions.save(question);

        saveOptions(question.getId(), draft.options());
        return question;
    }

    /** Mengubah soal yang sudah ada; validasi Paket/Topic sama seperti {@link #create} (AC-B02). */
    @Transactional
    public QuestionEntity update(UUID id, QuestionDraft draft, UUID clientId, UUID paketId) {
        QuestionEntity question = require(id, clientId);
        TopicEntity topic = requireTopicOf(draft.topicId(), paketId, clientId);

        String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
        if (bodyHtml.isBlank()) {
            throw new IllegalArgumentException("Isi soal tidak boleh kosong");
        }
        validateOptions(draft.type(), draft.options());

        question.reparent(paketId, topic.getId());
        // Sanitasi dan turunan teks polos ditulis dalam operasi yang sama dengan bodyHtml,
        // supaya keduanya tidak pernah sempat tidak sinkron (TC-25).
        question.setBodyHtml(bodyHtml);
        question.setBodyText(sanitizer.toPlainText(bodyHtml));
        applyExplanation(question, draft.explanationHtml());
        question = questions.save(question);

        // Seluruh Option lama diganti daripada dicocokkan satu per satu: editor selalu
        // mengirim daftar penuh, dan penomoran ulang posisi jadi otomatis benar.
        options.deleteByQuestionId(question.getId());
        // flush() wajib di antara hapus dan sisip. Hibernate mengurutkan seluruh INSERT sebelum
        // seluruh DELETE dalam satu flush, sehingga indeks parsial question_option_single_correct
        // sempat melihat dua Option benar untuk soal yang sama dan menolak pembaruan apa pun atas
        // soal pilihan ganda. Memaksa hapusnya mendarat lebih dulu memulihkan urutan yang
        // sebenarnya diniatkan kode ini.
        options.flush();
        saveOptions(question.getId(), draft.options());
        return question;
    }

    /**
     * Topic yang boleh ditulisi DAN sewadah dengan Paket tujuan (AC-B02).
     *
     * <p>{@link TaxonomyService#requireWritableTopic} sudah menjamin kepemilikannya: Topic
     * berada di dalam Paket master untuk konten Eduscreen, atau di dalam Paket milik Client ini
     * (ADR-0018) — selain itu diperlakukan seolah tidak ada (TC-09). Yang belum ditegakkannya
     * adalah Topic itu berada di Paket yang SAMA dengan {@code paketId}: satu Client bisa punya
     * banyak Paket, dan Topic dari Paket lain yang sama-sama miliknya tetap lolos pemeriksaan
     * kepemilikan itu sendiri.
     */
    private TopicEntity requireTopicOf(UUID topicId, UUID paketId, UUID clientId) {
        TopicEntity topic = taxonomy.requireWritableTopic(topicId, clientId);
        if (!topic.getPaketId().equals(paketId)) {
            throw new IllegalArgumentException("Topic bukan milik Paket ini");
        }
        return topic;
    }

    @Transactional
    public void softDelete(UUID id, UUID clientId) {
        QuestionEntity question = require(id, clientId);
        // Soal hilang dari pencarian bank soal tapi tetap terbaca oleh Exercise dan sesi yang
        // sudah memakainya (FR-018) — itu ditegakkan lewat @SQLRestriction plus
        // findAllForSnapshot, bukan di sini.
        question.softDelete(clock.now());
        questions.save(question);
    }

    private void applyExplanation(QuestionEntity question, String explanationHtmlRaw) {
        if (explanationHtmlRaw == null || explanationHtmlRaw.isBlank()) {
            question.setExplanationHtml(null);
            question.setExplanationText(null);
            return;
        }
        String explanationHtml = sanitizer.sanitize(explanationHtmlRaw);
        question.setExplanationHtml(explanationHtml);
        question.setExplanationText(sanitizer.toPlainText(explanationHtml));
    }

    private void saveOptions(UUID questionId, List<OptionDraft> drafts) {
        int position = 0;
        for (OptionDraft draft : drafts) {
            String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
            options.save(new QuestionOptionEntity(
                    questionId, bodyHtml, sanitizer.toPlainText(bodyHtml), draft.correct(), position++));
        }
    }

    /**
     * MULTIPLE_CHOICE butuh minimal 2 Option dan tepat 1 benar (FR-016, AC-Q01); ESSAY tidak
     * boleh punya Option sama sekali karena jawabannya bebas dan dinilai manual.
     */
    private void validateOptions(QuestionType type, List<OptionDraft> drafts) {
        if (type == QuestionType.ESSAY) {
            if (drafts != null && !drafts.isEmpty()) {
                throw new IllegalArgumentException("Soal esai tidak boleh punya pilihan jawaban");
            }
            return;
        }
        if (drafts == null || drafts.size() < 2) {
            throw new IllegalArgumentException("Soal pilihan ganda butuh minimal 2 pilihan jawaban");
        }
        long correctCount = drafts.stream().filter(OptionDraft::correct).count();
        if (correctCount != 1) {
            throw new IllegalArgumentException("Soal pilihan ganda wajib punya tepat 1 pilihan benar");
        }
    }
}
