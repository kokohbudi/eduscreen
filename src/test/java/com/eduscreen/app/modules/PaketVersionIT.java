package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.NeedsVersionChoiceException;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.PaketVersionService;
import com.eduscreen.app.modules.assessment.service.QuestionFrozenException;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Versi Paket master: beku setelah terbit, versi baru dan instance baru berbagi baris soal (ADR-0021). */
class PaketVersionIT extends PostgresTestBase {

    @Autowired TestData data;
    @Autowired PaketService pakets;
    @Autowired PaketVersionService versionService;
    @Autowired PaketVersionRepository versions;
    @Autowired PaketItemRepository items;
    @Autowired QuestionRepository questions;
    @Autowired QuestionService questionService;
    @Autowired MasterPublishingService publishing;
    @Autowired ExerciseItemRepository exerciseItems;

    private record Siap(PaketEntity paket, TopicEntity topic, QuestionEntity soal) {
    }

    /** Paket master terbit berisi satu soal terbit: versi 1 beku, tidak ada versi kerja. */
    private Siap paketTerbit(String nama) {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 " + nama, "Paket " + nama);
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topic, "Soal " + nama);
        publishing.publishPaket(paket.getId());
        return new Siap(paket, topic, soal);
    }

    /** Versi 1 sebuah Paket — versi terbit tertua. */
    private UUID v1(PaketEntity paket) {
        List<PaketVersionEntity> semua = versions.findByPaketIdOrderByNomorDesc(paket.getId());
        return semua.get(semua.size() - 1).getId();
    }

    private static QuestionService.QuestionDraft draf(UUID topicId, String body) {
        return new QuestionService.QuestionDraft(topicId, QuestionType.MULTIPLE_CHOICE, "<p>" + body + "</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false)));
    }

    @Test
    @DisplayName("AC-B12 (ADR-0021): menerbitkan Paket membekukan versi kerjanya; menulis ke Paket tanpa versi kerja ditolak dengan pilihan")
    void terbitMembekukanVersi() {
        Siap s = paketTerbit("Beku");

        PaketVersionEntity v1 = versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(s.paket().getId())
                .orElseThrow();
        assertThat(v1.getNomor()).isEqualTo(1);
        assertThat(versions.findDraft(s.paket().getId())).isEmpty();
        // Membaca tetap bisa: isi yang tampil adalah versi terbit terakhir.
        assertThat(questionService.groupByTopic(s.paket().getId()).get(s.topic().getId()))
                .extracting(QuestionEntity::getId).containsExactly(s.soal().getId());

        assertThatThrownBy(() -> questionService.create(draf(s.topic().getId(), "Soal baru"), null, s.paket().getId()))
                .isInstanceOf(NeedsVersionChoiceException.class);
    }

    @Test
    @DisplayName("AC-B12 (ADR-0021): versi baru menyalin penempatan, bukan soal — baris question yang sama di kedua versi")
    void versiBaruBerbagiSoal() {
        Siap s = paketTerbit("Versi2");

        PaketVersionEntity v2 = versionService.newVersion(s.paket().getId(), null);

        assertThat(v2.getNomor()).isEqualTo(2);
        assertThat(v2.isDraft()).isTrue();
        assertThat(items.questionIdsOf(v2.getId())).containsExactly(s.soal().getId());
        assertThat(questions.findAll()).filteredOn(q -> q.getBodyText().equals("Soal Versi2")).hasSize(1);

        // Versi kerja ada lagi: menulis boleh, dan hanya v2 yang berubah.
        QuestionEntity tambahan = questionService.create(draf(s.topic().getId(), "Soal tambahan v2"), null, s.paket().getId());
        assertThat(items.questionIdsOf(v2.getId())).containsExactly(s.soal().getId(), tambahan.getId());
        assertThat(items.questionIdsOf(v1(s.paket()))).containsExactly(s.soal().getId());
    }

    @Test
    @DisplayName("AC-B17 (ADR-0021): soal master terbit tidak diedit di tempat; revisi melahirkan baris baru yang menggantikannya di versi kerja saja")
    void revisiMelahirkanBarisBaru() {
        Siap s = paketTerbit("Revisi");
        PaketVersionEntity v2 = versionService.newVersion(s.paket().getId(), null);

        assertThatThrownBy(() -> questionService.update(s.soal().getId(), draf(s.topic().getId(), "diubah"),
                null, s.paket().getId()))
                .isInstanceOf(QuestionFrozenException.class);

        QuestionEntity revisi = questionService.revise(s.soal().getId(), draf(s.topic().getId(), "Soal Revisi v2"),
                s.paket().getId());

        assertThat(revisi.getId()).isNotEqualTo(s.soal().getId());
        assertThat(revisi.isPublished()).isFalse();
        QuestionEntity lama = questions.findById(s.soal().getId()).orElseThrow();
        assertThat(lama.getSupersededById()).isEqualTo(revisi.getId());
        assertThat(lama.getBodyText()).isEqualTo("Soal Revisi");
        // Versi kerja menunjuk revisi di Topic dan posisi yang sama; versi terbit tetap baris lama.
        PaketItemEntity itemBaru = items.findByPaketVersionIdAndQuestionId(v2.getId(), revisi.getId()).orElseThrow();
        assertThat(itemBaru.getTopicId()).isEqualTo(s.topic().getId());
        assertThat(itemBaru.getPosition()).isZero();
        assertThat(items.findByPaketVersionIdAndQuestionId(v2.getId(), s.soal().getId())).isEmpty();
        assertThat(items.questionIdsOf(v1(s.paket()))).containsExactly(s.soal().getId());
    }

    @Test
    @DisplayName("BR-S02 (ADR-0021): Exercise dan pencarian bank soal tidak bergeser karena revisi — Exercise tetap baris lama, ruang kerja hanya menawarkan yang terbaru")
    void exerciseTetapBarisLama() {
        Siap s = paketTerbit("Exercise");
        ClientEntity client = data.client("SD Versi Exercise");
        var guru = data.user(client, UserRole.GURU, "Guru Versi");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan versi", List.of(s.soal()));
        versionService.newVersion(s.paket().getId(), null);

        QuestionEntity revisi = questionService.revise(s.soal().getId(), draf(s.topic().getId(), "Soal Exercise v2"),
                s.paket().getId());

        assertThat(exerciseItems.findByExerciseIdOrderByPositionAsc(exercise.getId()))
                .extracting(item -> item.getQuestionId()).containsExactly(s.soal().getId());
        assertThat(questionService.searchMaster(null, null, "Soal Exercise", null,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(QuestionEntity::getId).containsExactly(revisi.getId());
    }

    @Test
    @DisplayName("AC-B05 (ADR-0021): instance baru = Paket baru dengan Topic salinan label dan item ke soal yang sama, nol baris question baru")
    void instanceBaruBerbagiSoal() {
        Siap s = paketTerbit("Instance");
        long soalSebelum = questions.count();

        PaketEntity baru = versionService.newInstance(s.paket().getId(), "Paket Instance (salinan)", null);

        assertThat(baru.getId()).isNotEqualTo(s.paket().getId());
        assertThat(baru.getClientId()).isNull();
        assertThat(baru.getSubjectId()).isEqualTo(s.paket().getSubjectId());
        assertThat(baru.isPublished()).isFalse();
        List<TopicEntity> topicBaru = pakets.topicsOf(baru.getId());
        assertThat(topicBaru).extracting(TopicEntity::getTitle).containsExactly(s.topic().getTitle());
        assertThat(topicBaru.get(0).getId()).isNotEqualTo(s.topic().getId());
        PaketVersionEntity draf = versions.findDraft(baru.getId()).orElseThrow();
        assertThat(items.findByVersionAndTopicOrdered(draf.getId(), topicBaru.get(0).getId()))
                .extracting(PaketItemEntity::getQuestionId).containsExactly(s.soal().getId());
        assertThat(questions.count()).isEqualTo(soalSebelum);

        // Hapus di instance baru hanya membuang penempatan di sana: Paket asal dan barisnya utuh.
        questionService.softDelete(s.soal().getId(), null, baru.getId());
        assertThat(items.questionIdsOf(draf.getId())).isEmpty();
        assertThat(items.questionIdsOf(v1(s.paket()))).containsExactly(s.soal().getId());
        assertThat(questions.findById(s.soal().getId())).isPresent();
    }

    @Test
    @DisplayName("AC-B18 (ADR-0021): jumlah soal per Paket master dihitung dari versi yang dibaca — Paket beku tidak tampil bersoal nol")
    void jumlahSoalPaketBeku() {
        Siap s = paketTerbit("Hitung");

        assertThat(questions.countMasterByPaket()).filteredOn(c -> c.getPaketId().equals(s.paket().getId()))
                .extracting(QuestionRepository.PaketCount::getJumlah).containsExactly(1L);

        versionService.newVersion(s.paket().getId(), null);
        questionService.create(draf(s.topic().getId(), "Soal Hitung dua"), null, s.paket().getId());
        assertThat(questions.countMasterByPaket()).filteredOn(c -> c.getPaketId().equals(s.paket().getId()))
                .extracting(QuestionRepository.PaketCount::getJumlah).as("versi kerja yang dibaca").containsExactly(2L);
    }

    @Test
    @DisplayName("AC-B17 (ADR-0021): soal yang sudah digantikan tidak bisa direvisi lagi dari Paket lain — rantai riwayat satu arah")
    void revisiGandaDitolak() {
        Siap s = paketTerbit("Rantai");
        PaketEntity instance = versionService.newInstance(s.paket().getId(), "Paket Rantai instance", null);
        versionService.newVersion(s.paket().getId(), null);
        TopicEntity topicInstance = pakets.topicsOf(instance.getId()).get(0);
        questionService.revise(s.soal().getId(), draf(s.topic().getId(), "Soal Rantai v2"), s.paket().getId());

        assertThatThrownBy(() -> questionService.revise(s.soal().getId(), draf(topicInstance.getId(), "Soal Rantai lain"),
                instance.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AC-B17 (ADR-0021): menghapus soal master yang ada di versi terbit hanya membuangnya dari versi kerja; barisnya tetap hidup")
    void hapusHanyaDariVersiKerja() {
        Siap s = paketTerbit("Hapus");
        PaketVersionEntity v2 = versionService.newVersion(s.paket().getId(), null);

        questionService.softDelete(s.soal().getId(), null, s.paket().getId());

        assertThat(items.questionIdsOf(v2.getId())).isEmpty();
        assertThat(items.questionIdsOf(v1(s.paket()))).containsExactly(s.soal().getId());
        assertThat(questions.findById(s.soal().getId())).isPresent();
    }

    @Test
    @DisplayName("AC-B12 (ADR-0021): menerbitkan versi kerja berikutnya membekukannya sebagai versi 2 dan menandai versi 1 tergantikan")
    void terbitVersiBerikutnya() {
        Siap s = paketTerbit("Terbit2");
        PaketVersionEntity v2 = versionService.newVersion(s.paket().getId(), null);
        questionService.create(draf(s.topic().getId(), "Soal Terbit2 baru"), null, s.paket().getId());

        publishing.publishPaket(s.paket().getId(), true);

        PaketVersionEntity v2Terbit = versions.findById(v2.getId()).orElseThrow();
        assertThat(v2Terbit.isDraft()).isFalse();
        PaketVersionEntity v1 = versions.findById(v1(s.paket())).orElseThrow();
        assertThat(v1.getSupersededAt()).isNotNull();
        assertThat(versions.findDraft(s.paket().getId())).isEmpty();
        assertThat(pakets.versionOf(s.paket().getId()).getId()).isEqualTo(v2.getId());
    }
}
