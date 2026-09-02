package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adopsi konten master Eduscreen oleh satu Client: copy-on-adopt (ADR-0001, FR-021).
 *
 * <p>Adopsi membuat <b>salinan penuh</b>, bukan referensi ke baris master. Alasannya bukan
 * teknis melainkan operasional: sekolah yang sudah menyesuaikan soal (mengubah redaksi,
 * memperbaiki kunci jawaban, menambah pembahasan) tidak boleh mendapat perubahan dari Eduscreen
 * di tengah semester begitu master-nya direvisi. Setelah adopsi, salinan Client hidup sendiri —
 * {@code sourceQuestionId} hanya jejak asal untuk ditelusuri, bukan tautan yang disinkronkan.
 *
 * <p>Subject {@code GLOBAL} sengaja TIDAK pernah disalin (BR-O02, AC-O02): ia dibaca langsung
 * oleh semua Client, sehingga menyalinnya hanya akan menggandakan data tanpa tujuan. Yang
 * disalin adalah Topic, Question beserta Option, dan Exercise beserta ExerciseItem.
 */
@Service
public class ContentAdoptionService {

    private final TopicRepository topics;
    private final PaketRepository pakets;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final ExerciseRepository exercises;
    private final ExerciseItemRepository exerciseItems;

    public ContentAdoptionService(TopicRepository topics,
                                  PaketRepository pakets,
                                  QuestionRepository questions,
                                  QuestionOptionRepository options,
                                  ExerciseRepository exercises,
                                  ExerciseItemRepository exerciseItems) {
        this.topics = topics;
        this.pakets = pakets;
        this.questions = questions;
        this.options = options;
        this.exercises = exercises;
        this.exerciseItems = exerciseItems;
    }

    public record AdoptionSummary(int topics, int questions, int exercises) {}

