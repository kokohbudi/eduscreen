package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenant;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T039 — IDOR pada portal Siswa dan halaman pengerjaan ujian.
 *
 * <p>{@link com.eduscreen.app.modules.assessment.controller.ExamSessionController} dan
 * {@link com.eduscreen.app.modules.assessment.controller.SessionTimeController} adalah
 * permukaan paling sensitif di sistem (CONSTITUTION.md Pasal 3). Setiap tes di sini membuktikan
 * keempat lapis anti-IDOR dari luar lewat HTTP sungguhan: pengenal tak tertebak saja tidak
 * cukup tanpa verifikasi kepemilikan di setiap permintaan.
 */
@AutoConfigureMockMvc
class ExamSessionIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired ExamSessionService examSessions;
    @Autowired ExamSessionRepository sessionRepository;
    @Autowired SessionAnswerRepository answerRepository;

    @Test
    @DisplayName("TC-09: sesi milik Siswa lain, Siswa Client lain, dan sesi yang tidak ada membalas 404 identik")
    void sesiOrangLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));

        MvcResult siswaLainSamaClient = mockMvc.perform(get("/siswa/sesi/{id}", sesi.getId())
                        .with(user(data.principal(tenants.a().siswaLain()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult siswaClientLain = mockMvc.perform(get("/siswa/sesi/{id}", sesi.getId())
                        .with(user(data.principal(tenants.b().siswa()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(get("/siswa/sesi/{id}", UUID.randomUUID())
                        .with(user(data.principal(tenants.a().siswa()))))
                .andExpect(status().isNotFound())
                .andReturn();

        // Ketiga penyebab kegagalan — bukan pemilik dalam satu Client, bukan pemilik lintas
        // Client, dan pengenal yang tidak pernah ada — harus tidak bisa dibedakan dari luar.
        // Kepemilikan sesi tidak boleh menjadi oracle (TC-09, TC-11).
        String badan = tidakAda.getResponse().getContentAsString();
        assertEquals(badan, siswaLainSamaClient.getResponse().getContentAsString());
        assertEquals(badan, siswaClientLain.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TC-08: menyimpan jawaban ke sesi orang lain ditolak 404 dan tidak menulis jawaban apa pun")
    void simpanJawabanKeSesiOrangLainTidakMenulis() throws Exception {
        Tenants tenants = data.twoTenants();
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));
        SessionQuestionEntity soalPertama = examSessions.snapshotOf(sesi.getId()).get(0);

        mockMvc.perform(put("/siswa/sesi/{sessionId}/jawaban/{sessionQuestionId}",
                        sesi.getId(), soalPertama.getId())
                        .with(user(data.principal(tenants.a().siswaLain())))
                        .with(csrf())
                        .param("essayText", "jawaban selundupan"))
                .andExpect(status().isNotFound());

        // AnswerService memverifikasi kepemilikan sesi SEBELUM menyentuh SessionAnswer mana pun
        // (TC-08); 404 yang diam-diam tetap menulis adalah kegagalan yang lebih berbahaya
        // daripada 403 biasa.
        assertTrue(answerRepository.findBySessionQuestionId(soalPertama.getId()).isEmpty());
    }

    @Test
    @DisplayName("TC-08: menyelesaikan sesi orang lain ditolak 404 dan sesi asli tetap IN_PROGRESS")
    void selesaikanSesiOrangLainTidakMengubahStatus() throws Exception {
        Tenants tenants = data.twoTenants();
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));

        mockMvc.perform(post("/siswa/sesi/{id}/selesai", sesi.getId())
                        .with(user(data.principal(tenants.b().siswa())))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        ExamSessionEntity sesudahnya = sessionRepository
                .findByIdAndClientId(sesi.getId(), tenants.a().client().getId())
                .orElseThrow();
        assertEquals(SessionStatus.IN_PROGRESS, sesudahnya.getStatus());
    }

    @Test
    @DisplayName("TC-08: sisa waktu dan heartbeat sesi orang lain membalas 404")
    void waktuDanHeartbeatSesiOrangLainMembalas404() throws Exception {
        Tenants tenants = data.twoTenants();
        ExamSessionEntity sesi = examSessions.start(tenants.a().assignment().getId(),
                data.principal(tenants.a().siswa()));

        mockMvc.perform(get("/siswa/sesi/{id}/waktu", sesi.getId())
                        .with(user(data.principal(tenants.a().siswaLain()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/siswa/sesi/{id}/heartbeat", sesi.getId())
                        .with(user(data.principal(tenants.a().siswaLain())))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-S01: memulai Assignment di Ruangan yang tidak diikuti Siswa ditolak 404 tanpa melahirkan sesi")
    void mulaiAssignmentDiRuanganYangTidakDiikutiTidakMelahirkanSesi() throws Exception {
        Tenants tenants = data.twoTenants();
        Tenant clientA = tenants.a();

        // Ruangan kedua di Client YANG SAMA, sengaja tidak diikuti Siswa ini: kegagalannya harus
        // membuktikan pemeriksaan keanggotaan Ruangan, bukan sekadar batas Client (BR-S01).
        RuanganEntity ruanganLain = data.ruangan(clientA.client(), "Kelas Lain");
        data.join(ruanganLain, clientA.guru(), MemberRole.GURU);
        ExerciseEntity exerciseLain = data.exercise(clientA.client(), clientA.guru(),
                "Ulangan lain", clientA.questions());
        AssignmentEntity assignmentLain = data.publishedQuiz(clientA.client(), exerciseLain, ruanganLain,
                clientA.guru(), 60, OffsetDateTime.now().plusDays(1), 3);

        mockMvc.perform(post("/siswa/assignment/{id}/mulai", assignmentLain.getId())
                        .with(user(data.principal(clientA.siswa())))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Sesi lahir hanya saat Mulai benar-benar berhasil (BR-S01, ADR-0002); permintaan yang
        // ditolak tidak boleh meninggalkan satu baris sesi pun.
        assertTrue(examSessions.attemptsOf(assignmentLain.getId(), clientA.siswa().getId()).isEmpty());
    }
}
