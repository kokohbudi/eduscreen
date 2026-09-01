package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard Eduscreen Admin: angka kartu dan antrean pekerjaan yang macet (BR-O05).
 *
 * <p>Database tes dipakai bersama seluruh kelas (lihat {@link PostgresTestBase}), jadi tidak ada
 * assertion yang boleh mengandaikan angka mutlak. Yang diukur selalu SELISIH sebelum dan sesudah.
 */
class EduscreenDashboardIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    EduscreenDashboardService dashboard;
    @Autowired
    MasterPublishingService publishing;
    @Autowired
    TaxonomyService taxonomy;
    @Autowired
    SubjectRepository subjects;

    @Test
    @DisplayName("BR-O05: kartu menghitung Client, Question master, dan paket terbit")
    void kartuMenghitungMilikEduscreen() {
        var sebelum = dashboard.kartu();

        data.client("SD Dashboard1");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal terbit dashboard");
        data.masterMcq(topic, "Soal draf dashboard");
        data.masterExercise("Paket dashboard terbit", List.of(terbit));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
        // Dua Question master lahir: satu terbit, satu draf. Kartu menghitung keduanya.
        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster() + 2);
        // masterExercise() melahirkan paket DRAF, jadi kartu "paket terbit" tidak bergerak.
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): kartu tidak menghitung satu pun Question atau paket milik Client")
    void kartuTidakMenghitungMilikClient() {
        var sebelum = dashboard.kartu();

        ClientEntity client = data.client("SD Dashboard2");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah dashboard", 4);
        data.exercise(client, data.user(client, UserRole.GURU, "Guru Dashboard"),
                "Paket sekolah dashboard", List.of(soalClient));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster());
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
        // Client-nya sendiri MEMANG bertambah: entitas client dikelola Eduscreen, isinya tidak.
        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
    }

    @Test
    @DisplayName("BR-O05 (FR-069): paket master yang memuat Question belum terbit masuk antrean, dan keluar begitu isinya diterbitkan")
    void paketMacetMasukAntreanLaluKeluar() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Isi paket macet");
        var paket = data.masterExercise("Paket macet dashboard", List.of(draf));

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).contains(paket.getId());

        publishing.publishQuestion(draf.getId());

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("BR-O05 (FR-072): paket berisi yang seluruh isinya sudah terbit masuk antrean siap terbit, lalu keluar setelah diterbitkan")
    void paketSiapTerbitMasukAntreanLaluKeluar() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Isi paket siap");
        var paket = data.masterExercise("Paket siap dashboard", List.of(terbit));

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).contains(paket.getId());

        publishing.publishExercise(paket.getId());

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("BR-O05 (FR-072): paket master kosong tidak pernah disebut siap terbit")
    void paketKosongBukanSiapTerbit() {
        var kosong = data.masterExercise("Paket kosong dashboard", List.of());

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(kosong.getId());
        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(kosong.getId());
    }

    @Test
    @DisplayName("BR-O05: Subject global tanpa Topic masuk antrean, lalu keluar begitu Topic pertama lahir")
    void subjectBuntuMasukAntreanLaluKeluar() {
        var subject = taxonomy.createGlobalSubject("Kimia Kelas 11 buntu dashboard");

        assertThat(dashboard.antrean().subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).contains(subject.getId());

        taxonomy.createGlobalTopic(subject.getId(), "Asam Basa");

        assertThat(dashboard.antrean().subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).doesNotContain(subject.getId());
    }

    @Test
    @DisplayName("BR-O05 (TC-36): Topic milik Client di bawah Subject global tidak mengeluarkannya dari antrean buntu")
    void topicClientTidakMembuatSubjectGlobalBerhentiBuntu() {
        ClientEntity client = data.client("SD Dashboard4");
        var subject = taxonomy.createGlobalSubject("Kimia Kelas 12 buntu client");

        // FR-014: Client boleh menggantungkan Topic lokal di bawah Subject GLOBAL. Topic itu tidak
        // terlihat di ruang kerja master, jadi Subject-nya TETAP buntu bagi Eduscreen Admin.
        taxonomy.createClientTopic(subject.getId(), client.getId(), "Bab lokal sekolah");

        assertThat(dashboard.antrean().subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).contains(subject.getId());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): pekerjaan macet milik sebuah Client tidak pernah masuk antrean Eduscreen")
    void antreanTidakMemuatPekerjaanClient() {
        ClientEntity client = data.client("SD Dashboard3");
        var guru = data.user(client, UserRole.GURU, "Guru Antrean");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah antrean", 4);
        var paketClient = data.exercise(client, guru, "Paket sekolah antrean", List.of(soalClient));
        var subjectClient = subjects.save(SubjectEntity.forClient(client.getId(), "Bahasa Sunda Kelas 5 buntu"));

        var antrean = dashboard.antrean();

        assertThat(antrean.paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paketClient.getId());
        assertThat(antrean.paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paketClient.getId());
        assertThat(antrean.subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).doesNotContain(subjectClient.getId());
    }

    @Test
    @DisplayName("BR-O05: antrean tanpa satu pun baris menyatakan dirinya kosong, sehingga bloknya tidak dirender")
    void antreanTanpaBarisMenyatakanDirinyaKosong() {
        var nihil = new EduscreenDashboardService.Baris<ExerciseEntity>(List.of(), 0);
        var kosong = new EduscreenDashboardService.Antrean(0, nihil, nihil,
                new EduscreenDashboardService.Baris<SubjectEntity>(List.of(), 0));

        assertThat(kosong.kosong()).isTrue();

        var adaDraf = new EduscreenDashboardService.Antrean(1, nihil, nihil,
                new EduscreenDashboardService.Baris<SubjectEntity>(List.of(), 0));

        assertThat(adaDraf.kosong()).isFalse();
    }

    @Test
    @DisplayName("BR-O05: satu baris antrean menampilkan paling banyak lima nama dan menghitung sisanya")
    void barisAntreanDipotongLimaNama() {
        var tujuh = List.of("a", "b", "c", "d", "e", "f", "g");

        var baris = EduscreenDashboardService.Baris.dari(tujuh);

        assertThat(baris.tampil()).hasSize(5);
        assertThat(baris.total()).isEqualTo(7);
        assertThat(baris.sisa()).isEqualTo(2);
        assertThat(baris.ada()).isTrue();
    }
}
