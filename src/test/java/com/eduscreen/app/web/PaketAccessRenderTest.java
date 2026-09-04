package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Layar akses Paket (ADR-0021): admin memberi, sekolah membaca dan merakit — dirender sungguhan (TC-13). */
@AutoConfigureMockMvc
class PaketAccessRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;
    @Autowired MasterPublishingService masterPublishing;
    @Autowired PaketAccessService access;
    @Autowired ExerciseService exercises;

    private record Siap(ClientEntity sekolah, PaketEntity master, TopicEntity topic, QuestionEntity soal) {
    }

    private Siap siap(String nama) {
        ClientEntity sekolah = data.client("SD Render Akses " + nama);
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Akses " + nama, "Paket akses " + nama);
        TopicEntity topic = paketService.topicsOf(master.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topic, "Soal akses " + nama + " unik");
        masterPublishing.publishPaket(master.getId());
        return new Siap(sekolah, master, topic, soal);
    }

    @Test
    @DisplayName("TC-13 (FR-067): layar Akses Paket dirender; memberi, memindahkan versi, dan mencabut lewat form sungguhan")
    void layarAksesAdmin() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        Siap s = siap("Admin");

        mockMvc.perform(get("/eduscreen/akses").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(s.sekolah().getName())));

        mockMvc.perform(get("/eduscreen/akses").param("clientId", s.sekolah().getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Beri Paket ke " + s.sekolah().getName())))
                .andExpect(content().string(containsString("<option value=\"" + s.master().getId() + "\"")))
                .andExpect(content().string(containsString("belum diberi Paket")));

        mockMvc.perform(post("/eduscreen/akses")
                        .param("clientId", s.sekolah().getId().toString())
                        .param("paketId", s.master().getId().toString())
                        .param("validUntil", "2030-06-30")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/eduscreen/akses?clientId=" + s.sekolah().getId()));

        var akses = access.activeFor(s.sekolah().getId()).get(0);
        mockMvc.perform(get("/eduscreen/akses").param("clientId", s.sekolah().getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket akses Admin")))
                .andExpect(content().string(containsString("Versi 1")))
                .andExpect(content().string(containsString("30 Jun 2030")))
                .andExpect(content().string(containsString("action=\"/eduscreen/akses/" + akses.getId() + "/cabut\"")));

        mockMvc.perform(post("/eduscreen/akses/{id}/cabut", akses.getId())
                        .param("clientId", s.sekolah().getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/eduscreen/akses").param("clientId", s.sekolah().getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("belum diberi Paket")));
    }

    @Test
    @DisplayName("TC-13 (TC-41): Bank Soal sekolah memuat Paket Eduscreen hanya-baca lewat akses; tanpa akses 404, dengan akses tanpa satu pun aksi tulis")
    void bankSoalSekolahHanyaBaca() throws Exception {
        Siap s = siap("Sekolah");
        var clientAdmin = user(data.principal(data.user(s.sekolah(), UserRole.CLIENT_ADMIN, "Admin")));
        var guru = user(data.principal(data.user(s.sekolah(), UserRole.GURU, "Guru")));

        mockMvc.perform(get("/bank-soal/paket/{id}", s.master().getId()).with(clientAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/bank-soal").with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Paket akses Sekolah"))));

        access.grant(s.sekolah().getId(), s.master().getId(), null, null);

        mockMvc.perform(get("/bank-soal").with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Eduscreen")))
                .andExpect(content().string(containsString("Paket akses Sekolah")));

        for (var pengguna : java.util.List.of(clientAdmin, guru)) {
            mockMvc.perform(get("/bank-soal/paket/{id}", s.master().getId()).with(pengguna))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Soal akses Sekolah unik")))
                    .andExpect(content().string(containsString("Versi 1")))
                    .andExpect(content().string(containsString("hanya bisa dibaca")))
                    .andExpect(content().string(not(containsString("hx-delete=\"/bank-soal/soal/" + s.soal().getId()))))
                    .andExpect(content().string(not(containsString("/soal/baru"))));
        }
        // Soal master tidak punya editor di sisi sekolah, akses atau tidak (TC-36).
        mockMvc.perform(get("/bank-soal/soal/{id}", s.soal().getId()).with(clientAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-13 (BR-E01): perakit Exercise menawarkan Paket Eduscreen sebagai sumber dan 'Tambah semua soal sumber ini' memasang soal masternya")
    void perakitExerciseMemakaiAkses() throws Exception {
        Siap s = siap("Builder");
        var guruEntity = data.user(s.sekolah(), UserRole.GURU, "Guru");
        var guru = user(data.principal(guruEntity));
        ExerciseEntity exercise = exercises.create(s.sekolah().getId(), "Ulangan akses render", guruEntity.getId());

        mockMvc.perform(get("/exercise/{id}", exercise.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Paket akses Builder"))));

        access.grant(s.sekolah().getId(), s.master().getId(), null, null);

        mockMvc.perform(get("/exercise/{id}", exercise.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket akses Builder")))
                .andExpect(content().string(containsString("Tambah semua soal sumber ini")));

        mockMvc.perform(post("/exercise/{id}/item/paket", exercise.getId())
                        .param("paketId", s.master().getId().toString())
                        .with(guru).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soal akses Builder unik")));
    }
}
