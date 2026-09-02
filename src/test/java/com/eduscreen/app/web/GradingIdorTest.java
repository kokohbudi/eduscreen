package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenant;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T086 — IDOR pada antrean dan penilaian jawaban essay.
 *
 * <p>{@link com.eduscreen.app.modules.assessment.controller.GradingController} menolak Guru
 * yang tidak ditugaskan di Ruangan Assignment itu dengan {@code 404}, identik dengan Assignment
 * atau jawaban yang tidak ada (BR-G01, AC-G01, TC-09). Setiap kelas tes menyiapkan Assignment
 * essay yang sudah selesai dikerjakan, lalu membuktikan hanya Guru yang benar-benar ditugaskan
 * yang bisa menyentuhnya.
 */
@AutoConfigureMockMvc
class GradingIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired ExamSessionService examSessions;
    @Autowired AnswerService answerService;
    @Autowired SessionFinalizer finalizer;
    @Autowired SessionAnswerRepository answerRepository;

    private Tenants tenants;
    private AssignmentEntity assignment;
    private UUID sessionAnswerId;

    /**
     * Satu Assignment berisi soal essay tunggal, Siswa sudah menjawab, dan sesinya sudah
     * difinalisasi lewat {@link SessionFinalizer#submit} — persis jalur yang membuat jawaban
     * essay itu benar-benar muncul di antrean penilaian Guru.
     */
    private void siapkanAssignmentEssayTerselesaikan() {
        tenants = data.twoTenants();
        Tenant clientA = tenants.a();

        QuestionEntity essay = data.essay(clientA.client(), clientA.topic(), "Jelaskan pecahan senilai");
        ExerciseEntity exercise = data.exercise(clientA.client(), clientA.guru(),
                "Ulangan essay", List.of(essay));
        assignment = data.publishedQuiz(clientA.client(), exercise, clientA.ruangan(), clientA.guru(),
                60, OffsetDateTime.now().plusDays(1), 3);

        ExamSessionEntity sesi = examSessions.start(assignment.getId(), data.principal(clientA.siswa()));
        SessionQuestionEntity soalPertama = examSessions.snapshotOf(sesi.getId()).get(0);
        answerService.save(sesi.getId(), soalPertama.getId(), null, "Jawaban essay siswa",
                data.principal(clientA.siswa()));
        finalizer.submit(sesi.getId(), clientA.client().getId());

        sessionAnswerId = answerRepository.findBySessionQuestionId(soalPertama.getId())
                .orElseThrow().getId();
    }

    @Test
    @DisplayName("AC-G01, BR-G01: Guru yang tidak ditugaskan di Ruangan Assignment mendapat 404 di antrean penilaian")
    void guruTidakDitugaskanMembalas404DiAntreanPenilaian() throws Exception {
        siapkanAssignmentEssayTerselesaikan();
        // Sengaja TIDAK di-join ke Ruangan mana pun — satu-satunya pembeda dari Guru yang sah.
        AppUserEntity guruKedua = data.user(tenants.a().client(), UserRole.GURU, "Guru Kedua Tidak Ditugaskan");

        mockMvc.perform(get("/guru/assignment/{id}/penilaian", assignment.getId())
                        .with(user(data.principal(guruKedua))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-G01, BR-G01: Guru yang tidak ditugaskan menilai jawaban dibalas 404 dan essayScore tetap null")
    void guruTidakDitugaskanMenilaiTidakMengubahSkor() throws Exception {
        siapkanAssignmentEssayTerselesaikan();
        AppUserEntity guruKedua = data.user(tenants.a().client(), UserRole.GURU, "Guru Kedua Tidak Ditugaskan");

        mockMvc.perform(put("/guru/jawaban/{id}/nilai", sessionAnswerId)
                        .with(user(data.principal(guruKedua)))
                        .with(csrf())
                        .param("essayScore", "80"))
                .andExpect(status().isNotFound());

        // 404 yang diam-diam tetap menilai lebih berbahaya daripada 403: Guru yang tidak
        // ditugaskan tidak boleh sanggup mengubah nilai walau permintaannya sendiri ditolak.
        SessionAnswerEntity jawaban = answerRepository.findById(sessionAnswerId).orElseThrow();
        assertNull(jawaban.getEssayScore());
    }

    @Test
    @DisplayName("TC-08: Guru dari Client lain mendapat 404 di antrean penilaian")
    void guruClientLainMembalas404() throws Exception {
        siapkanAssignmentEssayTerselesaikan();

        mockMvc.perform(get("/guru/assignment/{id}/penilaian", assignment.getId())
                        .with(user(data.principal(tenants.b().guru()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-G01 (kontrol positif): Guru yang benar-benar ditugaskan berhasil membuka dan menilai")
    void guruYangDitugaskanBerhasil() throws Exception {
        siapkanAssignmentEssayTerselesaikan();
        Tenant clientA = tenants.a();

        // Kontrol positif: tanpa ini, keempat tes 404 di atas bisa saja lulus karena penyiapan
        // yang salah (mis. Assignment tidak pernah ada), bukan karena penegakan izinnya benar.
        mockMvc.perform(get("/guru/assignment/{id}/penilaian", assignment.getId())
                        .with(user(data.principal(clientA.guru()))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/guru/jawaban/{id}/nilai", sessionAnswerId)
                        .with(user(data.principal(clientA.guru())))
                        .with(csrf())
                        .param("essayScore", "80"))
                .andExpect(status().isOk());
    }
}
