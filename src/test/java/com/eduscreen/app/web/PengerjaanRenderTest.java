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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Penanda "terjawab" dan posisi aktif di peta soal Quiz selalu datang dari server sebagai swap
 * out-of-band, tidak menunggu muat ulang halaman (TC-14: peta adalah HTML yang harus mendarat di
 * DOM, jadi jalurnya fragmen, bukan JSON). Navigasi soal membawa peta utuh; auto-save hanya
 * membawa tombol yang berubah.
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

    /**
     * Menyimpan jawaban Quiz tidak mengubah soalnya, jadi balasannya bukan batang soal utuh:
     * hanya baris status dan satu tombol peta yang berubah, sebagai swap out-of-band. Batang
     * soal, opsi, dan textarea yang sedang diketik tidak diganti di bawah jari Siswa, dan
     * server tidak membaca ulang soal yang tidak berubah.
     */
    @Test
    @DisplayName("TC-14: auto-save Quiz membalas status + satu tombol peta OOB, bukan batang soal utuh")
    void autoSaveQuizMembalasStatusDanSatuTombolPeta() throws Exception {
        Tenants tenants = data.twoTenants();
        var siswa = user(data.principal(tenants.a().siswa()));
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));
        var soal = examSessions.view(sesi, 0, false);
        var kirim = put("/siswa/sesi/{sid}/jawaban/{qid}", sesi.getId(), soal.sessionQuestion().getId())
                .with(siswa).with(csrf());
        kirim = soal.options().isEmpty()
                ? kirim.param("essayText", "jawaban")
                : kirim.param("selectedOptionId", soal.options().get(0).getId().toString());

        mockMvc.perform(kirim)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"status-simpan\"")))
                .andExpect(content().string(containsString("id=\"peta-0\"")))
                .andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("aria-current=\"true\"")))
                .andExpect(content().string(not(containsString("id=\"soal\""))))
                .andExpect(content().string(not(containsString("id=\"peta-soal\""))));
    }
}
