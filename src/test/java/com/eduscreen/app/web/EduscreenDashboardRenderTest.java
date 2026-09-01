package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
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

/** TC-14: dashboard dan halaman Client berdiri sebagai dua halaman terpisah. */
@AutoConfigureMockMvc
class EduscreenDashboardRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;

    @Test
    @DisplayName("TC-14 (BR-O05): dashboard merender antrean berisi paket yang macet")
    void dashboardMerenderAntrean() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Isi paket macet render");
        data.masterExercise("Paket macet render", List.of(draf));

        mockMvc.perform(get("/eduscreen").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Butuh perhatian")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket macet render")))
                // Isi halaman Client sudah pindah; dashboard tidak lagi memuat form onboarding.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan"))));
    }

    @Test
    @DisplayName("TC-14: /eduscreen/client memuat daftar Client dan form onboarding, bukan antrean")
    void halamanClientMemuatOnboarding() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        data.client("SD Render Dashboard");

        mockMvc.perform(get("/eduscreen/client").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Butuh perhatian"))));
    }
}
