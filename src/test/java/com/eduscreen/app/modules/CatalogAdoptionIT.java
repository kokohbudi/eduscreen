package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ContentAdoptionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Katalog Paket master dan adopsinya (Task 8/11, ADR-0018): Paket adalah satu-satunya satuan
 * katalog dan adopsi, menggantikan adopsi per Question dan per Exercise (AC-B05).
 *
 * <p>Termasuk pembuktian bahwa hasil adopsi berperilaku persis seperti soal buatan sekolah sendiri
 * di tangan Guru (FR-024, SC-018), dan bahwa aliran konten hanya satu arah dari master ke Client
 * (TC-09, FR-082).
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
    MasterPublishingService masterPublishing;
    @Autowired
    PaketService paketService;
    @Autowired
    PaketRepository pakets;
    @Autowired
    QuestionRepository questions;
    @Autowired
    QuestionOptionRepository options;
    @Autowired
    SubjectRepository subjects;

    // -------------------------------------------------------------- adopsi per Paket

    @Test
    @DisplayName("AC-B05: adopsi menyalin Paket beserta Topic, Question, dan Option")
    void adoptCopiesWholePaket() {
        ClientEntity client = data.client("SD Adopsi Paket");
        PaketEntity master = data.masterPaket("Matematika Kelas 6 Adopsi", "Paket Master");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        data.publishedMasterMcq(topicMaster, "Berapa 7 x 8?");
        masterPublishing.publishPaket(master.getId());

        ContentAdoptionService.AdoptionSummary ringkasan =
                adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        assertThat(ringkasan.pakets()).isEqualTo(1);
        assertThat(ringkasan.questions()).isEqualTo(1);

        PaketEntity salinan = pakets.findByClientIdAndSubjectIdOrderByTitleAsc(
                client.getId(), master.getSubjectId()).get(0);
        assertThat(salinan.getSourcePaketId()).isEqualTo(master.getId());
        List<QuestionEntity> questionSalinan = questions.findByPaketIdOrderByPositionAsc(salinan.getId());
        assertThat(questionSalinan).hasSize(1);

        // Salinan Option lengkap — jumlahnya dan mana yang benar (FR-016) — bukan sekadar
        // Question-nya yang tersalin.
        List<QuestionOptionEntity> opsiSalinan =
                options.findByQuestionIdOrderByPositionAsc(questionSalinan.get(0).getId());
        assertThat(opsiSalinan).hasSize(4);
        assertThat(opsiSalinan).filteredOn(QuestionOptionEntity::isCorrect).hasSize(1);
    }

    @Test
    @DisplayName("AC-B05: Paket master yang belum terbit tidak bisa diadopsi")
    void unpublishedMasterCannotBeAdopted() {
        ClientEntity client = data.client("SD Adopsi Draf");
        PaketEntity draf = data.masterPaket("IPS Kelas 6 Adopsi", "Masih Draf");

        assertThatThrownBy(() -> adoption.adoptPakets(client.getId(), List.of(draf.getId()), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("TC-09 (FR-067): Paket master yang sama menjadi bisa diadopsi begitu diterbitkan, pembeda satu-satunya adalah keadaan terbitnya")
    void draftBecomesAdoptableOncePublished() {
        ClientEntity client = data.client("SD Adopsi Terbit");
        PaketEntity master = data.masterPaket("Biologi Kelas 8 Adopsi", "Paket Masih Digarap");
        data.publishedMasterMcq(paketService.topicsOf(master.getId()).get(0), "Isi paket adopsi terbit");

        assertThatThrownBy(() -> adoption.adoptPakets(client.getId(), List.of(master.getId()), null))
                .isInstanceOf(ResourceNotFoundException.class);

        masterPublishing.publishPaket(master.getId());
        ContentAdoptionService.AdoptionSummary ringkasan =
                adoption.adoptPakets(client.getId(), List.of(master.getId()), null);
        assertThat(ringkasan.pakets()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-B05 (FR-016): adopsi menyalin seluruh Topic, Question, dan Option dengan jumlah dan urutan yang tepat")
    void adoptCopiesMultipleTopicsQuestionsAndOptionsExactly() {
        ClientEntity client = data.client("SD Adopsi Lengkap");
        PaketEntity master = data.masterPaket("IPA Kelas 5 Adopsi", "Paket IPA Lengkap");
        TopicEntity topic1 = paketService.topicsOf(master.getId()).get(0);
        TopicEntity topic2 = paketService.addTopic(master.getId(), "Topik 2", null);

        QuestionEntity soal1 = questionService.create(new QuestionService.QuestionDraft(
                topic1.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Soal 1</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false),
                        new QuestionService.OptionDraft("<p>C</p>", false))), null, master.getId());
        QuestionEntity soal2 = questionService.create(new QuestionService.QuestionDraft(
                topic1.getId(), QuestionType.ESSAY, "<p>Soal 2 esai</p>", "<p>Pembahasan</p>", List.of()),
                null, master.getId());
        QuestionEntity soal3 = questionService.create(new QuestionService.QuestionDraft(
                topic2.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Soal 3</p>", null,
                List.of(new QuestionService.OptionDraft("<p>X</p>", false),
                        new QuestionService.OptionDraft("<p>Y</p>", true))), null, master.getId());
        // Paket master ditolak terbit selama isinya draf (AC-B12) — ketiganya diterbitkan lebih
        // dulu supaya publishPaket di bawah ini lolos gerbangnya.
        masterPublishing.publishQuestion(soal1.getId());
        masterPublishing.publishQuestion(soal2.getId());
        masterPublishing.publishQuestion(soal3.getId());
        masterPublishing.publishPaket(master.getId());

        ContentAdoptionService.AdoptionSummary ringkasan =
                adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        assertThat(ringkasan.pakets()).isEqualTo(1);
        assertThat(ringkasan.topics()).isEqualTo(2);
        assertThat(ringkasan.questions()).isEqualTo(3);

        PaketEntity salinan = pakets.findByClientIdAndSubjectIdOrderByTitleAsc(
                client.getId(), master.getSubjectId()).get(0);
        List<TopicEntity> topicSalinan = paketService.topicsOf(salinan.getId());
        assertThat(topicSalinan).hasSize(2);
        assertThat(topicSalinan).extracting(TopicEntity::getPosition).containsExactly(0, 1);

        List<QuestionEntity> questionTopic1Salinan =
                questions.findByTopicIdOrderByPositionAsc(topicSalinan.get(0).getId());
        assertThat(questionTopic1Salinan).extracting(QuestionEntity::getBodyText)
                .containsExactly("Soal 1", "Soal 2 esai");
        assertThat(questionTopic1Salinan).extracting(QuestionEntity::getPosition).containsExactly(0, 1);

        List<QuestionOptionEntity> opsiSoal1 =
                options.findByQuestionIdOrderByPositionAsc(questionTopic1Salinan.get(0).getId());
        assertThat(opsiSoal1).extracting(QuestionOptionEntity::getBodyText).containsExactly("A", "B", "C");
        assertThat(opsiSoal1).filteredOn(QuestionOptionEntity::isCorrect)
                .extracting(QuestionOptionEntity::getBodyText).containsExactly("A");
        // Soal esai tidak pernah punya Option.
        assertThat(options.findByQuestionIdOrderByPositionAsc(questionTopic1Salinan.get(1).getId())).isEmpty();

        List<QuestionEntity> questionTopic2Salinan =
                questions.findByTopicIdOrderByPositionAsc(topicSalinan.get(1).getId());
        assertThat(questionTopic2Salinan).extracting(QuestionEntity::getBodyText).containsExactly("Soal 3");
        List<QuestionOptionEntity> opsiSoal3 =
                options.findByQuestionIdOrderByPositionAsc(questionTopic2Salinan.get(0).getId());
        assertThat(opsiSoal3).filteredOn(QuestionOptionEntity::isCorrect)
                .extracting(QuestionOptionEntity::getBodyText).containsExactly("Y");
    }

    @Test
    @DisplayName("AC-B10 (FR-068): menarik Paket master dari peredaran tidak menyentuh satu pun Paket atau Question salinan yang sudah diadopsi")
    void withdrawMasterDoesNotTouchAdoptedCopies() {
        ClientEntity clientA = data.client("SD Adopsi Tarik1");
        ClientEntity clientB = data.client("SD Adopsi Tarik2");
        PaketEntity master = data.masterPaket("Matematika Kelas 5 Tarik", "Paket Ditarik");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        data.publishedMasterMcq(topicMaster, "Soal paket ditarik");
        masterPublishing.publishPaket(master.getId());

        adoption.adoptPakets(clientA.getId(), List.of(master.getId()), null);
        adoption.adoptPakets(clientB.getId(), List.of(master.getId()), null);

        masterPublishing.withdrawPaket(master.getId());

        // Ditarik: tidak lagi bisa diadopsi lewat gerbang yang sama...
        assertThatThrownBy(() -> adoption.adoptPakets(
                data.client("SD Adopsi Tarik3").getId(), List.of(master.getId()), null))
                .isInstanceOf(ResourceNotFoundException.class);

        // ...tapi kedua salinan yang sudah diadopsi sebelumnya tetap utuh, tidak tersentuh.
        for (ClientEntity client : List.of(clientA, clientB)) {
            List<PaketEntity> paketClient = pakets.findByClientIdAndSubjectIdOrderByTitleAsc(
                    client.getId(), master.getSubjectId());
            assertThat(paketClient).hasSize(1);
            assertThat(questions.findByPaketIdOrderByPositionAsc(paketClient.get(0).getId())).hasSize(1);
        }
    }

    @Test
    @DisplayName("AC-O01 (FR-070): mengubah Question master setelah diadopsi tidak mengubah salinan milik Client")
    void editingMasterDoesNotPropagateToCopy() {
        ClientEntity client = data.client("SD Adopsi Sunting");
        PaketEntity master = data.masterPaket("Matematika Kelas 5 Sunting", "Paket Disunting");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topicMaster, "Redaksi lama unikrambat");
        masterPublishing.publishPaket(master.getId());
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        questionService.update(soal.getId(), new QuestionService.QuestionDraft(
                topicMaster.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Redaksi baru unikrambat</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false))), null, master.getId());

        // search() sudah dicabut (Task 14): searchForBuilder dengan paketId/type null dan
        // excluded kosong adalah pencarian bank soal Client biasa, satu-satunya yang tersisa.
        Page<QuestionEntity> salinan = questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), "unikrambat", PageRequest.of(0, 20));
        assertThat(salinan.getContent()).hasSize(1);
        assertThat(salinan.getContent().get(0).getBodyText()).isEqualTo("Redaksi lama unikrambat");
    }

    @Test
    @DisplayName("AC-B09 (FR-065): menghapus Question master menghilangkannya dari katalog master, dan salinan yang sudah diadopsi tetap utuh")
    void softDeletingMasterDoesNotTouchAdoptedCopy() {
        ClientEntity client = data.client("SD Adopsi Hapus");
        PaketEntity master = data.masterPaket("Matematika Kelas 5 Hapus", "Paket Dihapus");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topicMaster, "Soal dihapus unikhapus");
        masterPublishing.publishPaket(master.getId());
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        // Tarik Paket-nya dulu: sejak AC-B17 isi Paket yang sedang terbit tidak boleh dihapus,
        // karena menghapus soal terakhirnya menghasilkan Paket terbit yang kosong. Penarikan
        // Paket sendiri tidak menyentuh salinan yang sudah diadopsi (AC-B10), jadi yang dibuktikan
        // tes ini — salinan Client tetap utuh — tetap yang itu juga.
        masterPublishing.withdrawPaket(master.getId());
        questionService.softDelete(soal.getId(), null);

        assertThat(questionService.searchMaster(null, null, "unikhapus", null, PageRequest.of(0, 20))
                .getTotalElements()).isZero();
        assertThat(questions.searchPublishedMaster(null, null, "%unikhapus%", PageRequest.of(0, 20))
                .getTotalElements()).isZero();

        Page<QuestionEntity> salinan = questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), "unikhapus", PageRequest.of(0, 20));
        assertThat(salinan.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("AC-B10: adopsi kedua atas Paket yang sama tetap diizinkan dan melahirkan salinan kedua yang terpisah")
    void secondAdoptionCreatesSeparateCopy() {
        ClientEntity client = data.client("SD Adopsi Ulang");
        PaketEntity master = data.masterPaket("Matematika Kelas 5 Ulang", "Paket Diadopsi Ulang");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        data.publishedMasterMcq(topicMaster, "Soal diadopsi ulang");
        masterPublishing.publishPaket(master.getId());

        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        List<PaketEntity> paketClient = pakets.findByClientIdAndSubjectIdOrderByTitleAsc(
                client.getId(), master.getSubjectId());
        assertThat(paketClient).hasSize(2);
        assertThat(paketClient).extracting(PaketEntity::getId).doesNotHaveDuplicates();
        assertThat(paketClient).allSatisfy(p -> assertThat(p.getSourcePaketId()).isEqualTo(master.getId()));

        // Masing-masing salinan punya Question sendiri, bukan berbagi baris yang sama.
        List<UUID> questionIdSalinan1 = questions.findByPaketIdOrderByPositionAsc(paketClient.get(0).getId())
                .stream().map(QuestionEntity::getId).toList();
        List<UUID> questionIdSalinan2 = questions.findByPaketIdOrderByPositionAsc(paketClient.get(1).getId())
                .stream().map(QuestionEntity::getId).toList();
        assertThat(questionIdSalinan1).hasSize(1);
        assertThat(questionIdSalinan2).hasSize(1);
        assertThat(questionIdSalinan1).doesNotContainAnyElementsOf(questionIdSalinan2);
    }

    // ------------------------------------------------------------ katalog: penanda adopsi

    @Test
    @DisplayName("AC-B11 (FR-076): katalog menandai Paket master yang sudah pernah diadopsi Client")
    void catalogMarksAdoptedPaket() {
        ClientEntity client = data.client("SD Katalog Tanda");
        PaketEntity master = data.masterPaket("Sejarah Kelas 9 Tanda", "Paket Sejarah");
        data.publishedMasterMcq(paketService.topicsOf(master.getId()).get(0), "Isi paket tanda");
        masterPublishing.publishPaket(master.getId());
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        assertThat(adoption.adoptedSourcePaketIds(client.getId(), List.of(master.getId())))
                .contains(master.getId());
    }

    @Test
    @DisplayName("AC-B11 (FR-076): penanda adopsi hanya berlaku untuk Client yang mengadopsi, bukan Client lain")
    void adoptionMarkerIsScopedToAdoptingClient() {
        ClientEntity client = data.client("SD Katalog Tanda Sendiri");
        ClientEntity clientLain = data.client("SD Katalog Tanda Lain");
        PaketEntity master = data.masterPaket("Sejarah Kelas 9 Tanda Sendiri", "Paket Sejarah Sendiri");
        data.publishedMasterMcq(paketService.topicsOf(master.getId()).get(0), "Isi paket tanda sendiri");
        masterPublishing.publishPaket(master.getId());
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        Set<UUID> penanda = adoption.adoptedSourcePaketIds(clientLain.getId(), List.of(master.getId()));
        assertThat(penanda).isEmpty();
    }

    @Test
    @DisplayName("AC-O02 (BR-O02): Paket hasil adopsi menunjuk Subject global yang sama, tanpa melahirkan Subject baru")
    void adoptedCopyPointsToSameGlobalSubject() {
        ClientEntity client = data.client("SD Adopsi Subject");
        PaketEntity master = data.masterPaket("Kimia Kelas 10 Adopsi Subject", "Paket Kimia");
        data.publishedMasterMcq(paketService.topicsOf(master.getId()).get(0), "Isi paket adopsi subject");
        masterPublishing.publishPaket(master.getId());
        long subjectSebelum = subjects.count();

        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        // Subject GLOBAL tidak pernah disalin (BR-O02): salinan Paket menunjuk baris Subject
        // yang persis sama dengan master-nya.
        assertThat(subjects.count()).isEqualTo(subjectSebelum);
        PaketEntity salinan = pakets.findByClientIdAndSubjectIdOrderByTitleAsc(
                client.getId(), master.getSubjectId()).get(0);
        assertThat(salinan.getSubjectId()).isEqualTo(master.getSubjectId());
    }

    // ------------------------------------------------ hasil adopsi di tangan Guru

    @Test
    @DisplayName("AC-E02 (FR-024, FR-081): soal hasil adopsi berdampingan dengan soal buatan sekolah dan langsung bisa dirakit Guru")
    void guruMerakitDariHasilAdopsi() {
        ClientEntity client = data.client("SD Katalog7");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Rakit", "Paket Rakit");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        data.publishedMasterMcq(topicMaster, "Soal dari Eduscreen");
        masterPublishing.publishPaket(master.getId());
        TopicEntity topicSekolah = data.topic(client, "Muatan Lokal", "Aksara Jawa");

        adoption.adoptPakets(client.getId(), List.of(master.getId()), guru.getId());
        QuestionEntity buatanSendiri = data.mcq(client, topicSekolah, "Soal buatan sekolah", 4);

        // Keduanya terlihat di bank soal Client tanpa sekat apa pun (BR-P02).
        Page<QuestionEntity> bank = questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), null, PageRequest.of(0, 50));
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
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Arah", "Paket Arah");
        TopicEntity topicMaster = paketService.topicsOf(master.getId()).get(0);
        // Question sendiri juga wajib terbit di sini: searchPublishedMaster menyaring
        // q.publishedAt, terpisah dari gerbang adopsi yang kini di tingkat Paket.
        QuestionEntity soal = data.publishedMasterMcq(topicMaster, "Soal satu arah");
        masterPublishing.publishPaket(master.getId());
        adoption.adoptPakets(client.getId(), List.of(master.getId()), null);

        QuestionEntity salinan = questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), "Soal satu arah", PageRequest.of(0, 20))
                .getContent().get(0);

        // Salinan membawa jejak asal, master tidak; tidak ada jalur yang membalik arah itu.
        assertThat(salinan.getSourceQuestionId()).isEqualTo(soal.getId());
        assertThat(salinan.getClientId()).isEqualTo(client.getId());
        assertThat(soal.getSourceQuestionId()).isNull();

        // Salinan milik Client tidak terbaca sebagai konten master, dan tidak muncul di ruang
        // kerja master (QuestionService.searchPublishedMaster sudah dihapus — tidak ada lagi
        // katalog per Question sejak ADR-0018, jadi dibaca langsung lewat repository).
        assertThat(questions.searchPublishedMaster(null, null, "%soal satu arah%", PageRequest.of(0, 20))
                .getContent()).extracting(QuestionEntity::getId).containsExactly(soal.getId());
    }
}
