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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T040 — IDOR pada bank soal, perakit Exercise, dan penyajian gambar.
 *
 * <p>{@link com.eduscreen.app.modules.assessment.controller.QuestionBankController},
 * {@link com.eduscreen.app.modules.assessment.controller.ExerciseController}, dan
 * {@link com.eduscreen.app.modules.assessment.controller.ImageController} membentuk satu
 * permukaan: bank soal, Exercise yang merakitnya, dan gambar yang disisipkan di dalamnya harus
 * sama-sama tertutup dari Client lain (TC-08, TC-09, TC-26, TC-36).
 */
@AutoConfigureMockMvc
class ContentIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired QuestionRepository questions;
    @Autowired ImageService images;

    @Test
    @DisplayName("TC-09: Question Client lain dan Question yang tidak ada membalas 404 dengan body identik")
    void soalClientLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();
        QuestionEntity soalClientB = tenants.b().questions().get(0);

        MvcResult milikClientLain = mockMvc.perform(get("/soal/{id}", soalClientB.getId())
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(get("/soal/{id}", UUID.randomUUID())
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

        MvcResult hasil = mockMvc.perform(get("/soal")
                        .header("HX-Request", "true")
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

        mockMvc.perform(delete("/soal/{id}", soalClientB.getId())
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
    @DisplayName("AC-P04: Siswa yang mengetahui id Question tidak pernah mendapat isinya lewat /soal/{id}")
    void siswaTidakBisaMembacaQuestionLangsung() throws Exception {
        Tenants tenants = data.twoTenants();
        QuestionEntity soal = tenants.a().questions().get(0);

        MvcResult hasil = mockMvc.perform(get("/soal/{id}", soal.getId())
                        .with(user(data.principal(tenants.a().siswa()))))
                .andReturn();

        // SecurityConfig menolak peran SISWA di /soal/** lebih dulu (403); seandainya jalur itu
        // tercapai, controller sendiri membalas 404. Yang dituntut AC-P04 bukan kode angka yang
        // mana, melainkan isi soal tidak pernah keluar lewat salah satu dari keduanya.
        int statusDidapat = hasil.getResponse().getStatus();
        assertTrue(statusDidapat == 403 || statusDidapat == 404,
                "Diharapkan 403 atau 404, didapat " + statusDidapat);
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
    @DisplayName("TC-41 (FR-081): Guru, Siswa, dan Client Admin ditolak di ruang kerja konten master; Guru dan Siswa juga ditolak di katalog")
    void peranClientDitolakDiRuangKerjaMaster() throws Exception {
        Tenants tenants = data.twoTenants();

        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(get("/eduscreen/soal").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/eduscreen/paket").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
        }

        // Adopsi adalah kewenangan Client Admin; Guru meracik dari Question yang sudah ada di
        // Client-nya, bukan menarik sendiri dari katalog (FR-081, BR-E01).
        for (var pengguna : java.util.List.of(tenants.a().guru(), tenants.a().siswa())) {
            mockMvc.perform(get("/katalog").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("TC-41 (FR-081): peran Client tidak bisa membuat maupun mengubah nama Subject global")
    void peranClientTidakBisaMenyentuhSubjectGlobal() throws Exception {
        Tenants tenants = data.twoTenants();
        UUID subjectGlobal = data.globalTopic("Kimia Kelas 11 idor", "Asam Basa").getSubjectId();

        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(post("/eduscreen/subject").param("name", "Titipan")
                            .with(user(data.principal(pengguna))).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/eduscreen/subject/{id}/nama", subjectGlobal)
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
        UUID subjectClient = tenants.a().topic().getSubjectId();

        mockMvc.perform(post("/eduscreen/subject/{id}/nama", subjectClient)
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
                        get("/eduscreen/soal/{id}", soalClient.getId()).with(user(admin)))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult tidakAda = mockMvc.perform(
                        get("/eduscreen/soal/{id}", UUID.randomUUID()).with(user(admin)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClient.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): pencarian ruang kerja master tidak pernah memuat isi Question milik sebuah Client")
    void pencarianMasterTidakMembocorkanIsiSoalClient() throws Exception {
        Tenants tenants = data.twoTenants();
        String teksSoalClient = tenants.a().questions().get(0).getBodyText();

        MvcResult hasil = mockMvc.perform(get("/eduscreen/soal")
                        .header("HX-Request", "true")
                        .with(user(data.principal(data.eduscreenAdmin()))))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(hasil.getResponse().getContentAsString().contains(teksSoalClient));
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
}
