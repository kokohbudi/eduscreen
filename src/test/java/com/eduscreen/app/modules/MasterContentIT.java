package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ruang kerja konten master Eduscreen: penulisan Topic dan Question milik Eduscreen (FR-060
 * sampai FR-064), dan batasnya terhadap konten milik Client (FR-061, FR-080).
 *
 * <p>Konten master dibedakan dari konten Client hanya oleh kepemilikan — {@code clientId} null —
 * sehingga seluruh aturan bentuk Question tetap sama persis dan sengaja diuji ulang di sini
 * lewat jalur tulis yang berbeda.
 */
class MasterContentIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    QuestionService questionService;
    @Autowired
    TaxonomyService taxonomy;
    @Autowired
    PaketService pakets;
    @Autowired
    QuestionRepository questions;
    @Autowired
    SubjectRepository subjects;
    @Autowired
    PaketRepository paketRepository;
    @Autowired
    MasterPublishingService publishing;

    @Test
    @DisplayName("BR-O02 (FR-061): Topic yang dibuat Eduscreen Admin lahir GLOBAL tanpa pemilik Client")
    void topicMasterLahirGlobal() {
        SubjectEntity subject = subjects.save(SubjectEntity.global("Matematika Kelas 4 master"));

        TopicEntity topic = pakets.createGlobalTopic(subject.getId(), "Pecahan");

        // Kepemilikan Topic diwarisi dari Paket induknya (ADR-0018): master berarti Paket
        // tanpa Client, di bawah Subject yang sama.
        PaketEntity paket = data.paketOf(topic);
        assertThat(paket.getClientId()).isNull();
        assertThat(paket.getSubjectId()).isEqualTo(subject.getId());
    }

    @Test
    @DisplayName("TC-09 (FR-061): Subject milik sebuah Client tidak bisa menampung Topic master; ia diperlakukan seolah tidak ada")
    void subjectMilikClientTidakBisaMenampungTopicMaster() {
        ClientEntity client = data.client("SD Master1");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");

        assertThatThrownBy(() -> pakets.createGlobalTopic(data.subjectIdOf(topicClient), "Pecahan"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("BR-Q01 (FR-060, FR-062): Question master tersimpan tanpa pemilik Client dan tunduk aturan Option yang sama")
    void questionMasterTersimpanDanTundukAturanYangSama() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");

        QuestionEntity pilihanGanda = questionService.create(new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Berapa hasil 1/2 + 1/4?</p>", null,
                List.of(new QuestionService.OptionDraft("<p>3/4</p>", true),
                        new QuestionService.OptionDraft("<p>2/6</p>", false))), null, topic.getPaketId());
        QuestionEntity esai = questionService.create(new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.ESSAY, "<p>Jelaskan pecahan senilai.</p>",
                "<p>Pembahasan</p>", List.of()), null, topic.getPaketId());

        assertThat(pilihanGanda.getClientId()).isNull();
        assertThat(pilihanGanda.getSourceQuestionId()).isNull();
        assertThat(pilihanGanda.isPublished()).isFalse();
        assertThat(esai.getClientId()).isNull();

        // Aturan bentuk tidak dilonggarkan untuk konten master: dua kunci benar tetap ditolak
        // sebagai masukan yang salah, bukan sebagai kerusakan sistem (BR-Q01).
        assertThatThrownBy(() -> questionService.create(new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Dua kunci</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", true))), null, topic.getPaketId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BR-Q02 (FR-061): Question master menolak Topic milik sebuah Client")
    void questionMasterMenolakTopicMilikClient() {
        ClientEntity client = data.client("SD Master2");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        PaketEntity paketMaster = data.masterPaket("Matematika Kelas 4", "Paket master sasaran");

        assertThatThrownBy(() -> questionService.create(new QuestionService.QuestionDraft(
                topicClient.getId(), QuestionType.ESSAY, "<p>Soal</p>", null, List.of()),
                null, paketMaster.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("TC-25 (FR-064): pencarian ruang kerja master menemukan kata di isi soal dan menyaring per Subject")
    void pencarianMasterMenyaringPerSubjectDanKataKunci() {
        TopicEntity pecahan = data.globalTopic("Matematika Kelas 4", "Pecahan");
        TopicEntity gerak = data.globalTopic("Fisika Kelas 9", "Gerak Lurus");
        data.masterMcq(pecahan, "Pecahan senilai zebrakata");
        data.masterMcq(gerak, "Kecepatan rata-rata zebrakata");

        Page<QuestionEntity> semua = questionService.searchMaster(null, null, "zebrakata", null, PageRequest.of(0, 20));
        Page<QuestionEntity> hanyaMatematika = questionService.searchMaster(
                data.subjectIdOf(pecahan), null, "zebrakata", null, PageRequest.of(0, 20));

        assertThat(semua.getTotalElements()).isEqualTo(2);
        assertThat(hanyaMatematika.getContent()).hasSize(1);
        assertThat(hanyaMatematika.getContent().get(0).getTopicId()).isEqualTo(pecahan.getId());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): pencarian ruang kerja master tidak pernah memuat Question milik sebuah Client")
    void pencarianMasterTidakMemuatKontenClient() {
        ClientEntity client = data.client("SD Master3");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        data.mcq(client, topicClient, "Soal milik sekolah kunciunik", 4);

        Page<QuestionEntity> hasil = questionService.searchMaster(null, null, "kunciunik", null, PageRequest.of(0, 20));

        assertThat(hasil.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("TC-09 (FR-080): Question milik sebuah Client tidak terbaca lewat jalur konten master")
    void questionClientTidakTerbacaLewatJalurMaster() {
        ClientEntity client = data.client("SD Master4");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah", 4);

        assertThatThrownBy(() -> questionService.require(soalClient.getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------- keadaan terbit
    //
    // Tiga invarian master/salinan yang semula diuji di sini lewat adoptQuestions
    // (Question belum terbit tidak bisa diadopsi; menarik master tidak menyentuh salinan;
    // mengubah master tidak merambat ke salinan) pindah ke CatalogAdoptionIT lewat adoptPakets
    // sejak Task 8/ADR-0018 — satuan adopsinya berganti dari Question ke Paket, tapi aturannya
    // sendiri tidak berubah.

    @Test
    @DisplayName("AC-B16 (FR-072): Paket master tanpa satu pun Question ditolak terbit")
    void paketMasterKosongDitolakTerbit() {
        PaketEntity kosong = data.masterPaket("Matematika Kelas 4 Gerbang Kosong", "Paket tanpa soal");

        assertThatThrownBy(() -> publishing.publishPaket(kosong.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimal 1 soal");
    }

    @Test
    @DisplayName("AC-B17 (FR-067): Paket master yang belum terbit tidak muncul di katalog Client")
    void unpublishedPaketStaysHidden() {
        PaketEntity draf = data.masterPaket("Kimia Kelas 10 Master", "Draf Kimia");
        TopicEntity topic = pakets.topicsOf(draf.getId()).get(0);
        data.publishedMasterMcq(topic, "Soal supaya Paket ini boleh terbit");

        assertThat(paketRepository.findMasterPublished(draf.getSubjectId()))
                .extracting(PaketEntity::getId)
                .doesNotContain(draf.getId());

        publishing.publishPaket(draf.getId());

        assertThat(paketRepository.findMasterPublished(draf.getSubjectId()))
                .extracting(PaketEntity::getId)
                .contains(draf.getId());
    }

    @Test
    @DisplayName("AC-B12 (FR-067, FR-069): Paket master ditolak terbit selama masih memuat Question yang belum terbit, dan bisa diterbitkan begitu soal itu diterbitkan")
    void paketMasterDitolakTerbitSelagiMasihMemuatQuestionDraf() {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Gerbang Paket", "Paket berisi draf");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity draft = data.masterMcq(topic, "Belum terbit penyebab paket");

        // Tanpa gerbang ini, adoptPakets akan menyalin soal draf ini apa adanya ke setiap
        // sekolah yang mengadopsi — ContentAdoptionService sengaja tidak menyaring status terbit
        // per Question, jadi satu-satunya gerbang FR-067 untuk isi Paket ada di sini.
        assertThatThrownBy(() -> publishing.publishPaket(paket.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Belum terbit penyebab paket");

        publishing.publishQuestion(draft.getId());
        assertThat(publishing.publishPaket(paket.getId()).isPublished()).isTrue();
    }

    // hapusMasterTidakMenyentuhSalinan pindah ke CatalogAdoptionIT (AC-B09), memakai adoptPakets
    // menggantikan adoptQuestions — lihat catatan di atas paketMasterKosongDitolakTerbit.

    @Test
    @DisplayName("TC-10 (FR-066): keadaan terbit ditolak database pada konten milik sebuah Client")
    void kontenClientTidakBolehPunyaKeadaanTerbit() {
        ClientEntity client = data.client("SD Terbit6");
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topic, "Soal sekolah", 4);

        // Check constraint question_publish_master_only, bukan validasi layanan: satu jalur tulis
        // yang lupa memeriksa tidak boleh bisa menembusnya (Prinsip VII).
        soalClient.publish(java.time.OffsetDateTime.now());
        assertThatThrownBy(() -> questions.saveAndFlush(soalClient))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("BR-O03 (ADR-0004): nama Subject GLOBAL yang kembar ditolak, beda kapital dan spasi tepi tetap dihitung kembar")
    void namaSubjectGlobalKembarDitolak() {
        taxonomy.createGlobalSubject("Kimia Kelas 11 unik");

        assertThatThrownBy(() -> taxonomy.createGlobalSubject("  kimia KELAS 11 unik  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sudah ada");
    }

    @Test
    @DisplayName("BR-O03 (TC-36): dua Client boleh punya Subject lokal senama; aturan tunggal hanya mengikat yang GLOBAL")
    void subjectLokalSenamaLintasClientTetapBoleh() {
        ClientEntity satu = data.client("SD Nama1");
        ClientEntity dua = data.client("SD Nama2");

        taxonomy.createClientSubject(satu.getId(), "Bahasa Sunda Kelas 5");
        taxonomy.createClientSubject(dua.getId(), "Bahasa Sunda Kelas 5");

        assertThat(taxonomy.visibleSubjects(satu.getId()))
                .extracting(SubjectEntity::getName).contains("Bahasa Sunda Kelas 5");
        assertThat(taxonomy.visibleSubjects(dua.getId()))
                .extracting(SubjectEntity::getName).contains("Bahasa Sunda Kelas 5");
    }

    @Test
    @DisplayName("BR-O04 (BR-O02): rename Subject GLOBAL mengubah baris yang sama yang dibaca Client, bukan melahirkan salinan")
    void renameSubjectGlobalTerlihatClientTanpaSalinanBaru() {
        ClientEntity client = data.client("SD Rename1");
        SubjectEntity subject = taxonomy.createGlobalSubject("Kimia Kelas 11 salahketik");
        int sebelum = taxonomy.visibleSubjects(client.getId()).size();

        taxonomy.renameGlobalSubject(subject.getId(), "Kimia Kelas 12");

        List<SubjectEntity> terlihat = taxonomy.visibleSubjects(client.getId());
        assertThat(terlihat).hasSize(sebelum);
        assertThat(terlihat).extracting(SubjectEntity::getId).contains(subject.getId());
        assertThat(terlihat).extracting(SubjectEntity::getName)
                .contains("Kimia Kelas 12")
                .doesNotContain("Kimia Kelas 11 salahketik");
    }

    @Test
    @DisplayName("BR-O03: rename ke nama yang sudah dipakai Subject GLOBAL lain ditolak")
    void renameKeNamaYangSudahDipakaiDitolak() {
        taxonomy.createGlobalSubject("Fisika Kelas 10 bentrok");
        SubjectEntity lain = taxonomy.createGlobalSubject("Fisika Kelas 11 bentrok");

        assertThatThrownBy(() -> taxonomy.renameGlobalSubject(lain.getId(), "Fisika Kelas 10 bentrok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sudah ada");
    }

    @Test
    @DisplayName("BR-O04: rename yang hanya membetulkan kapital atau spasi tepi tidak menabrak dirinya sendiri")
    void renameKapitalisasiSendiriTidakMenabrakDirinya() {
        SubjectEntity subject = taxonomy.createGlobalSubject("biologi kelas 8 sendiri");

        taxonomy.renameGlobalSubject(subject.getId(), "  Biologi Kelas 8 Sendiri  ");

        assertThat(subjects.findById(subject.getId()).orElseThrow().getName())
                .isEqualTo("Biologi Kelas 8 Sendiri");
    }

    @Test
    @DisplayName("TC-09 (FR-061): Subject milik sebuah Client tidak bisa di-rename lewat jalur master")
    void subjectClientTidakBisaDirenameLewatJalurMaster() {
        ClientEntity client = data.client("SD Rename2");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");

        assertThatThrownBy(() ->
                taxonomy.renameGlobalSubject(data.subjectIdOf(topicClient), "Dirampas Eduscreen"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("BR-O05: penyaring status DRAF hanya memunculkan Question master yang belum terbit")
    void penyaringStatusDrafHanyaMemunculkanDraf() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Soal draf penyaring unik");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal terbit penyaring unik");

        var hasilDraf = questionService.searchMaster(null, topic.getId(), "penyaring unik",
                com.eduscreen.app.modules.assessment.domain.StatusTerbit.DRAF,
                PageRequest.of(0, 20));

        assertThat(hasilDraf.getContent()).extracting(QuestionEntity::getId)
                .contains(draf.getId())
                .doesNotContain(terbit.getId());

        var hasilSemua = questionService.searchMaster(null, topic.getId(), "penyaring unik",
                null, PageRequest.of(0, 20));

        assertThat(hasilSemua.getContent()).extracting(QuestionEntity::getId)
                .contains(draf.getId(), terbit.getId());
    }
}
