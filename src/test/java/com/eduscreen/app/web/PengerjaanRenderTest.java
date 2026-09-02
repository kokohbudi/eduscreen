package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Peta soal Quiz ikut dalam setiap balasan fragmen sebagai swap out-of-band, sehingga penanda
 * "terjawab" dan posisi aktif tidak menunggu muat ulang halaman (TC-14: peta adalah HTML yang
 * harus mendarat di DOM, jadi jalurnya fragmen, bukan JSON).
 */
@AutoConfigureMockMvc
class PengerjaanRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired ExamSessionService examSessions;

    @Test
    @DisplayName("TC-14: fragmen soal membawa peta soal ber-hx-swap-oob, dan muat awal hanya merender satu peta")
    void fragmenSoalMembawaPetaOob() throws Exception {
        Tenants tenants = data.twoTenants();
        var siswa = user(data.principal(tenants.a().siswa()));
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));

        String halaman = mockMvc.perform(get("/siswa/sesi/{id}", sesi.getId()).with(siswa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, halaman.split("id=\"peta-soal\"", -1).length - 1,
                "muat awal merender tepat satu peta soal");
        assertEquals(1, halaman.split("id=\"soal\"", -1).length - 1,
                "muat awal merender tepat satu batang soal");

        mockMvc.perform(get("/siswa/sesi/{id}/soal/{position}", sesi.getId(), 0).with(siswa))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"peta-soal\" hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("aria-current=\"true\"")))
                .andExpect(content().string(containsString("id=\"soal\"")));
    }
}