    /**
     * Pengenal konten master yang sudah pernah diadopsi Client ini, di antara {@code masterIds}
     * yang sedang ditampilkan (FR-076, FR-077).
     *
     * <p>Dibaca dari {@code sourceQuestionId} yang sudah ditulis sejak adopsi pertama — jejak
     * asal yang memang dirancang untuk ditelusuri (ADR-0001), bukan tabel jejak baru. Dibatasi
     * pada satu halaman katalog supaya biayanya tetap datar berapa pun besar katalognya.
     */
    @Transactional(readOnly = true)
    public Set<UUID> adoptedSourceIds(UUID clientId, Collection<UUID> masterIds) {
        if (masterIds == null || masterIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(questions.findAdoptedSourceIds(clientId, masterIds));
    }

    /**
     * Apakah Client ini sudah pernah mengadopsi Topic master yang sedang disaring di katalog
     * (FR-076, FR-077).
     *
     * <p>Satu pertanyaan per tampilan, bukan per baris: katalog menyaring paling banyak satu
     * Topic sekaligus, jadi biayanya tidak tumbuh bersama besarnya halaman (SC-015).
     */
    @Transactional(readOnly = true)
    public boolean hasAdoptedTopic(UUID clientId, UUID masterTopicId) {
        if (masterTopicId == null) {
            return false;
        }
        // sementara sampai Task 8: jejak adopsi pindah dari Topic ke Paket (ADR-0018), jadi
        // pertanyaan lama "sudah pernah ambil Topic ini?" dijawab lewat Paket induknya. Adopsi
        // per Paket menggantikan seluruh jalur ini di Task 8.
        return topics.findById(masterTopicId)
                .map(topic -> pakets.existsByClientIdAndSourcePaketId(clientId, topic.getPaketId()))
                .orElse(false);
    }

    /** Adopsi soal lepas dari katalog master, di luar konteks Exercise (FR-021). */
    @Transactional
    public AdoptionSummary adoptQuestions(UUID clientId, List<UUID> questionIds, UUID actor) {
        Map<UUID, TopicEntity> topicMap = new HashMap<>();
        int copiedQuestions = 0;
        for (UUID masterId : questionIds) {
            QuestionEntity master = requireMasterQuestion(masterId);
            copyQuestion(master, clientId, actor, topicMap);
            copiedQuestions++;
        }
        return new AdoptionSummary(topicMap.size(), copiedQuestions, 0);
    }

    /** Adopsi paket Exercise: menyalin Exercise, seluruh ExerciseItem, dan tiap Question yang dirujuknya (FR-021). */
    @Transactional
    public AdoptionSummary adoptExercises(UUID clientId, List<UUID> exerciseIds, UUID actor) {
        Map<UUID, TopicEntity> topicMap = new HashMap<>();
        // Dedup soal per pemanggilan: dua Exercise yang diadopsi bersamaan bisa merujuk soal
        // yang sama, dan soal itu hanya perlu satu salinan (sejalan dengan aturan dedup Topic).
        Map<UUID, UUID> questionMap = new HashMap<>();
        int copiedExercises = 0;

        for (UUID masterExerciseId : exerciseIds) {
            // Hanya paket TERBIT yang bisa diadopsi (FR-067). Paket yang masih digarap dan
            // paket yang tidak ada sama-sama 404, sehingga menebak pengenal tidak membuktikan
            // apa pun (TC-09).
            ExerciseEntity masterExercise = exercises.findPublishedMasterById(masterExerciseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Exercise master tidak ditemukan"));

            // Lahir dengan lockedAt kosong: Client boleh menyusun ulang isinya sebelum
            // menerbitkan Assignment pertamanya sendiri (FR-026).
            ExerciseEntity copy = exercises.save(new ExerciseEntity(clientId, masterExercise.getTitle(), actor));

            for (ExerciseItemEntity item : exerciseItems.findByExerciseIdOrderByPositionAsc(masterExerciseId)) {
                UUID copiedQuestionId = questionMap.get(item.getQuestionId());
                if (copiedQuestionId == null) {
                    QuestionEntity masterQuestion = requireMasterQuestion(item.getQuestionId());
                    QuestionEntity copiedQuestion = copyQuestion(masterQuestion, clientId, actor, topicMap);
                    copiedQuestionId = copiedQuestion.getId();
                    questionMap.put(item.getQuestionId(), copiedQuestionId);
                }
                exerciseItems.save(new ExerciseItemEntity(copy.getId(), copiedQuestionId, item.getPosition()));
            }
            copiedExercises++;
        }
        return new AdoptionSummary(topicMap.size(), questionMap.size(), copiedExercises);
    }

    /**
     * Hanya Question master TERBIT yang bisa diadopsi (FR-067).
     *
     * <p>Gerbang FR-069 menjamin paket terbit tidak memuat Question yang belum terbit, jadi
     * adopsi lewat paket tidak akan tersandung di sini; yang dijaga method ini adalah adopsi
     * per Question yang pengenalnya datang langsung dari klien.
     */
    private QuestionEntity requireMasterQuestion(UUID id) {
        return questions.findPublishedMasterById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soal master tidak ditemukan"));
    }

    /** Menyalin satu Question beserta Option-nya, menyelesaikan Topic induknya sekali per pemanggilan. */
    private QuestionEntity copyQuestion(QuestionEntity master, UUID clientId, UUID actor,
                                        Map<UUID, TopicEntity> topicMap) {
        TopicEntity copiedTopic = topicMap.computeIfAbsent(
                master.getTopicId(), masterTopicId -> copyTopic(masterTopicId, clientId, actor));

        // sementara sampai Task 8: Paket induk salinan diturunkan dari Topic salinan yang baru
        // ditulis. Task 8 mengadopsi per Paket, sehingga Paket tujuan datang dari pemanggil.
        QuestionEntity copy = new QuestionEntity(clientId, copiedTopic.getPaketId(),
                copiedTopic.getId(), master.getType(),
                master.getBodyHtml(), master.getBodyText());
        copy.setExplanationHtml(master.getExplanationHtml());
        copy.setExplanationText(master.getExplanationText());
        // Jejak adopsi saja (ADR-0001): tidak ada sinkronisasi lanjutan dari master ini.
        copy.setSourceQuestionId(master.getId());
        copy.setCreatedBy(actor);
        questions.save(copy);

        for (QuestionOptionEntity masterOption : options.findByQuestionIdOrderByPositionAsc(master.getId())) {
            options.save(new QuestionOptionEntity(copy.getId(), masterOption.getBodyHtml(), masterOption.getBodyText(),
                    masterOption.isCorrect(), masterOption.getPosition()));
        }
        return copy;
    }

    /**
     * Topic master disalin bersama Paket yang menaunginya, tetap menunjuk Subject global yang
     * sama — {@code subjectId} tidak berubah, karena Subject GLOBAL itu sendiri tidak pernah
     * disalin (BR-O02).
     *
     * <p>Adopsi kedua sengaja melahirkan Paket dan Topic baru, bukan memakai ulang yang sudah
     * ada (FR-077). Yang mencegah penggandaan tak sengaja adalah peringatan di katalog, bukan
     * penolakan diam-diam di sini: Client Admin yang memang ingin salinan kedua tetap boleh
     * mendapatkannya, sama seperti aturan yang sudah berlaku untuk Question.
     *
     * <p>{@code sourcePaketId} yang membuat peringatan itu mungkin. Bukan pencocokan nama:
     * master yang di-rename Eduscreen dan salinan yang dirapikan Guru sama-sama membuat
     * tebakan berdasarkan nama meleset, dan ke dua arah sekaligus.
     */
    private TopicEntity copyTopic(UUID masterTopicId, UUID clientId, UUID actor) {
        TopicEntity master = topics.findById(masterTopicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic master tidak ditemukan"));
        PaketEntity masterPaket = pakets.findById(master.getPaketId())
                .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));
        PaketEntity paketCopy = pakets.save(PaketEntity.adoptedFrom(clientId,
                masterPaket.getSubjectId(), masterPaket.getTitle(), actor, masterPaket.getId()));
        return topics.save(TopicEntity.of(paketCopy.getId(), master.getTitle(), master.getPosition()));
    }
}
