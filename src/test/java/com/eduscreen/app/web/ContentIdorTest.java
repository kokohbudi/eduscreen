package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.StoredImageEntity;
import com.eduscreen.app.modules.assessment.service.ImageService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T040 — IDOR pada bank soal, perakit Exercise, dan penyajian gambar.
 *
 * <p>{@link com.eduscreen.app.modules.assessment.controller.BankSoalController},
 * {@link com.eduscreen.app.modules.assessment.controller.ExerciseController}, dan
 * {@link com.eduscreen.app.modules.assessment.controller.ImageController} membentuk satu
 * permukaan: bank soal, Exercise yang merakitnya, dan gambar yang disisipkan di dalamnya harus
 * sama-sama tertutup dari Client lain (TC-08, TC-09, TC-26, TC-36).
 *
 * <p>{@code QuestionBankController} lama (rute {@code /soal/**}, {@code /subject/**}) sudah
 * dicabut (Task 14, ADR-0018): penggantinya {@link
 * com.eduscreen.app.modules.assessment.controller.BankSoalController} mengambil alih seluruh
 * kasus di sini — detail (404 identik), hapus (TC-09), dan pencarian (AC-P02) lewat
 * {@code /bank-soal/soal/{id}} dan {@code /bank-soal/cari}. Pemblokiran peran SISWA (AC-P04)
 * diuji di {@code BankSoalRenderTest#pagarPeranBankSoal}, bukan di sini, karena pagarnya berbasis
 * prefiks {@code /bank-soal/**} di {@code SecurityConfig} — satu 403 di sana sudah membuktikan
 * seluruh subpath tertutup bagi SISWA.
 */
@AutoConfigureMockMvc
class ContentIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired QuestionRepository questions;
    @Autowired ImageService images;
    @Autowired com.eduscreen.app.modules.assessment.service.PaketService paketService;
    @Autowired com.eduscreen.app.modules.assessment.service.MasterPublishingService masterPublishing;
    @Autowired com.eduscreen.app.modules.assessment.service.PaketAccessService access;

    @Test
    @DisplayName("TC-09: Question Client lain dan Question yang tidak ada membalas 404 dengan body identik")
    void soalClientLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();
        QuestionEntity soalClientB = tenants.b().questions().get(0);

        // Rute Bank Soal ({@code GET /bank-soal/soal/{id}}), penerus /soal/{id} lama (Task 14).
        MvcResult milikClientLain = mockMvc.perform(get("/bank-soal/soal/{id}", soalClientB.getId())
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(get("/bank-soal/soal/{id}", UUID.randomUUID())
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClientLain.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("AC-P02: pencarian bank soal Guru Client A tidak pernah memuat Question Client B")
    void pencarianBankSoalTidakMemuatSoalClientLain() throws Exception {
        Tenants tenants = data.twoTenants();
        String teksSoalClientB = tenants.b().questions().get(0).getBodyText();

        // GET /bank-soal/cari (Task 14): penerus GET /soal lama, sama-sama menyaring clientId di
        // klausa query lewat QuestionService.searchForBuilder.
        MvcResult hasil = mockMvc.perform(get("/bank-soal/cari")
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isOk())
                .andReturn();

        // Batas Client sudah ditegakkan di klausa query (TC-08); ini pembuktian sisi hitam-kotak
        // bahwa isinya benar-benar tidak pernah keluar lewat body respons (AC-P02).
        assertFalse(hasil.getResponse().getContentAsString().contains(teksSoalClientB));
    }

    @Test
    @DisplayName("TC-09: menghapus Question Client lain ditolak 404 dan soalnya tetap ada")
    void hapusSoalClientLainTidakMenghapusApaPun() throws Exception {
        Tenants tenants = data.twoTenants();
        QuestionEntity soalClientB = tenants.b().questions().get(0);

        // DELETE /bank-soal/soal/{id} (Task 14): kapasitas hapus sisi Client dibuka kembali
        // setelah keliru dicatat sudah dicabut permanen (temuan review Task 14).
        mockMvc.perform(delete("/bank-soal/soal/{id}", soalClientB.getId())
                        .with(user(data.principal(tenants.a().guru())))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // 404 yang diam-diam tetap menghapus adalah kegagalan yang lebih buruk daripada 403;
        // @SQLRestriction membuat soal yang terhapus lunak hilang dari query ini juga, jadi
        // "masih ada" berarti deleted_at benar-benar masih null.
        assertTrue(questions.findByIdAndClientId(soalClientB.getId(), tenants.b().client().getId()).isPresent());
    }

    @Test
    @DisplayName("TC-09: Exercise Client lain membalas 404")
    void exerciseClientLainMembalas404() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(get("/exercise/{id}", tenants.b().exercise().getId())
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-09: menambah item ke Exercise Client lain ditolak 404")
    void tambahItemKeExerciseClientLainDitolak() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(post("/exercise/{id}/item", tenants.b().exercise().getId())
                        .with(user(data.principal(tenants.a().guru())))
                        .with(csrf())
                        .param("questionId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-26: gambar milik Guru Client lain membalas 404, bukan tersaji lewat URL yang dibagikan")
    void gambarClientLainMembalas404() throws Exception {
        Tenants tenants = data.twoTenants();
        StoredImageEntity gambar = images.store(pngKecil(), data.principal(tenants.a().guru()));

        // Tanpa endpoint berotorisasi ini, empat lapis anti-IDOR yang menjaga bank soal dan
        // sesi ujian bisa dilewati begitu saja: siapa pun yang memegang satu tautan .png membaca
        // isinya tanpa pernah menyentuh endpoint Soal atau Session yang dijaga.
        mockMvc.perform(get("/gambar/{id}", gambar.getId())
                        .with(user(data.principal(tenants.b().guru()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-14: POST /gambar mengunggah dan membalas fragmen gambarTerunggah untuk Guru")
    void unggahGambarMembalasFragmenTerunggah() throws Exception {
        // POST /gambar pindah dari QuestionBankController ke ImageController (Task 14); jalur ini
        // tidak punya tes tingkat HTTP sebelum maupun sesudah pindah (temuan review Task 14) —
        // memastikan pindahnya benar-benar tuntas, bukan cuma lolos kompilasi.
        Tenants tenants = data.twoTenants();
        MockMultipartFile berkas = new MockMultipartFile("berkas", "soal.png", "image/png", pngKecil());

        mockMvc.perform(multipart("/gambar").file(berkas)
                        .with(user(data.principal(tenants.a().guru())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/gambar/")));
    }

    private byte[] pngKecil() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // ------------------------------------------------------ batas ruang kerja konten master
    //
    // Dua arah, dan keduanya wajib: peran Client tidak boleh masuk ke ruang kerja master, dan
    // Eduscreen Admin tidak boleh keluar dari sana menuju data sebuah sekolah (FR-080, FR-081,
    // BR-P04, TC-41).

    @Test
    @DisplayName("TC-41 (FR-081): Guru, Siswa, dan Client Admin ditolak di ruang kerja konten master dan di layar akses Paket")
    void peranClientDitolakDiRuangKerjaMaster() throws Exception {
        Tenants tenants = data.twoTenants();

        // Sejak Task 10 ruang kerja master satu jalur saja, /eduscreen/bank-soal — dulu dua rute
        // terpisah (/eduscreen/soal untuk Question, /eduscreen/paket untuk Exercise master).
        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(get("/eduscreen/bank-soal").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
        }

        // Akses Paket diberi Eduscreen Admin; tidak ada peran sekolah yang bisa mengambil sendiri
        // (Pasal 3, ADR-0021), termasuk Client Admin.
        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(get("/eduscreen/akses").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/eduscreen/akses").param("clientId", tenants.a().client().getId().toString())
                            .param("paketId", UUID.randomUUID().toString())
                            .with(user(data.principal(pengguna))).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("TC-41 (FR-081): peran Client tidak bisa mengubah nama Subject global")
    void peranClientTidakBisaMerenameSubjectGlobal() throws Exception {
        Tenants tenants = data.twoTenants();
        UUID subjectGlobal = data.subjectIdOf(data.globalTopic("Kimia Kelas 11 idor rename", "Asam Basa"));

        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(post("/eduscreen/bank-soal/subject/{id}/nama", subjectGlobal)
                            .param("name", "Dirampas")
                            .with(user(data.principal(pengguna))).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("TC-09 (FR-061): Eduscreen Admin mengubah nama Subject milik sebuah Client menerima 404")
    void eduscreenAdminTidakBisaMerenameSubjectClient() throws Exception {
        Tenants tenants = data.twoTenants();
        var admin = data.principal(data.eduscreenAdmin());
        UUID subjectClient = data.subjectIdOf(tenants.a().topic());

        mockMvc.perform(post("/eduscreen/bank-soal/subject/{id}/nama", subjectClient)
                        .param("name", "Dirampas Eduscreen").with(user(admin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-09 (FR-080): Eduscreen Admin meminta Question milik sebuah Client menerima 404 yang identik dengan pengenal tak dikenal")
    void eduscreenAdminTidakBisaMembacaSoalClient() throws Exception {
        Tenants tenants = data.twoTenants();
        var admin = data.principal(data.eduscreenAdmin());
        QuestionEntity soalClient = tenants.a().questions().get(0);

        MvcResult milikClient = mockMvc.perform(
                        get("/eduscreen/bank-soal/soal/{id}", soalClient.getId()).with(user(admin)))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult tidakAda = mockMvc.perform(
                        get("/eduscreen/bank-soal/soal/{id}", UUID.randomUUID()).with(user(admin)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClient.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TC-26 (FR-063): Eduscreen Admin bisa membaca gambar master, dan gambar milik sebuah Client tetap tertutup baginya")
    void eduscreenAdminMembacaGambarMasterTapiTidakGambarClient() throws Exception {
        Tenants tenants = data.twoTenants();
        var admin = data.principal(data.eduscreenAdmin());

        StoredImageEntity gambarMaster = images.store(pngSatuPiksel(), admin);
        StoredImageEntity gambarClient = images.store(pngSatuPiksel(),
                data.principal(tenants.a().guru()));

        mockMvc.perform(get("/gambar/{id}", gambarMaster.getId()).with(user(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/gambar/{id}", gambarClient.getId()).with(user(admin)))
                .andExpect(status().isNotFound());
        // Gambar master dibaca semua Client — ia menempel di Question master yang mereka adopsi.
        mockMvc.perform(get("/gambar/{id}", gambarMaster.getId())
                        .with(user(data.principal(tenants.b().guru()))))
                .andExpect(status().isOk());
    }

    private byte[] pngSatuPiksel() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("TC-41 (FR-081): dashboard Eduscreen tetap tertutup bagi seluruh peran Client")
    void peranClientDitolakDiDashboard() throws Exception {
        Tenants tenants = data.twoTenants();

        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(get("/eduscreen").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/eduscreen/client").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("TC-41 (ADR-0021): endpoint versi dan akses ditolak lintas-Client dan lintas-peran — 404 identik, tidak ada yang berubah")
    void endpointVersiDanAksesLintasClient() throws Exception {
        Tenants tenants = data.twoTenants();
        var masterPaket = data.masterPaket("Matematika Kelas 4 idor versi", "Paket idor versi");
        var topic = paketService.topicsOf(masterPaket.getId()).get(0);
        var soalMaster = data.publishedMasterMcq(topic, "Soal idor versi");
        masterPublishing.publishPaket(masterPaket.getId());
        var akses = access.grant(tenants.a().client().getId(), masterPaket.getId(), null, null);
        var versi = paketService.versionOf(masterPaket.getId());

        // Guru A: /bank-soal terbuka untuknya, tapi memindahkan versi milik Client Admin (BR-O06).
        mockMvc.perform(post("/bank-soal/akses/{id}/versi", akses.getId())
                        .param("versionId", versi.getId().toString())
                        .with(user(data.principal(tenants.a().guru()))).with(csrf()))
                .andExpect(status().isNotFound());
        // Client Admin B: akses milik A tidak ada baginya.
        mockMvc.perform(post("/bank-soal/akses/{id}/versi", akses.getId())
                        .param("versionId", versi.getId().toString())
                        .with(user(data.principal(tenants.b().admin()))).with(csrf()))
                .andExpect(status().isNotFound());
        // Guru B: Paket master tanpa akses tidak bisa dipasang ke Exercise-nya.
        mockMvc.perform(post("/exercise/{id}/item/paket", tenants.b().exercise().getId())
                        .param("paketId", masterPaket.getId().toString())
                        .with(user(data.principal(tenants.b().guru()))).with(csrf()))
                .andExpect(status().isNotFound());
        // Guru B: Paket sekolah A pun tidak.
        mockMvc.perform(post("/exercise/{id}/item/paket", tenants.b().exercise().getId())
                        .param("paketId", data.paketOf(tenants.a().topic()).getId().toString())
                        .with(user(data.principal(tenants.b().guru()))).with(csrf()))
                .andExpect(status().isNotFound());

        // Eduscreen Admin: rute versi dan revisi hanya untuk Paket/soal master; milik Client → 404.
        var eduscreen = user(data.principal(data.eduscreenAdmin()));
        var paketClient = data.paketOf(tenants.a().topic());
        var soalClient = tenants.a().questions().get(0);
        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/versi-baru", paketClient.getId())
                        .with(eduscreen).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/instance-baru", paketClient.getId())
                        .param("title", "Rampas").with(eduscreen).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/eduscreen/bank-soal/soal/{id}/revisi", soalClient.getId())
                        .param("topicTitle", "x").param("type", "ESSAY").param("bodyHtml", "<p>rampas</p>")
                        .with(eduscreen).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals("Soal 1 SD Alfa", data.questionsInPaket(paketClient.getId()).get(0).getBodyText());
        // Soal master masih di versinya, akses A masih menunjuk versi 1.
        assertEquals(versi.getId(), access.activeFor(tenants.a().client().getId()).get(0).getVersionId());
        assertTrue(access.usable(tenants.a().client().getId()).stream()
                .anyMatch(a -> a.getPaketId().equals(masterPaket.getId())));
        assertFalse(soalMaster.isSuperseded());
    }
}
