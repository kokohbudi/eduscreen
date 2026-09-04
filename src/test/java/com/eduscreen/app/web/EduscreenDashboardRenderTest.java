package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-O05: dashboard dan halaman Client berdiri sebagai dua halaman terpisah. */
@AutoConfigureMockMvc
class EduscreenDashboardRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;

    @Test
    @DisplayName("BR-O05: dashboard merender antrean berisi paket yang macet")
    void dashboardMerenderAntrean() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Dashboard Render", "Paket macet render");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        data.masterMcq(topic, "Isi paket macet render");

        mockMvc.perform(get("/eduscreen").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Butuh perhatian")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket macet render")))
                // Isi halaman Client sudah pindah; dashboard tidak lagi memuat form onboarding.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan"))));
    }

    @Test
    @DisplayName("BR-O05: /eduscreen/client memuat daftar Client dan tombol ke halaman Client baru, bukan antrean; form onboarding di halamannya sendiri")
    void halamanClientMemuatOnboarding() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        data.client("SD Render Dashboard");

        mockMvc.perform(get("/eduscreen/client").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SD Render Dashboard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/eduscreen/client/baru\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Butuh perhatian"))));

        mockMvc.perform(get("/eduscreen/client/baru").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/eduscreen/client\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan")));
    }

    @Test
    @DisplayName("BR-P04: nav Eduscreen muncul di seluruh halaman /eduscreen/**")
    void navMunculDiSeluruhHalamanEduscreen() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));

        for (String jalur : List.of("/eduscreen", "/eduscreen/client", "/eduscreen/bank-soal")) {
            mockMvc.perform(get(jalur).with(admin))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("id=\"nav-eduscreen\"")));
        }
    }

    @Test
    @DisplayName("BR-P04: Guru, Siswa, dan Client Admin tidak pernah melihat nav Eduscreen di portalnya")
    void navEduscreenTidakBocorKePeranClient() throws Exception {
        var tenants = data.twoTenants();

        mockMvc.perform(get("/guru").with(user(data.principal(tenants.a().guru()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
        mockMvc.perform(get("/siswa").with(user(data.principal(tenants.a().siswa()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
        mockMvc.perform(get("/admin").with(user(data.principal(tenants.a().admin()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
    }
}
