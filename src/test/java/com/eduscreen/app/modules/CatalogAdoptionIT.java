package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.service.ContentAdoptionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Katalog granular dan adopsinya (FR-074 sampai FR-079), plus pembuktian bahwa hasil adopsi
 * berperilaku persis seperti soal buatan sekolah sendiri di tangan Guru (FR-024, SC-018).
 *
 * <p>Perakitan paket master (FR-071 sampai FR-073) ikut diuji di sini karena paket adalah satuan
 * yang katalog tawarkan; gerbang penerbitannya sendiri diuji di {@code MasterContentIT}.
 */
class CatalogAdoptionIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    QuestionService questionService;
    @Autowired
    ExerciseService exerciseService;
    @Autowired
    ContentAdoptionService adoption;
    @Autowired
    MasterPublishingService publishing;
    @Autowired
    TaxonomyService taxonomy;
    @Autowired
    SubjectRepository subjects;

    // -------------------------------------------------------------- paket master

    @Test
    @DisplayName("AC-E02 (FR-071): paket master memuat soal lintas Subject tanpa peringatan apa pun")
    void paketMasterLintasSubject() {
        TopicEntity pecahan = data.globalTopic("Matematika Kelas 4", "Pecahan");
        TopicEntity gerak = data.globalTopic("Fisika Kelas 9", "Gerak Lurus");
        QuestionEntity a = data.masterMcq(pecahan, "Soal pecahan");
        QuestionEntity b = data.masterMcq(gerak, "Soal gerak");
        ExerciseEntity paket = data.masterExercise("Paket lintas Subject", List.of());

        exerciseService.addQuestion(paket.getId(), a.getId(), null);
        exerciseService.addQuestion(paket.getId(), b.getId(), null);

        assertThat(exerciseService.itemsOf(paket.getId())).hasSize(2);
    }

    @Test
    @DisplayName("BR-E04 (FR-073): paket master tidak pernah terkunci, jadi isinya tetap bisa diubah setelah diadopsi banyak Client")
    void paketMasterTidakPernahTerkunci() {
        ClientEntity clientA = data.client("SD Katalog1");
        ClientEntity clientB = data.client("SD Katalog2");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal paket");
        ExerciseEntity paket = data.masterExercise("Paket dipakai banyak Client", List.of(terbit));
        publishing.publishExercise(paket.getId());

        adoption.adoptExercises(clientA.getId(), List.of(paket.getId()), null);
        adoption.adoptExercises(clientB.getId(), List.of(paket.getId()), null);

        QuestionEntity tambahan = data.publishedMasterMcq(topic, "Soal tambahan");
        exerciseService.addQuestion(paket.getId(), tambahan.getId(), null);

        assertThat(exerciseService.require(paket.getId(), null).isLocked()).isFalse();
        assertThat(exerciseService.itemsOf(paket.getId())).hasSize(2);
        // Salinan masing-masing Client tetap berisi satu soal: perubahan master tidak merambat.
        for (ClientEntity client : List.of(clientA, clientB)) {
            ExerciseEntity salinan = exerciseService.list(client.getId(), null, PageRequest.of(0, 20))
                    .getContent().get(0);
            assertThat(exerciseService.itemsOf(salinan.getId())).hasSize(1);
        }
    }

    // ------------------------------------------------------------ katalog granular

    @Test
    @DisplayName("AC-O02 (FR-074, FR-075, FR-078): katalog menyaring per Subject dan Topic, dan adopsi terpilih menjadi salinan milik Client tanpa Subject baru")
    void katalogGranularDanAdopsiTerpilih() {
        ClientEntity client = data.client("SD Katalog3");
        TopicEntity pecahan = data.globalTopic("Matematika Kelas 4", "Pecahan");
        TopicEntity gerak = data.globalTopic("Fisika Kelas 9", "Gerak Lurus");
        for (int i = 0; i < 3; i++) {
            data.publishedMasterMcq(pecahan, "Pecahan katalogunik " + i);
        }
        data.publishedMasterMcq(gerak, "Gerak katalogunik lain");

        Page<QuestionEntity> semua = questionService.searchPublishedMaster(
                null, null, "katalogunik", PageRequest.of(0, 20));
        Page<QuestionEntity> perTopic = questionService.searchPublishedMaster(
                null, pecahan.getId(), "katalogunik", PageRequest.of(0, 20));
        Page<QuestionEntity> perSubject = questionService.searchPublishedMaster(
                data.subjectIdOf(gerak), null, "katalogunik", PageRequest.of(0, 20));

        assertThat(semua.getTotalElements()).isEqualTo(4);
        assertThat(perTopic.getTotalElements()).isEqualTo(3);
        assertThat(perSubject.getTotalElements()).isEqualTo(1);

        List<UUID> dipilih = perTopic.getContent().stream().map(QuestionEntity::getId).limit(2).toList();
        long subjectSebelum = subjects.count();

        ContentAdoptionService.AdoptionSummary ringkasan =
                adoption.adoptQuestions(client.getId(), dipilih, null);

        // Ringkasan menyebut apa yang tersalin (FR-079).
        assertThat(ringkasan.questions()).isEqualTo(2);
        assertThat(ringkasan.topics()).isEqualTo(1);
        assertThat(ringkasan.exercises()).isZero();

        Page<QuestionEntity> bank = questionService.search(
                client.getId(), null, "katalogunik", PageRequest.of(0, 20));
        assertThat(bank.getTotalElements()).isEqualTo(2);
        assertThat(bank.getContent()).allSatisfy(q ->
                assertThat(q.getClientId()).isEqualTo(client.getId()));

        // Subject GLOBAL tidak pernah disalin; yang disalin Topic, Question, dan Exercise
        // (BR-O02, AC-O02).
        assertThat(subjects.count()).isEqualTo(subjectSebelum);
    }

    @Test
    @DisplayName("BR-Q04 (FR-076, FR-077): Topic hasil adopsi membawa jejak asalnya, dan adopsi kedua diperingatkan tapi tetap boleh melahirkan Topic baru")
    void jejakAsalTopicDanPeringatanAdopsiBerulang() {
        ClientEntity client = data.client("SD Katalog9");
        ClientEntity lain = data.client("SD Katalog10");
        TopicEntity pecahan = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity senin = data.publishedMasterMcq(pecahan, "Pecahan jejakunik 1");
        QuestionEntity jumat = data.publishedMasterMcq(pecahan, "Pecahan jejakunik 2");

        // Belum pernah mengambil: tidak ada yang perlu diperingatkan.
        assertThat(adoption.hasAdoptedTopic(client.getId(), pecahan.getId())).isFalse();

        adoption.adoptQuestions(client.getId(), List.of(senin.getId()), null);

        // Jejak asal tercatat, dan Topic salinan tetap punya id sendiri (ADR-0001).
        List<TopicEntity> setelahPertama = milikClient(pecahan, client);
        assertThat(setelahPertama).hasSize(1);
        assertThat(setelahPertama.getFirst().getId()).isNotEqualTo(pecahan.getId());
        assertThat(data.paketOf(setelahPertama.getFirst()).getSourcePaketId())
                .isEqualTo(data.paketOf(pecahan).getId());

        // Peringatan menyala untuk sekolah yang sudah mengambil, dan hanya untuk sekolah itu.
        assertThat(adoption.hasAdoptedTopic(client.getId(), pecahan.getId())).isTrue();
        assertThat(adoption.hasAdoptedTopic(lain.getId(), pecahan.getId())).isFalse();

        // Peringatan, bukan gerbang: adopsi kedua tetap jalan dan melahirkan Topic baru (FR-077).
        var adopsiKedua = adoption.adoptQuestions(client.getId(), List.of(jumat.getId()), null);
        assertThat(adopsiKedua.topics()).isEqualTo(1);
        assertThat(milikClient(pecahan, client))
                .hasSize(2)
                .allSatisfy(t -> assertThat(data.paketOf(t).getSourcePaketId())
                        .isEqualTo(data.paketOf(pecahan).getId()));
    }

    private List<TopicEntity> milikClient(TopicEntity master, ClientEntity client) {
        return taxonomy.visibleTopics(data.subjectIdOf(master), client.getId()).stream()
                .filter(t -> client.getId().equals(data.paketOf(t).getClientId()))
                .toList();
    }

    @Test
    @DisplayName("BR-Q04 (FR-076, FR-077): katalog menandai konten yang sudah pernah diadopsi Client yang sedang melihatnya")
    void penandaSudahDiadopsi() {
        ClientEntity client = data.client("SD Katalog4");
        ClientEntity clientLain = data.client("SD Katalog5");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity diadopsi = data.publishedMasterMcq(topic, "Soal diadopsi");
        QuestionEntity belum = data.publishedMasterMcq(topic, "Soal belum diadopsi");

        adoption.adoptQuestions(client.getId(), List.of(diadopsi.getId()), null);

        Set<UUID> penanda = adoption.adoptedSourceIds(
                client.getId(), List.of(diadopsi.getId(), belum.getId()));
        assertThat(penanda).containsExactly(diadopsi.getId());

        // Penandanya milik satu Client saja; adopsi sekolah lain tidak boleh ikut terlihat.
        assertThat(adoption.adoptedSourceIds(
                clientLain.getId(), List.of(diadopsi.getId(), belum.getId()))).isEmpty();
    }

    @Test
    @DisplayName("AC-O01 (FR-078): adopsi paket terbit menyalin Exercise beserta seluruh soalnya menjadi milik Client")
    void adopsiPaketMenyalinSeluruhIsinya() {
        ClientEntity client = data.client("SD Katalog6");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        List<QuestionEntity> isi = List.of(
                data.publishedMasterMcq(topic, "Isi paket 1"),
                data.publishedMasterMcq(topic, "Isi paket 2"));
        ExerciseEntity paket = data.masterExercise("Paket adopsi utuh", isi);
        publishing.publishExercise(paket.getId());

        ContentAdoptionService.AdoptionSummary ringkasan =
                adoption.adoptExercises(client.getId(), List.of(paket.getId()), null);

        assertThat(ringkasan.exercises()).isEqualTo(1);
        assertThat(ringkasan.questions()).isEqualTo(2);
        ExerciseEntity salinan = exerciseService.list(client.getId(), null, PageRequest.of(0, 20))
                .getContent().get(0);
        assertThat(salinan.getClientId()).isEqualTo(client.getId());
        assertThat(salinan.isLocked()).isFalse();
        assertThat(exerciseService.itemsOf(salinan.getId())).hasSize(2);
    }

    // ------------------------------------------------ hasil adopsi di tangan Guru

    @Test
    @DisplayName("AC-E02 (FR-024, FR-081): soal hasil adopsi berdampingan dengan soal buatan sekolah dan langsung bisa dirakit Guru")
    void guruMerakitDariHasilAdopsi() {
        ClientEntity client = data.client("SD Katalog7");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        TopicEntity topicMaster = data.globalTopic("Matematika Kelas 4", "Pecahan");
        TopicEntity topicSekolah = data.topic(client, "Muatan Lokal", "Aksara Jawa");

        QuestionEntity master = data.publishedMasterMcq(topicMaster, "Soal dari Eduscreen");
        adoption.adoptQuestions(client.getId(), List.of(master.getId()), guru.getId());
        QuestionEntity buatanSendiri = data.mcq(client, topicSekolah, "Soal buatan sekolah", 4);

        // Keduanya terlihat di bank soal Client tanpa sekat apa pun (BR-P02).
        Page<QuestionEntity> bank = questionService.search(
                client.getId(), null, null, PageRequest.of(0, 50));
        assertThat(bank.getContent()).extracting(QuestionEntity::getBodyText)
                .contains("Soal dari Eduscreen", "Soal buatan sekolah");

        UUID salinanId = bank.getContent().stream()
                .filter(q -> q.getBodyText().equals("Soal dari Eduscreen"))
                .findFirst().orElseThrow().getId();

        ExerciseEntity exercise = exerciseService.create(client.getId(), "Racikan Guru", guru.getId());
        exerciseService.addQuestion(exercise.getId(), salinanId, client.getId());
        exerciseService.addQuestion(exercise.getId(), buatanSendiri.getId(), client.getId());

        assertThat(exerciseService.itemsOf(exercise.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-09 (FR-082): konten master tidak pernah lahir dari adopsi, dan salinan Client tidak pernah menjadi konten master")
    void aliranHanyaSatuArah() {
        ClientEntity client = data.client("SD Katalog8");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity master = data.publishedMasterMcq(topic, "Soal satu arah");
        adoption.adoptQuestions(client.getId(), List.of(master.getId()), null);

        QuestionEntity salinan = questionService.search(
                client.getId(), null, "Soal satu arah", PageRequest.of(0, 20)).getContent().get(0);

        // Salinan membawa jejak asal, master tidak; tidak ada jalur yang membalik arah itu.
        assertThat(salinan.getSourceQuestionId()).isEqualTo(master.getId());
        assertThat(salinan.getClientId()).isEqualTo(client.getId());
        assertThat(master.getSourceQuestionId()).isNull();

        // Salinan milik Client tidak terbaca sebagai konten master, dan tidak muncul di katalog.
        assertThat(questionService.searchPublishedMaster(null, null, "Soal satu arah", PageRequest.of(0, 20))
                .getContent()).extracting(QuestionEntity::getId).containsExactly(master.getId());
    }
}
