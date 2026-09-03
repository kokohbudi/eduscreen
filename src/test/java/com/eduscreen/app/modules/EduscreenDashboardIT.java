package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketService;
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
 *
 * <p>Sebelum Task 10 (ADR-0018), fixture di sini memakai {@code data.masterExercise(...)} —
 * Exercise ber-{@code clientId} null, satuan konten master sebelum Paket menggantikannya. Exercise
 * master itu sudah dicabut; fixture sekarang memakai {@code data.masterPaket(...)} beserta
 * Question yang ditambahkan ke Topic bawaannya, satuan yang sungguhan dibaca dashboard sejak
 * Task 10 (lihat {@code EduscreenDashboardService}).
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
    PaketService pakets;
    @Autowired
    SubjectRepository subjects;
    @Autowired
    PaketRepository paketRepository;

    @Test
    @DisplayName("BR-O05: kartu menghitung Client, Question master, dan paket terbit")
    void kartuMenghitungMilikEduscreen() {
        var sebelum = dashboard.kartu();
        var antreanSebelum = dashboard.antrean();

        data.client("SD Dashboard1");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        data.publishedMasterMcq(topic, "Soal terbit dashboard");
        data.masterMcq(topic, "Soal draf dashboard");
        data.masterPaket("Matematika Kelas 4 Dashboard Terbit", "Paket dashboard terbit");

        var sesudah = dashboard.kartu();
        var antreanSesudah = dashboard.antrean();

        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
        // Dua Question master lahir: satu terbit, satu draf. Kartu menghitung keduanya.
        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster() + 2);
        // masterPaket() melahirkan Paket DRAF, jadi kartu "paket terbit" tidak bergerak.
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
        // Dari dua Question master di atas, hanya satu yang draf (masterMcq tanpa publish).
        assertThat(antreanSesudah.questionDraf()).isEqualTo(antreanSebelum.questionDraf() + 1);
    }

    @Test
    @DisplayName("BR-P04 (FR-080): kartu tidak menghitung satu pun Question atau paket milik Client")
    void kartuTidakMenghitungMilikClient() {
        var sebelum = dashboard.kartu();

        ClientEntity client = data.client("SD Dashboard2");
        data.paket(client, "Matematika Kelas 4 Dashboard Client", "Paket sekolah dashboard");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        data.mcq(client, topicClient, "Soal sekolah dashboard", 4);

        var sesudah = dashboard.kartu();

        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster());
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
        // Client-nya sendiri MEMANG bertambah: entitas client dikelola Eduscreen, isinya tidak.
        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
    }

    @Test
    @DisplayName("BR-O05 (FR-069): paket master yang memuat Question belum terbit masuk antrean, dan keluar begitu isinya diterbitkan")
    void paketMacetMasukAntreanLaluKeluar() {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Dashboard Macet", "Paket macet dashboard");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity draf = data.masterMcq(topic, "Isi paket macet");

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(PaketEntity::getId).contains(paket.getId());

        publishing.publishQuestion(draf.getId());

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(PaketEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("BR-O05 (AC-B16): paket berisi yang seluruh isinya sudah terbit masuk antrean siap terbit, lalu keluar setelah diterbitkan")
    void paketSiapTerbitMasukAntreanLaluKeluar() {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Dashboard Siap", "Paket siap dashboard");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        data.publishedMasterMcq(topic, "Isi paket siap");

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(PaketEntity::getId).contains(paket.getId());

        publishing.publishPaket(paket.getId());

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(PaketEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("AC-B16 (BR-O05): paket master kosong masuk antrean macet, bukan siap terbit")
    void paketKosongMacetBukanSiapTerbit() {
        PaketEntity kosong = data.masterPaket("Matematika Kelas 4 Dashboard Kosong", "Paket kosong dashboard");

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(PaketEntity::getId).doesNotContain(kosong.getId());
        // Query penuh, bukan tampil(): daftar tampilan dipotong lima, jadi di database tes
        // bersama isinya bergantung pada Paket macet lain yang ditinggalkan kelas tes lain.
        assertThat(paketRepository.findMasterBlocked())
                .extracting(PaketEntity::getId).contains(kosong.getId());
    }

    @Test
    @DisplayName("BR-O05: Subject global tanpa Topic masuk antrean, lalu keluar begitu Topic pertama lahir")
    void subjectBuntuMasukAntreanLaluKeluar() {
        var subject = taxonomy.createGlobalSubject("Kimia Kelas 11 buntu dashboard");

        // Diperiksa lewat query penuh (subjects.findGlobalWithoutTopic()), bukan
        // dashboard.antrean().subjectBuntu().tampil(): daftar tampilan dipotong lima dan diurut
        // nama, jadi di database tes bersama ia bergantung pada kelas tes mana yang kebetulan
        // jalan lebih dulu meninggalkan berapa banyak Subject GLOBAL buntu lain.
        assertThat(subjects.findGlobalWithoutTopic())
                .extracting(SubjectEntity::getId).contains(subject.getId());

        // createGlobalTopic sudah dicabut (Task 14): create() dengan clientId null melahirkan
        // Paket master beserta Topic bawaannya sekali jalan (PaketService.TOPIC_BAWAAN).
        pakets.create(new PaketService.PaketDraft("Asam Basa", subject.getId(), null), null, null);

        assertThat(subjects.findGlobalWithoutTopic())
                .extracting(SubjectEntity::getId).doesNotContain(subject.getId());
    }

    @Test
    @DisplayName("BR-O05 (TC-36): Topic milik Client di bawah Subject global tidak mengeluarkannya dari antrean buntu")
    void topicClientTidakMembuatSubjectGlobalBerhentiBuntu() {
        ClientEntity client = data.client("SD Dashboard4");
        var subject = taxonomy.createGlobalSubject("Kimia Kelas 12 buntu client");

        // FR-014: Client boleh menggantungkan Topic lokal di bawah Subject GLOBAL. Topic itu tidak
        // terlihat di ruang kerja master, jadi Subject-nya TETAP buntu bagi Eduscreen Admin.
        // createClientTopic sudah dicabut (Task 14): create() dengan clientId milik Client
        // menghasilkan Paket Client di bawah Subject global yang sama, Topic bawaan ikut serta.
        pakets.create(new PaketService.PaketDraft("Bab lokal sekolah", subject.getId(), null),
                client.getId(), null);

        assertThat(subjects.findGlobalWithoutTopic())
                .extracting(SubjectEntity::getId).contains(subject.getId());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): pekerjaan macet milik sebuah Client tidak pernah masuk antrean Eduscreen")
    void antreanTidakMemuatPekerjaanClient() {
        var questionDrafSebelum = dashboard.antrean().questionDraf();

        ClientEntity client = data.client("SD Dashboard3");
        PaketEntity paketClient = data.paket(client, "Matematika Kelas 4 Dashboard Antrean", "Paket sekolah antrean");
        TopicEntity topicPaketClient = pakets.topicsOf(paketClient.getId()).get(0);
        data.mcq(client, topicPaketClient, "Soal sekolah antrean", 4);
        var subjectClient = subjects.save(SubjectEntity.forClient(client.getId(), "Bahasa Sunda Kelas 5 buntu"));

        var antrean = dashboard.antrean();

        // Diperiksa lewat query penuh, bukan Baris.tampil(): daftar tampilan dipotong lima nama
        // dan subjectBuntu diurut nama, jadi kalau suatu hari filter tenant hilang dari query,
        // baris milik Client cuma tertangkap kalau kebetulan masuk lima besar. Pengunci batas
        // tenant tidak boleh bergantung pada kebetulan seperti itu.
        assertThat(paketRepository.findMasterBlocked())
                .extracting(PaketEntity::getId).doesNotContain(paketClient.getId());
        assertThat(paketRepository.findMasterReadyToPublish())
                .extracting(PaketEntity::getId).doesNotContain(paketClient.getId());
        assertThat(subjects.findGlobalWithoutTopic())
                .extracting(SubjectEntity::getId).doesNotContain(subjectClient.getId());
        // soalClient lahir lewat data.mcq(...) tanpa publishedAt, tapi ber-clientId — tidak boleh
        // ikut terhitung sebagai Question master draf.
        assertThat(antrean.questionDraf()).isEqualTo(questionDrafSebelum);
    }

    @Test
    @DisplayName("BR-O05: antrean tanpa satu pun baris menyatakan dirinya kosong, sehingga bloknya tidak dirender")
    void antreanTanpaBarisMenyatakanDirinyaKosong() {
        var nihil = new EduscreenDashboardService.Baris<PaketEntity>(List.of(), 0);
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
