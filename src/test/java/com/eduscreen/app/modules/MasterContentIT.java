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
    @DisplayName("BR-O02 (FR-061): Paket yang dibuat Eduscreen Admin lahir GLOBAL tanpa pemilik Client, Topic bawaannya ikut serta")
    void topicMasterLahirGlobal() {
        SubjectEntity subject = subjects.save(SubjectEntity.global("Matematika Kelas 4 master"));

        // createGlobalTopic sudah dicabut (Task 14): create() dengan clientId null adalah jalan
        // sungguhan yang dipakai MasterContentController, dan ia melahirkan Topic bawaan sekali
        // jalan (PaketService.TOPIC_BAWAAN) — kepemilikan Topic diwarisi dari Paket ini
        // (ADR-0018).
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Pecahan", subject.getId(), null), null, null);

        assertThat(paket.getClientId()).isNull();
        assertThat(paket.getSubjectId()).isEqualTo(subject.getId());
    }

    @Test
    @DisplayName("TC-09 (FR-061): Subject milik sebuah Client tidak bisa menampung Paket master; ia diperlakukan seolah tidak ada")
    void subjectMilikClientTidakBisaMenampungPaketMaster() {
        ClientEntity client = data.client("SD Master1");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");

        // createGlobalTopic sudah dicabut (Task 14): pemeriksaan requireGlobalSubject yang sama
        // sekarang ditegakkan di dalam create() lewat requireSubject (lihat PaketService).
        assertThatThrownBy(() -> pakets.create(
                new PaketService.PaketDraft("Pecahan", data.subjectIdOf(topicClient), null), null, null))
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
    @DisplayName("TC-09 (FR-067): findMasterPublished menyaring Paket master pada keadaan terbitnya")
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
    @DisplayName("AC-B12 (ADR-0020): Paket berisi campuran draf dan soal terbit bisa terbit dengan drafnya ditinggal, atau dengan drafnya ikut serta")
    void paketMasterTerbitDenganAtauTanpaSoalDraf() {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Gerbang Paket", "Paket berisi draf");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        data.publishedMasterMcq(topic, "Soal siap gerbang paket");
        QuestionEntity draft = data.masterMcq(topic, "Belum terbit penyebab paket");

        // Terbit "yang siap saja": Paket naik, draf tetap draf. Yang menjaga draf itu tidak bocor
        // ke sekolah adalah penyaring di ContentAdoptionService (AC-B23), bukan gerbang di sini.
        assertThat(publishing.publishPaket(paket.getId(), false).isPublished()).isTrue();
        assertThat(questions.findByIdAndClientId(draft.getId(), null).orElseThrow().isPublished())
                .as("draf tidak boleh ikut terbit tanpa diminta")
                .isFalse();

        publishing.withdrawPaket(paket.getId());

        // Terbit "semua": draf yang sama ikut naik dalam satu tindakan.
        assertThat(publishing.publishPaket(paket.getId(), true).isPublished()).isTrue();
        assertThat(questions.findByIdAndClientId(draft.getId(), null).orElseThrow().isPublished()).isTrue();
    }

    @Test
    @DisplayName("AC-B16 (ADR-0020): Paket yang seluruh isinya masih draf ditolak terbit, sama seperti Paket kosong")
    void paketMasterTanpaSoalTerbitDitolakTerbit() {
        PaketEntity paket = data.masterPaket("Biologi Kelas 8 Gerbang Semua Draf", "Paket semua draf");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        data.masterMcq(topic, "Draf satu-satunya isi paket");

        assertThatThrownBy(() -> publishing.publishPaket(paket.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belum punya satu pun soal terbit");
    }

    @Test
    @DisplayName("AC-B22 (ADR-0020): satu tindakan menerbitkan seluruh soal draf di sebuah Paket sekaligus")
    void terbitkanSeluruhSoalDrafSekaligus() {
        PaketEntity paket = data.masterPaket("Kimia Kelas 10 Terbit Massal", "Paket terbit massal");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity a = data.masterMcq(topic, "Draf massal satu");
        QuestionEntity b = data.masterMcq(topic, "Draf massal dua");
        QuestionEntity sudah = data.publishedMasterMcq(topic, "Sudah terbit sebelum massal");

        assertThat(publishing.publishDraftQuestions(paket.getId())).isEqualTo(2);
        assertThat(publishing.draftQuestionsOf(paket.getId())).isEmpty();
        assertThat(questions.findByIdAndClientId(a.getId(), null).orElseThrow().isPublished()).isTrue();
        assertThat(questions.findByIdAndClientId(b.getId(), null).orElseThrow().isPublished()).isTrue();
        assertThat(questions.findByIdAndClientId(sudah.getId(), null).orElseThrow().isPublished()).isTrue();
    }

    @Test
    @DisplayName("AC-B17: menarik Question yang Paket induknya sedang terbit ditolak, dan diizinkan lagi begitu Paket itu ditarik")
    void tarikSoalDitolakSelamaPaketInduknyaTerbit() {
        PaketEntity paket = data.masterPaket("Fisika Kelas 9 Gerbang Balik", "Paket gerbang tarik soal");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topic, "Soal gerbang tarik");
        publishing.publishPaket(paket.getId());

        // Tanpa gerbang ini: Paket tetap terbit di katalog sementara isinya turun jadi draf, dan
        // adopsi menyalin soal draf itu ke sekolah. findMasterBlocked pun tidak memunculkannya
        // di dasbor, karena antrean itu hanya melihat Paket yang masih draf.
        assertThatThrownBy(() -> publishing.unpublishQuestion(soal.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Paket gerbang tarik soal")
                .hasMessageContaining("Tarik Paket itu dari katalog dulu");
        assertThat(questions.findByIdAndClientId(soal.getId(), null).orElseThrow().isPublished())
                .as("penolakan tidak boleh menyisakan perubahan separuh jalan")
                .isTrue();

        publishing.withdrawPaket(paket.getId());

        assertThat(publishing.unpublishQuestion(soal.getId()).isPublished()).isFalse();
    }

    @Test
    @DisplayName("AC-B17 (AC-B16): menghapus Question yang Paket induknya sedang terbit ditolak, dan diizinkan lagi begitu Paket itu ditarik")
    void hapusSoalDitolakSelamaPaketInduknyaTerbit() {
        PaketEntity paket = data.masterPaket("Fisika Kelas 9 Gerbang Hapus", "Paket gerbang hapus soal");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topic, "Soal gerbang hapus unik");
        publishing.publishPaket(paket.getId());

        // Ini satu-satunya soal Paket itu: menghapusnya menghasilkan Paket TERBIT yang KOSONG,
        // persis keadaan yang AC-B16 tolak saat penerbitan — dicapai lewat pintu belakang, dan
        // tetap bisa diadopsi sekolah.
        assertThatThrownBy(() -> questionService.softDelete(soal.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Paket gerbang hapus soal")
                .hasMessageContaining("Tarik Paket itu dari katalog dulu");
        assertThat(questionService.searchMaster(null, null, "gerbang hapus unik", null,
                PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);

        publishing.withdrawPaket(paket.getId());
        questionService.softDelete(soal.getId(), null);

        assertThat(questionService.searchMaster(null, null, "gerbang hapus unik", null,
                PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("AC-B17 (TC-36): menghapus soal milik Client tidak pernah tersandung gerbang terbit, karena Paket Client tidak punya keadaan terbit")
    void hapusSoalClientTidakTersandungGerbangTerbit() {
        ClientEntity client = data.client("SD Gerbang Terbit Client");
        PaketEntity paket = data.paket(client, "Matematika Kelas 4 Gerbang Client", "Paket Client gerbang");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.mcq(client, topic, "Soal client gerbang unik", 4);

        questionService.softDelete(soal.getId(), client.getId());

        assertThat(questions.findByIdAndClientId(soal.getId(), client.getId())).isEmpty();
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
