package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Layar Bank Soal dua tingkat benar-benar dirender, bukan sekadar dipetakan.
 *
 * <p>Alasan yang sama dengan {@code MasterContentRenderTest}: galat templat (ekspresi bersarang,
 * {@code th:each} bersama {@code th:replace} di elemen yang sama) lolos seluruh tes layanan dan
 * baru meledak saat halamannya disentuh. Setiap halaman dan fragmen baru di sini karena itu
 * disentuh lewat MockMvc (TC-13, TC-14).
 *
 * <p>Sejak revisi tingkat pertama (PO pasca-ADR-0018), {@code GET /bank-soal} menyambut dengan
 * tabel Paket lintas Subject dan formulir buat Paket, bukan tabel Subject tanpa satu pun jalan
 * membuat sesuatu. Subject turun jadi penyaring lewat {@code subjectId}, bukan tingkat navigasi
 * sendiri — drill tinggal dua tingkat: daftar Paket, lalu isi Paket.
 */
@AutoConfigureMockMvc
class BankSoalRenderTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;
    @Autowired TaxonomyService taxonomy;

    @Test
    @DisplayName("TC-13 (ADR-0018): dua tingkat Bank Soal dirender: tabel Paket dengan form buat Paket, lalu isi Paket per Topic")
    void duaTingkatDirender() throws Exception {
        ClientEntity client = data.client("SD Bank Render");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Bank")));
        PaketEntity paket = data.paket(client, "Matematika Kelas 4 BankRender", "Paket Aritmetika Render");
        TopicEntity topik1 = paketService.topicsOf(paket.getId()).get(0);
        data.mcq(client, topik1, "Soal isi paket render", 4);

        // Tingkat 1: tabel Paket menyambut langsung (bukan tabel Subject), lengkap dengan nama
        // Subject per baris dan form buat Paket. URL harus benar-benar terpasang, bukan
        // "basePath" harfiah — kelas kesalahan yang pernah lolos di templat bersama lain.
        mvc.perform(get("/bank-soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Aritmetika Render")))
                .andExpect(content().string(containsString("Matematika Kelas 4 BankRender")))
                .andExpect(content().string(containsString("/bank-soal/paket/" + paket.getId())))
                .andExpect(content().string(containsString("action=\"/bank-soal/paket\"")))
                .andExpect(content().string(not(containsString("basePath"))));

        // Subject sekarang penyaring lewat subjectId, bukan tingkat navigasi terpisah — tabel
        // yang sama tersaring, bukan berpindah templat.
        mvc.perform(get("/bank-soal").param("subjectId", paket.getSubjectId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Aritmetika Render")))
                .andExpect(content().string(containsString("/bank-soal/paket/" + paket.getId())));

        // Tingkat 2: soal dikelompokkan per Topic, tombol tambah soal menunjuk Topic-nya.
        mvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Topik 1")))
                .andExpect(content().string(containsString("Soal isi paket render")))
                .andExpect(content().string(containsString(
                        "/bank-soal/paket/" + paket.getId() + "/soal/baru?topicId=" + topik1.getId())))
                .andExpect(content().string(containsString(
                        "hx-get=\"/bank-soal/paket/" + paket.getId() + "/pinjam\"")))
                .andExpect(content().string(not(containsString("basePath"))));

        // Topic baru harus terlihat setelah redirect — sukses yang tak terlihat terbaca gagal.
        mvc.perform(post("/bank-soal/paket/{id}/topic", paket.getId())
                        .param("title", "Topik Pecahan Render").with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Topik Pecahan Render")));
    }

    @Test
    @DisplayName("AC-B18 (TC-13): tingkat pertama menampilkan Paket dari lebih dari satu Subject sekaligus, tanpa Paket milik Client lain")
    void tingkatPertamaLintasSubjectTanpaBocorClientLain() throws Exception {
        ClientEntity client = data.client("SD Bank Lintas Subject");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Lintas Subject")));
        PaketEntity paketMatematika = data.paket(client, "Matematika Kelas 4 BankLintas", "Paket Matematika Lintas");
        PaketEntity paketFisika = data.paket(client, "Fisika Kelas 8 BankLintas", "Paket Fisika Lintas");
        ClientEntity lain = data.client("SD Bank Lintas Lain");
        data.paket(lain, "Matematika Kelas 4 BankLintas", "Paket Milik Client Lain Lintas");

        mvc.perform(get("/bank-soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Matematika Lintas")))
                .andExpect(content().string(containsString("Paket Fisika Lintas")))
                .andExpect(content().string(not(containsString("Paket Milik Client Lain Lintas"))));

        // Bukti lumpuh-pulih penjaring subjectId ada di
        // #penyaringSubjectIdBenarBenarMenyaring — di sini cukup pastikan keduanya masih
        // muncul saat tidak disaring, supaya kedua tes tidak menguji hal yang sama.
        assertThat(paketMatematika.getSubjectId()).isNotEqualTo(paketFisika.getSubjectId());
    }

    @Test
    @DisplayName("AC-B18 (TC-13): penyaring subjectId pada tingkat pertama benar-benar menyaring, bukan hiasan")
    void penyaringSubjectIdBenarBenarMenyaring() throws Exception {
        ClientEntity client = data.client("SD Bank Saring");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Saring")));
        PaketEntity paketMatematika = data.paket(client, "Matematika Kelas 4 BankSaring", "Paket Matematika Saring");
        PaketEntity paketFisika = data.paket(client, "Fisika Kelas 8 BankSaring", "Paket Fisika Saring");

        mvc.perform(get("/bank-soal").param("subjectId", paketMatematika.getSubjectId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Matematika Saring")))
                .andExpect(content().string(not(containsString("Paket Fisika Saring"))));
    }

    @Test
    @DisplayName("AC-B01: Paket yang baru dibuat langsung menampilkan Topik 1 dan tombol tambah soal yang menunjuk Topic bawaan itu")
    void paketBaruLangsungSiapDitulisi() throws Exception {
        ClientEntity client = data.client("SD Bank B01");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B01")));

        String location = mvc.perform(post("/bank-soal/paket")
                        .param("title", "Paket Baru AC B01")
                        .param("subjectName", "IPA Kelas 5 BankRender")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/bank-soal/paket/")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Topik 1")))
                .andExpect(content().string(containsString("soal/baru?topicId=")));
    }

    @Test
    @DisplayName("TC-36 (TC-09, BR-P04): Paket dan soal milik Client lain dijawab 404 di rute baca maupun tulis Bank Soal")
    void milikClientLainDijawab404() throws Exception {
        ClientEntity milikku = data.client("SD Bank A");
        var admin = user(data.principal(data.user(milikku, UserRole.CLIENT_ADMIN, "Admin A")));
        ClientEntity lain = data.client("SD Bank B");
        PaketEntity paketLain = data.paket(lain, "Matematika Kelas 4 BankLain", "Paket Client Lain");
        TopicEntity topikLain = paketService.topicsOf(paketLain.getId()).get(0);
        QuestionEntity soalLain = data.mcq(lain, topikLain, "Soal client lain", 4);

        mvc.perform(get("/bank-soal/paket/{id}", paketLain.getId()).with(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bank-soal/paket/{id}/soal/baru", paketLain.getId())
                        .param("topicId", topikLain.getId().toString()).with(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bank-soal/paket/{id}/pinjam", paketLain.getId()).with(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bank-soal/soal/{id}", soalLain.getId()).with(admin))
                .andExpect(status().isNotFound());
        // Jalur tulisnya juga: PUT dengan muatan sah tetap 404, bukan menimpa milik Client lain.
        mvc.perform(put("/bank-soal/soal/{id}", soalLain.getId())
                        .param("topicId", topikLain.getId().toString())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Percobaan tulis lintas Client</p>")
                        .param("optionBody", "<p>a</p>", "<p>b</p>")
                        .param("correctIndex", "0")
                        .with(admin).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-B06: membuat Paket dengan nama Subject yang sudah ada memakai ulang Subject itu, tanpa peduli kapital dan spasi tepi")
    void namaSubjectYangSudahAdaDipakaiUlang() throws Exception {
        ClientEntity client = data.client("SD Bank B06");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B06")));
        data.paket(client, "Bahasa Jawa BankRender", "Paket Pertama AC B06");

        mvc.perform(post("/bank-soal/paket")
                        .param("title", "Paket Kedua AC B06")
                        .param("subjectName", "  bahasa jawa BANKRENDER ")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        long senama = taxonomy.visibleSubjects(client.getId()).stream()
                .filter(s -> s.getName().equalsIgnoreCase("Bahasa Jawa BankRender"))
                .count();
        assertThat(senama).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-B04 (AC-B03): panel pinjam tidak menampilkan soal yang salinannya sudah ada di Paket tujuan, dan salinannya tampil di isi Paket")
    void panelPinjamMenyembunyikanYangSudahDipinjam() throws Exception {
        ClientEntity client = data.client("SD Bank B04");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B04")));
        PaketEntity sumber = data.paket(client, "Fisika BankRender", "Paket Sumber Pinjam");
        TopicEntity topikSumber = paketService.topicsOf(sumber.getId()).get(0);
        QuestionEntity dipinjam = data.mcq(client, topikSumber, "Soal sudah dipinjam render", 4);
        data.mcq(client, topikSumber, "Soal belum dipinjam render", 4);
        PaketEntity target = data.paket(client, "Fisika BankRender", "Paket Target Pinjam");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);

        // Panel awal tanpa Paket diklik: tabel soal SUDAH terisi lintas Paket, tanpa menunggu
        // apa pun disaring dulu — itulah cacat yang diperbaiki (defect asli produk).
        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Sumber Pinjam")))
                .andExpect(content().string(containsString("Soal sudah dipinjam render")))
                .andExpect(content().string(containsString("Soal belum dipinjam render")));

        mvc.perform(post("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("topicId", topikTarget.getId().toString())
                        .param("questionIds", dipinjam.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Setelah dipinjam: hilang dari tabel soal (unfiltered maupun difilter ke Paket sumbernya),
        // Salin seluruh Topic tetap tersedia begitu Paket diklik.
        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("filterPaketId", sumber.getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soal belum dipinjam render")))
                .andExpect(content().string(not(containsString("Soal sudah dipinjam render"))))
                .andExpect(content().string(containsString("Salin seluruh Topic ini")));

        // Salinan hasil pinjam benar-benar mendarat di Paket tujuan (AC-B03).
        mvc.perform(get("/bank-soal/paket/{id}", target.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soal sudah dipinjam render")));

        // Pinjam satu Topic sumber sekaligus lewat tombolnya.
        mvc.perform(post("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("topicId", topikTarget.getId().toString())
                        .param("sourceTopicId", topikSumber.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("filterPaketId", sumber.getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Soal belum dipinjam render"))));
    }

    @Test
    @DisplayName("AC-B19: Paket baru di Subject yang belum punya Paket lain tetap menawarkan sumber dari Subject lain di panel pinjam")
    void panelPinjamMenawarkanSumberLintasSubjectUntukPaketSubjectSendirian() throws Exception {
        ClientEntity client = data.client("SD Bank Pinjam Lintas Subject");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Pinjam Lintas")));
        // Sejarah: Paket satu-satunya di Subject-nya, persis kasus yang ditemukan pemilik produk.
        PaketEntity sejarah = data.paket(client, "Sejarah Kelas 9 BankPinjam", "Paket Sejarah Sendirian");
        // Matematika: Subject lain, satu-satunya sumber yang tersedia untuk Sejarah.
        PaketEntity matematika = data.paket(client, "Matematika Kelas 4 BankPinjam", "Paket Matematika Sumber");
        TopicEntity topikMatematika = paketService.topicsOf(matematika.getId()).get(0);
        data.mcq(client, topikMatematika, "Soal matematika lintas subject", 4);

        mvc.perform(get("/bank-soal/paket/{id}/pinjam", sejarah.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Matematika Sumber")))
                .andExpect(content().string(containsString("Soal matematika lintas subject")));
    }

    @Test
    @DisplayName("TC-36: Paket milik Client lain tidak pernah muncul sebagai sumber di panel pinjam")
    void panelPinjamTidakMenawarkanPaketMilikClientLain() throws Exception {
        ClientEntity client = data.client("SD Bank Pinjam Sendiri");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Pinjam Sendiri")));
        PaketEntity target = data.paket(client, "Biologi BankPinjam", "Paket Target Sendiri");
        ClientEntity lain = data.client("SD Bank Pinjam Lain");
        PaketEntity paketLain = data.paket(lain, "Biologi BankPinjam", "Paket Milik Client Lain Pinjam");
        TopicEntity topikLain = paketService.topicsOf(paketLain.getId()).get(0);
        data.mcq(lain, topikLain, "Soal milik Client lain pinjam", 4);

        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Paket Milik Client Lain Pinjam"))))
                .andExpect(content().string(not(containsString("Soal milik Client lain pinjam"))));
    }

    @Test
    @DisplayName("AC-B20: panel pinjam tidak menawarkan soal milik Paket tujuan sendiri sebagai sumber")
    void panelPinjamTidakMenawarkanSoalMilikPaketTujuanSendiri() throws Exception {
        ClientEntity client = data.client("SD Bank Pinjam Diri Sendiri");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Pinjam Diri")));
        PaketEntity target = data.paket(client, "Kimia BankPinjam", "Paket Target Diri Sendiri");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);
        data.mcq(client, topikTarget, "Soal milik Paket tujuan sendiri", 4);

        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Soal milik Paket tujuan sendiri"))))
                .andExpect(content().string(not(containsString("Paket Target Diri Sendiri"))));
    }

    @Test
    @DisplayName("AC-B02: menyimpan soal ke sebuah Paket dengan Topic milik Paket lain dijawab 400")
    void topicPaketLainDitolak() throws Exception {
        ClientEntity client = data.client("SD Bank B02");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B02")));
        PaketEntity paketA = data.paket(client, "Kimia BankRender", "Paket A AC B02");
        PaketEntity paketB = data.paket(client, "Kimia BankRender", "Paket B AC B02");
        TopicEntity topikB = paketService.topicsOf(paketB.getId()).get(0);

        mvc.perform(post("/bank-soal/paket/{id}/soal", paketA.getId())
                        .param("topicId", topikB.getId().toString())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Soal salah paket</p>")
                        .param("optionBody", "<p>a</p>", "<p>b</p>")
                        .param("correctIndex", "0")
                        .with(admin).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-13 (TC-14): editor soal dalam Paket dirender untuk buat maupun ubah, dan PUT membalas fragmen detail")
    void editorDalamPaketDirender() throws Exception {
        ClientEntity client = data.client("SD Bank Editor");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Editor")));
        PaketEntity paket = data.paket(client, "Biologi BankRender", "Paket Editor Render");
        TopicEntity topik = paketService.topicsOf(paket.getId()).get(0);

        mvc.perform(get("/bank-soal/paket/{id}/soal/baru", paket.getId())
                        .param("topicId", topik.getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/bank-soal/paket/" + paket.getId() + "/soal\"")))
                .andExpect(content().string(containsString("Simpan &amp; buat lagi")))
                .andExpect(content().string(containsString("Paket Editor Render")))
                .andExpect(content().string(not(containsString("basePath"))));

        QuestionEntity soal = data.mcq(client, topik, "Soal editor render", 4);
        mvc.perform(get("/bank-soal/soal/{id}", soal.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "hx-put=\"/bank-soal/soal/" + soal.getId() + "\"")));

        mvc.perform(put("/bank-soal/soal/{id}", soal.getId())
                        .param("topicId", topik.getId().toString())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Soal editor render diubah</p>")
                        .param("optionBody", "<p>a</p>", "<p>b</p>")
                        .param("correctIndex", "0")
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tersimpan")))
                .andExpect(content().string(containsString("Soal editor render diubah")));
    }

    @Test
    @DisplayName("AC-B08: soal yang disimpan berturut-turut dari formulir Paket mendarat berurutan di Topic-nya")
    void soalTersimpanBerurutan() throws Exception {
        ClientEntity client = data.client("SD Bank B08");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B08")));
        PaketEntity paket = data.paket(client, "Sejarah BankRender", "Paket Urut AC B08");
        TopicEntity topik = paketService.topicsOf(paket.getId()).get(0);

        simpanSoal(paket, topik, "Soal urut pertama render", false, admin)
                .andExpect(status().is3xxRedirection());
        simpanSoal(paket, topik, "Soal urut kedua render", false, admin)
                .andExpect(status().is3xxRedirection());

        String halaman = mvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(halaman.indexOf("Soal urut pertama render"))
                .isLessThan(halaman.indexOf("Soal urut kedua render"))
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("AC-B15: \"Simpan & buat lagi\" kembali ke formulir soal baru pada Topic yang sama, simpan biasa kembali ke isi Paket")
    void simpanBuatLagiKembaliKeFormulirTopicYangSama() throws Exception {
        ClientEntity client = data.client("SD Bank B15");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin B15")));
        PaketEntity paket = data.paket(client, "Geografi BankRender", "Paket Lanjut AC B15");
        TopicEntity topik = paketService.topicsOf(paket.getId()).get(0);

        simpanSoal(paket, topik, "Soal lanjut pertama render", true, admin)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "/bank-soal/paket/" + paket.getId() + "/soal/baru?topicId=" + topik.getId()));

        simpanSoal(paket, topik, "Soal lanjut kedua render", false, admin)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/bank-soal/paket/" + paket.getId()));
    }

    /** POST satu soal pilihan ganda sah ke formulir Paket; {@code lanjut} meniru tombol "buat lagi". */
    private ResultActions simpanSoal(PaketEntity paket, TopicEntity topik, String body,
                                     boolean lanjut, RequestPostProcessor admin) throws Exception {
        var permintaan = post("/bank-soal/paket/{id}/soal", paket.getId())
                .param("topicId", topik.getId().toString())
                .param("type", "MULTIPLE_CHOICE")
                .param("bodyHtml", "<p>" + body + "</p>")
                .param("optionBody", "<p>a</p>", "<p>b</p>")
                .param("correctIndex", "0")
                .with(admin).with(csrf());
        if (lanjut) {
            permintaan = permintaan.param("lanjut", "1");
        }
        return mvc.perform(permintaan);
    }

    @Test
    @DisplayName("BR-P03 (BR-P02, AC-P04): Siswa ditolak dari seluruh Bank Soal termasuk detail soal, Guru di Client yang sama diterima")
    void pagarPeranBankSoal() throws Exception {
        ClientEntity client = data.client("SD Bank Peran");
        var siswa = user(data.principal(data.user(client, UserRole.SISWA, "Siswa Bank")));
        var guru = user(data.principal(data.user(client, UserRole.GURU, "Guru Bank")));

        mvc.perform(get("/bank-soal").with(siswa)).andExpect(status().isForbidden());
        mvc.perform(get("/bank-soal").with(guru)).andExpect(status().isOk());

        // AC-P04: Siswa yang mengetahui id sebuah Question tidak pernah mendapat isinya lewat
        // rute Bank Soal — SecurityConfig memagari seluruh prefiks /bank-soal/** per peran
        // sebelum controller sempat dipanggil, jadi id acak sudah cukup membuktikan pagarnya
        // menutup /bank-soal/soal/{id} juga, bukan cuma akarnya.
        mvc.perform(get("/bank-soal/soal/{id}", UUID.randomUUID()).with(siswa))
                .andExpect(status().isForbidden());
    }
}
