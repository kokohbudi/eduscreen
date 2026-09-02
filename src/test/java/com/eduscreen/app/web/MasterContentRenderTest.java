package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Setiap halaman dan fragmen baru benar-benar dirender, bukan sekadar dipetakan.
 *
 * <p>Ada karena dua galat templat pada fitur ini lolos seluruh tes layanan dan baru meledak saat
 * halaman disentuh: ekspresi {@code ${...}} bersarang, dan {@code th:each} yang ditaruh bersama
 * {@code th:replace} di elemen yang sama sehingga fragmen dipanggil sebelum variabel iterasinya
 * terikat. Keduanya kelas kesalahan yang hanya bisa ditangkap dengan merender (TC-13, TC-14).
 */
@AutoConfigureMockMvc
class MasterContentRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired MasterPublishingService masterPublishing;
    @Autowired PaketService paketService;
    @Autowired PaketRepository pakets;

    @Test
    @DisplayName("TC-13: kedua tingkat ruang kerja Bank Soal master dirender utuh, jalurnya sungguhan terpasang")
    void ruangKerjaBankSoalMasterDirender() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Render Tier", "Paket tier render unik");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.masterMcq(topic, "Soal tier3 render draf unik");

        // Tingkat 1: tabel Paket master lintas Subject, dengan opsi penyaring Subject-nya lewat
        // subjectId (bukan tingkat navigasi terpisah).
        mockMvc.perform(get("/eduscreen/bank-soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Paket tier render unik")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<option value=\"" + paket.getSubjectId() + "\"")));

        // Tersaring ke Subject ini: tabel yang sama, dengan status dan tombol Terbitkan (FR-066).
        mockMvc.perform(get("/eduscreen/bank-soal").param("subjectId", paket.getSubjectId().toString())
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket tier render unik")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Belum terbit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/paket/" + paket.getId() + "/terbit\"")))
                // Token CSRF harus benar-benar sampai ke HTML (lihat catatan yang sama di bawah).
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-headers=")))
                // Templat bersama ini menyusun jalurnya dari atribut model lewat `@{...}`, yang
                // memperlakukan isinya sebagai teks URL, bukan SpEL. Versi pertama karena itu
                // sempat merender `hx-post="basePath/..."` secara harfiah dan tetap membalas 200;
                // hanya pemeriksaan jalur seperti ini yang menangkapnya (lihat komentar kelas).
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("basePath"))))
                // Lihat catatan penjaga kedua di tingkat 2: templat tingkat ini juga dipakai dua sisi.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"/bank-soal"))));

        // Tingkat 2: isi Paket, soal dikelompokkan per Topic, dengan status dan tombol Terbitkan
        // Question — gerbang AC-B12 tidak bisa dibuka tanpa jalur ini (lihat brief Task 10).
        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal tier3 render draf unik")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Belum terbit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/soal/" + soal.getId() + "/terbit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/eduscreen/bank-soal/soal/" + soal.getId() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("basePath"))))
                // Penjaga kedua, gejala yang berbeda: yang di atas menangkap `basePath` yang
                // bocor HARFIAH ke HTML, yang ini menangkap jalur Client yang DIKERASKAN di
                // templat dua sisi. Keduanya pernah terjadi di cabang ini, dan penjaga pertama
                // tidak melihat yang kedua sama sekali — `href="/bank-soal..."` adalah URL yang
                // sah-sah saja bentuknya, cuma dipagari CLIENT_ADMIN/GURU sehingga Eduscreen
                // Admin menekannya dan dapat 403. Jalur master selalu berawalan
                // "/eduscreen/bank-soal, jadi kutip-lalu-/bank-soal hanya cocok pada yang keras.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"/bank-soal"))));

        // Editor soal master: formulir baru dan detail sesudah simpan.
        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}/soal/baru", paket.getId())
                        .param("topicId", topic.getId().toString()).with(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/eduscreen/bank-soal/soal/{id}", soal.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal tier3 render draf unik")));

        // Menyimpan perubahan dibalas fragmen detail di tempat, bukan halaman penuh (TC-14).
        mockMvc.perform(put("/eduscreen/bank-soal/soal/{id}", soal.getId())
                        .param("topicId", topic.getId().toString())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Soal tier3 render diubah unik</p>")
                        .param("optionBody", "<p>Benar</p>", "<p>Salah</p>")
                        .param("correctIndex", "0")
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Soal tier3 render diubah unik")));
    }

    @Test
    @DisplayName("TC-14 (FR-066, FR-068): menerbitkan dan menarik Paket master menukar satu baris")
    void terbitDanTarikPaketMenukarSatuBaris() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Fisika Kelas 9 Render Terbit", "Paket render terbit tarik");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        data.publishedMasterMcq(topic, "Soal siap terbit render");

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/terbit", paket.getId())
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"paket-" + paket.getId() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Terbit<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/paket/" + paket.getId() + "/tarik\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Terbitkan"))));
        assertThat(pakets.findByIdAndClientIdIsNull(paket.getId()).orElseThrow().isPublished()).isTrue();

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/tarik", paket.getId())
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Belum terbit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/paket/" + paket.getId() + "/terbit\"")));
        assertThat(pakets.findByIdAndClientIdIsNull(paket.getId()).orElseThrow().isPublished()).isFalse();
    }

    @Test
    @DisplayName("TC-14 (FR-066): menerbitkan dan menarik Question master menukar satu baris, gerbang AC-B12 bagi Paket induknya")
    void terbitDanTarikSoalMenukarSatuBaris() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Kimia Kelas 10 Render Terbit Soal", "Paket render terbit soal");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.masterMcq(topic, "Soal render terbit satu baris");

        mockMvc.perform(post("/eduscreen/bank-soal/soal/{id}/terbit", soal.getId())
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"soal-" + soal.getId() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Terbit<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/soal/" + soal.getId() + "/tarik\"")));

        mockMvc.perform(post("/eduscreen/bank-soal/soal/{id}/tarik", soal.getId())
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Belum terbit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/bank-soal/soal/" + soal.getId() + "/terbit\"")));
    }

    @Test
    @DisplayName("AC-B12 (FR-069): Paket master yang masih memuat Question draf ditolak terbit lewat layar, bukan cuma lewat layanan")
    void paketDenganSoalDrafDitolakTerbitLewatLayar() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Biologi Kelas 8 Render Gerbang", "Paket render gerbang layar");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        data.masterMcq(topic, "Soal draf penyebab gerbang layar");

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/terbit", paket.getId())
                        .with(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Soal draf penyebab gerbang layar")));
        assertThat(pakets.findByIdAndClientIdIsNull(paket.getId()).orElseThrow().isPublished()).isFalse();
    }

    @Test
    @DisplayName("TC-36 (BR-P04): Paket milik sebuah Client tidak pernah bocor ke ruang kerja master, walau berbagi Subject GLOBAL yang sama")
    void paketMilikClientTidakBocorKeRuangKerjaMaster() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Render Bocor", "Paket master render bocor");
        ClientEntity client = data.client("SD Render Bocor Master");
        data.paket(client, "Matematika Kelas 4 Render Bocor", "Paket milik Client render bocor");

        mockMvc.perform(get("/eduscreen/bank-soal").param("subjectId", master.getSubjectId().toString())
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket master render bocor")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Paket milik Client render bocor"))));

        // Tingkat pertama tanpa penyaring: seluruh Paket master lintas Subject, tautannya
        // sendiri harus berawalan /eduscreen/bank-soal — bukan /bank-soal yang dipagari
        // CLIENT_ADMIN/GURU dan akan membalas 403 ke Eduscreen Admin.
        mockMvc.perform(get("/eduscreen/bank-soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket master render bocor")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Paket milik Client render bocor"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"/bank-soal"))));
    }

    @Test
    @DisplayName("TC-13: membuat Paket master, menambah Topic, dan menyimpan Question lewat layar")
    void buatPaketTambahTopicSimpanSoalLewatLayar() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));

        MvcResult dibuat = mockMvc.perform(post("/eduscreen/bank-soal/paket")
                        .param("title", "Paket dibuat lewat layar unik")
                        .param("subjectName", "Matematika Kelas 4 Render Buat")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String lokasi = dibuat.getResponse().getHeader("Location");
        assertThat(lokasi).startsWith("/eduscreen/bank-soal/paket/");
        UUID paketId = UUID.fromString(lokasi.substring(lokasi.lastIndexOf('/') + 1));

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/topic", paketId)
                        .param("title", "Topik render buat")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/eduscreen/bank-soal/paket/" + paketId));

        // Indeks 0 = "Topik 1" bawaan (AC-B01), indeks 1 = yang baru saja ditambahkan.
        TopicEntity topicBaru = paketService.topicsOf(paketId).get(1);

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/soal", paketId)
                        .param("topicId", topicBaru.getId().toString())
                        .param("type", "ESSAY")
                        .param("bodyHtml", "<p>Soal dibuat lewat layar unik</p>")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/eduscreen/bank-soal/paket/" + paketId));

        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}", paketId).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Topik render buat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal dibuat lewat layar unik")));
    }

    @Test
    @DisplayName("TC-13: panel pinjam master benar-benar memakai jalur /eduscreen/bank-soal, bukan fallback Client yang dijawab 403 (regresi review Task 10)")
    void panelPinjamMasterMemakaiJalurSendiri() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity sumber = data.masterPaket("Fisika Kelas 9 Render Pinjam", "Paket sumber pinjam render");
        TopicEntity topicSumber = paketService.topicsOf(sumber.getId()).get(0);
        QuestionEntity soalSumber = data.masterMcq(topicSumber, "Soal sumber pinjam render unik");
        PaketEntity target = data.masterPaket("Fisika Kelas 9 Render Pinjam", "Paket tujuan pinjam render");
        TopicEntity topicTarget = paketService.topicsOf(target.getId()).get(0);

        // Sebelum perbaikan, basePath kosong di sini dan jalurnya jatuh ke fallback '/bank-soal'
        // Client — yang untuk EDUSCREEN_ADMIN dijawab 403, membuat panel ini gagal senyap.
        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("sourcePaketId", sumber.getId().toString())
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-get=\"/eduscreen/bank-soal/paket/" + target.getId() + "/pinjam\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/eduscreen/bank-soal/paket/" + target.getId() + "/pinjam\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal sumber pinjam render unik")));

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("topicId", topicTarget.getId().toString())
                        .param("questionIds", soalSumber.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/eduscreen/bank-soal/paket/" + target.getId()));

        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}", target.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal sumber pinjam render unik")));
    }

    @Test
    @DisplayName("TC-13 (FR-064): pencarian Question master lintas-Paket dirender dan menyaring per kata kunci")
    void pencarianMasterDirenderDanMenyaring() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4 Render Cari", "Pecahan render cari");
        data.masterMcq(topic, "Soal cari unik zebrapencarian");
        data.masterMcq(topic, "Soal lain tidak cocok render");

        mockMvc.perform(get("/eduscreen/bank-soal/cari").param("q", "zebrapencarian").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal cari unik zebrapencarian")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Soal lain tidak cocok render"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-delete=\"/eduscreen/bank-soal/soal/")));
    }

    @Test
    @DisplayName("BR-Q04 (FR-065): menghapus Question master lewat layar menukar baris jadi konfirmasi, dan soal itu tidak lagi terbaca")
    void hapusSoalMasterLewatLayar() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Kimia Kelas 10 Render Hapus", "Paket render hapus soal");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.masterMcq(topic, "Soal render dihapus unik");

        mockMvc.perform(delete("/eduscreen/bank-soal/soal/{id}", soal.getId()).with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dihapus")));

        mockMvc.perform(get("/eduscreen/bank-soal/soal/{id}", soal.getId()).with(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-O04: mengubah nama Subject GLOBAL lewat layar memuat ulang ke Paket Subject itu, nama baru terlihat")
    void renameSubjectMasterLewatLayar() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Biologi Kelas 8 Render Ubah Nama", "Paket render ubah nama");

        mockMvc.perform(post("/eduscreen/bank-soal/subject/{id}/nama", paket.getSubjectId())
                        .param("name", "Biologi Kelas 8 Render Sudah Diubah")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "/eduscreen/bank-soal?subjectId=" + paket.getSubjectId()));

        mockMvc.perform(get("/eduscreen/bank-soal").param("subjectId", paket.getSubjectId().toString())
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Biologi Kelas 8 Render Sudah Diubah")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/eduscreen/bank-soal/subject/" + paket.getSubjectId() + "/nama\"")));
    }

    @Test
    @DisplayName("TC-13 (AC-B11, FR-074): katalog Paket dirender per Subject, lengkap dengan penanda adopsi")
    void katalogPaketDirender() throws Exception {
        ClientEntity client = data.client("SD Render");
        var clientAdmin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin")));
        PaketEntity terbit = data.masterPaket("Matematika Kelas 4 Render", "Paket katalog render");
        PaketEntity draf = data.masterPaket("Matematika Kelas 4 Render", "Paket katalog masih digarap");
        data.publishedMasterMcq(paketService.topicsOf(terbit.getId()).get(0), "Isi katalog render");
        masterPublishing.publishPaket(terbit.getId());

        // Tanpa Subject dipilih: daftar Subject terlihat, tapi belum ada Paket yang dimuat.
        mockMvc.perform(get("/katalog").with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Matematika Kelas 4 Render")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Paket katalog render"))));

        // Subject dipilih: Paket TERBIT muncul, yang masih digarap tidak pernah bocor (FR-067, SC-013).
        mockMvc.perform(get("/katalog").param("subjectId", terbit.getSubjectId().toString()).with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket katalog render")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Paket katalog masih digarap"))));

        // Adopsi lewat endpoint sungguhan, bukan lewat service langsung: endpoint inilah yang
        // merender fragmen ringkasan, dan fragmen yang salah konstruksi (ekspresi ${...}
        // bersarang, dkk.) baru meledak persis di sini, bukan di tes layanan (TC-13).
        mockMvc.perform(post("/katalog/adopsi").param("paketIds", terbit.getId().toString())
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Paket")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lihat di Bank Soal saya")));

        // Setelah diadopsi, penanda "Sudah diadopsi" muncul (AC-B11).
        mockMvc.perform(get("/katalog").param("subjectId", terbit.getSubjectId().toString()).with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sudah diadopsi")));
    }

    /**
     * Tautan lanjutan pada ringkasan adopsi sempat menunjuk {@code /soal}, rute yang dibongkar
     * Task 14 tanpa ada yang kembali ke sini. Tidak ada tes yang menangkapnya karena semuanya
     * hanya memeriksa TEKS tautannya ada, bukan bahwa jalurnya menjawab.
     */
    @Test
    @DisplayName("TC-13 (AC-B05): tautan lanjutan pada ringkasan adopsi menunjuk rute Bank Soal yang hidup, bukan rute yang sudah dibongkar")
    void tautanRingkasanAdopsiMenunjukRuteHidup() throws Exception {
        ClientEntity client = data.client("SD Tautan Ringkasan");
        var clientAdmin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin")));
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Tautan", "Paket tautan ringkasan");
        data.publishedMasterMcq(paketService.topicsOf(master.getId()).get(0), "Soal tautan ringkasan");
        masterPublishing.publishPaket(master.getId());

        String ringkasan = mockMvc.perform(post("/katalog/adopsi")
                        .param("paketIds", master.getId().toString())
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Jalurnya DIBACA dari HTML, bukan ditulis ulang di sini: menuliskannya ulang cuma
        // menyalin asumsi yang sama dan tidak akan pernah menangkap rute yang dibongkar.
        Matcher tautan = Pattern.compile("href=\"([^\"]+)\"[^>]*>Lihat di Bank Soal saya")
                .matcher(ringkasan);
        assertThat(tautan.find())
                .as("fragmen ringkasan memuat tautan lanjutan ke Bank Soal")
                .isTrue();

        mockMvc.perform(get(URI.create(tautan.group(1))).with(clientAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-B14 (FR-077): adopsi kedua atas Paket yang sama dihentikan sebelum menyalin dan membalas peringatan, kiriman ulang dengan konfirmasi tetap menyalin")
    void adopsiBerulangMembalasPeringatanSebelumMenyalin() throws Exception {
        ClientEntity client = data.client("SD Render Ulang");
        var clientAdmin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin")));
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Render Ulang", "Paket render ulang unik");
        TopicEntity topic = paketService.topicsOf(master.getId()).get(0);
        data.publishedMasterMcq(topic, "Soal render ulang");
        masterPublishing.publishPaket(master.getId());

        // Adopsi pertama: langsung jalan, membalas ringkasan — tidak ada yang perlu diperingatkan.
        mockMvc.perform(post("/katalog/adopsi").param("paketIds", master.getId().toString())
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Paket")));

        // Adopsi kedua atas Paket yang sama TANPA konfirmasi: berhenti sebelum menyalin, membalas
        // peringatan yang menyebut Paket-nya dan tombol "Ya, salin lagi" — bukan lencana pasif.
        mockMvc.perform(post("/katalog/adopsi").param("paketIds", master.getId().toString())
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket render ulang unik")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ya, salin lagi")));

        assertThat(pakets.findByClientIdAndSubjectIdOrderByTitleAsc(client.getId(), master.getSubjectId()))
                .hasSize(1);

        // Kiriman ulang membawa confirm=true: tetap menyalin, melahirkan salinan kedua terpisah.
        mockMvc.perform(post("/katalog/adopsi")
                        .param("paketIds", master.getId().toString())
                        .param("confirm", "true")
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Paket")));

        assertThat(pakets.findByClientIdAndSubjectIdOrderByTitleAsc(client.getId(), master.getSubjectId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("TC-13: menekan tombol salin tanpa mencentang apa pun membalas fragmen ringkasan nol, bukan 400 yang membuat htmx diam-diam tidak menukar apa pun")
    void adopsiTanpaCentangMembalasRingkasanNol() throws Exception {
        ClientEntity client = data.client("SD Render Kosong");
        var clientAdmin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin")));

        mockMvc.perform(post("/katalog/adopsi").with(clientAdmin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0 Paket")));
    }

    @Test
    @DisplayName("TC-13 (FR-024): bank soal Client tetap memakai jalurnya sendiri, tidak tertular jalur ruang kerja master")
    void bankSoalClientTetapMemakaiJalurnyaSendiri() throws Exception {
        ClientEntity client = data.client("SD Render2");
        var guru = user(data.principal(data.user(client, UserRole.GURU, "Guru")));
        PaketEntity paket = data.paket(client, "Matematika Kelas 4 Render2", "Paket Render2");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        data.mcq(client, topic, "Soal sekolah render", 4);

        // GET /soal lama (QuestionBankController) sudah dicabut (Task 14): jalur Client sekarang
        // isi Paket Bank Soal, {@code basePath} jatuh ke bawaan "/bank-soal" lewat fallback
        // Thymeleaf (bank/isi.html), bukan disuntik jalur master. hx-delete diperiksa sebagai
        // JALUR (assertion review pasca Task 14), bukan ketidakhadirannya: Client memang punya
        // tombol Hapus (FR-018), tapi harus menunjuk /bank-soal/soal/, bukan
        // /eduscreen/bank-soal/soal/ — regresi di sini berarti jalur Client ketiban rute master.
        mockMvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/bank-soal/soal/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-delete=\"/bank-soal/soal/")))
                // Terbit/Tarik tetap hanya di ruang kerja master (${master ?: false}); Client
                // tidak pernah melihat kedua tombol itu maupun jalur /eduscreen/ mana pun.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hx-post=\"/bank-soal/soal/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/eduscreen/"))));
    }
}
