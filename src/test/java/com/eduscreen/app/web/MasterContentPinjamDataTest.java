package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.controller.PinjamPanelData;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrak JSON panel pinjam ruang kerja master (ADR-0019, TC-14a), sejajar
 * {@link BankSoalPinjamDataTest}: {@code GET /eduscreen/bank-soal/paket/{id}/pinjam} membalas
 * {@link PinjamPanelData} yang sama bentuknya dengan sisi Client, dirakit lewat
 * {@code BankSoalController.pinjamPanelData} yang sama supaya kesepadanan dua sisi juga berlaku
 * di bentuk datanya.
 */
@AutoConfigureMockMvc
class MasterContentPinjamDataTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;
    @Autowired ObjectMapper objectMapper;

    private PinjamPanelData ambil(UUID targetId, RequestPostProcessor principal,
                                  String... paramPairs) throws Exception {
        MockHttpServletRequestBuilder req = get("/eduscreen/bank-soal/paket/{id}/pinjam", targetId).with(principal);
        for (int i = 0; i + 1 < paramPairs.length; i += 2) {
            req = req.param(paramPairs[i], paramPairs[i + 1]);
        }
        String body = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PinjamPanelData.class);
    }

    @Test
    @DisplayName("TC-36: JSON panel pinjam master menawarkan Paket master saja, tanpa Paket milik Client, sekaligus lintas Subject (AC-B19)")
    void menawarkanPaketMasterSajaLintasSubject() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity target = data.masterPaket("Sejarah Kelas 9 JSON Master", "Paket master target JSON");
        // Satu-satunya Paket master lain, di Subject BERBEDA: membuktikan tabel Paket master
        // menawarkan lintas Subject juga, sama seperti sisi Client (AC-B19).
        PaketEntity sumberLain = data.masterPaket("Matematika Kelas 4 JSON Master", "Paket master sumber JSON lain subject");
        TopicEntity topikSumberLain = paketService.topicsOf(sumberLain.getId()).get(0);
        data.masterMcq(topikSumberLain, "Soal master json lintas subject");

        ClientEntity client = data.client("SD Master JSON Pinjam Bukan Master");
        PaketEntity paketClient = data.paket(client, "Sejarah Kelas 9 JSON Master", "Paket Client JSON bukan master");
        TopicEntity topikClient = paketService.topicsOf(paketClient.getId()).get(0);
        data.mcq(client, topikClient, "Soal Client json bukan master", 4);

        PinjamPanelData hasil = ambil(target.getId(), admin);

        assertThat(hasil.pakets()).extracting(PinjamPanelData.Opsi::label)
                .contains("Paket master sumber JSON lain subject")
                .doesNotContain("Paket Client JSON bukan master");
        assertThat(hasil.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .contains("Soal master json lintas subject")
                .doesNotContain("Soal Client json bukan master");
    }

    @Test
    @DisplayName("AC-B20: JSON panel pinjam master tidak menawarkan soal milik Paket tujuan sendiri")
    void tidakMenawarkanSoalMilikPaketTujuanSendiri() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity target = data.masterPaket("Kimia Kelas 9 JSON Master", "Paket master target JSON diri sendiri");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);
        data.masterMcq(topikTarget, "Soal master json milik paket tujuan sendiri");

        PinjamPanelData hasil = ambil(target.getId(), admin);

        assertThat(hasil.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .doesNotContain("Soal master json milik paket tujuan sendiri");
    }

    @Test
    @DisplayName("AC-B04: JSON panel pinjam master berhenti menawarkan soal begitu salinannya ada di Paket tujuan")
    void tidakMenawarkanSoalYangSudahDipinjam() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity sumber = data.masterPaket("Fisika Kelas 9 JSON Master B04", "Paket master sumber JSON B04");
        TopicEntity topikSumber = paketService.topicsOf(sumber.getId()).get(0);
        QuestionEntity dipinjam = data.masterMcq(topikSumber, "Soal master json sudah dipinjam");
        data.masterMcq(topikSumber, "Soal master json belum dipinjam");
        PaketEntity target = data.masterPaket("Fisika Kelas 9 JSON Master B04", "Paket master target JSON B04");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);

        PinjamPanelData sebelum = ambil(target.getId(), admin);
        assertThat(sebelum.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .contains("Soal master json sudah dipinjam", "Soal master json belum dipinjam");

        mockMvc.perform(post("/eduscreen/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("topicId", topikTarget.getId().toString())
                        .param("questionIds", dipinjam.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        PinjamPanelData sesudah = ambil(target.getId(), admin);
        assertThat(sesudah.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .doesNotContain("Soal master json sudah dipinjam")
                .contains("Soal master json belum dipinjam");
    }

    @Test
    @DisplayName("TC-36 (TC-09): Paket bukan master di GET .../pinjam ruang kerja master dijawab 404")
    void paketBukanMasterDijawab404() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        ClientEntity client = data.client("SD Master JSON 404");
        PaketEntity paketClient = data.paket(client, "Biologi JSON Master 404", "Paket Client JSON untuk 404 master");

        mockMvc.perform(get("/eduscreen/bank-soal/paket/{id}/pinjam", paketClient.getId()).with(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
