package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T044 — Rekap Guru: dibangun dari anggota Ruangan (bukan dari sesi), dan perhitungan skor satu
 * sesi (BR-C01 sampai BR-C06).
 */
class AssignmentReportIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    ReportService reportService;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    AnswerService answerService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    ExamSessionRepository sessions;
    @Autowired
    ResultRepository results;

    @Test
    @DisplayName("AC-L01: rekap dibangun dari anggota Ruangan, dan sesi terbengkalai difinalisasi saat rekap dibuka")
    void recapListsAllRoomMembersAndFinalizesStaleSessions() {
        ClientEntity client = data.client("SD Rekap");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Bu Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 5A");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Matematika", "Pecahan");
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Pecahan",
                List.of(data.mcq(client, topic, "Soal 1", 4), data.mcq(client, topic, "Soal 2", 4)));

        // Spec aslinya (BR-L01) memakai 30 Siswa; di sini diperkecil jadi 5 karena yang diuji
        // adalah ATURANNYA (rekap dari anggota Ruangan, bukan dari sesi), bukan skalanya.
        List<AppUserEntity> students = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa " + i);
            data.join(room, siswa, MemberRole.SISWA);
            students.add(siswa);
        }

        // expiresAt sudah lewat: publishedQuiz dipakai langsung lewat repository (bukan lewat
        // AssignmentPublishingService) sebab keadaan ini justru yang ditolak gerbang penerbitan
        // (BR-M05: expiresAt wajib di masa depan saat publish), padahal tes ini butuh keadaan itu
        // sebagai TITIK AWAL.
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                TestData.TIMER_TAK_MENGIKAT, OffsetDateTime.now().minusMinutes(5), 3);

        // 3 dari 5 Siswa "mengerjakan": sesinya dibuat langsung lewat repository sebab
        // ExamSessionService.start menolak Assignment yang expiresAt-nya sudah lewat
        // (GoneException) — padahal skenario ini butuh sesi IN_PROGRESS yang terbengkalai.
        for (int i = 0; i < 3; i++) {
            sessions.save(new ExamSessionEntity(
                    client.getId(), assignment.getId(), students.get(i).getId(), 1,
                    OffsetDateTime.now().minusMinutes(20), assignment.getExpiresAt()));
        }

        List<ReportService.Row> rows = reportService.recap(assignment.getId(), data.principal(guru));

        // (a) Rekap berisi seluruh anggota Ruangan, bukan hanya yang punya sesi.
        assertThat(rows).hasSize(5);

        // (b) Dua Siswa yang tidak pernah Start tampil NOT_STARTED dengan skor 0.
        List<ReportService.Row> notStarted = rows.stream()
                .filter(r -> r.status().equals("NOT_STARTED"))
                .toList();
        assertThat(notStarted).hasSize(2);
        assertTrue(notStarted.stream().allMatch(r -> r.officialScore().compareTo(BigDecimal.ZERO) == 0));

        // (c) Membuka rekap tidak boleh melahirkan baris sesi baru untuk Siswa yang belum Start
        // (BR-S01 tetap utuh) — jumlah sesi Assignment ini tetap 3, bukan bertambah jadi 5.
        assertThat(sessions.findByAssignmentId(assignment.getId())).hasSize(3);

        // (d) Sesi terbengkalai (IN_PROGRESS, deadline sudah lewat) sudah difinalisasi, dan
        // Result-nya benar-benar ada (BR-L02, ADR-0002).
        List<ExamSessionEntity> allSessions = sessions.findByAssignmentId(assignment.getId());
        assertTrue(allSessions.stream().noneMatch(ExamSessionEntity::isInProgress));
        List<UUID> sessionIds = allSessions.stream().map(ExamSessionEntity::getId).toList();
        assertThat(results.findBySessionIdIn(sessionIds)).hasSize(3);
    }

    @Test
    @DisplayName("AC-C01: soal tak terjawab saat Timer habis dihitung salah, dan skor tetap dihitung dari seluruh soal")
    void unansweredQuestionsCountAgainstTotalWhenTimerExpires() {
        ClientEntity client = data.client("SD Skor");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 6A");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "IPA", "Tata Surya");
        List<QuestionEntity> content = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            content.add(data.mcq(client, topic, "Soal " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan IPA", content);
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                60, OffsetDateTime.now().plusDays(1), 1);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());

        // Siswa menjawab 6 dari 10 soal sebelum Timer habis.
        for (int i = 0; i < 6; i++) {
            SessionQuestionEntity sq = snapshot.get(i);
            UUID option = data.correctOptionOf(findQuestion(content, sq.getQuestionId()));
            answerService.save(session.getId(), sq.getId(), option, null, data.principal(siswa));
        }

        // Timer habis: deadline efektif didorong ke masa lalu langsung lewat entitasnya sendiri
        // (recomputeDeadline sudah publik untuk kebutuhan perpanjangan Guru, BR-T06) — jalur ini
        // dipilih karena ClientClock memakai jam sistem sungguhan, sehingga menunggu waktu asli
        // berlalu tidak praktis dalam tes integrasi.
        session.recomputeDeadline(OffsetDateTime.now().minusSeconds(1));
        sessions.save(session);
        finalizer.finalizeIfExpired(session.getId(), client.getId());

        ResultEntity result = results.findBySessionId(session.getId()).orElseThrow();

        // BR-C06: 4 soal tak terjawab dihitung salah dan masuk unansweredCount, dan totalnya
        // tetap 10 — bukan 6 (jumlah yang sempat dijawab).
        assertThat(result.getUnansweredCount()).isEqualTo(4);
        assertThat(result.getTotalQuestions()).isEqualTo(10);
    }

    @Test
    @DisplayName("AC-C04: benar, salah, dan kosong dihitung sesuai BR-C01 sampai BR-C03 tanpa nilai minus")
    void scoresBenarSalahKosongWithoutNegativePoints() {
        ClientEntity client = data.client("SD Skor2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 6B");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "IPA", "Ekosistem");
        List<QuestionEntity> content = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            content.add(data.mcq(client, topic, "Soal " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan IPA 2", content);
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                60, OffsetDateTime.now().plusDays(1), 1);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());

        // 6 benar, 2 salah, 2 dibiarkan kosong (soal indeks 8 dan 9 tidak disentuh sama sekali).
        for (int i = 0; i < 6; i++) {
            SessionQuestionEntity sq = snapshot.get(i);
            UUID option = data.correctOptionOf(findQuestion(content, sq.getQuestionId()));
            answerService.save(session.getId(), sq.getId(), option, null, data.principal(siswa));
        }
        for (int i = 6; i < 8; i++) {
            SessionQuestionEntity sq = snapshot.get(i);
            UUID option = data.wrongOptionOf(findQuestion(content, sq.getQuestionId()));
            answerService.save(session.getId(), sq.getId(), option, null, data.principal(siswa));
        }

        ResultEntity result = finalizer.submit(session.getId(), client.getId());

        // BigDecimal dibandingkan dengan compareTo, bukan equals — skala penyimpanan (scale 4)
        // tidak boleh membuat 0.6000 dianggap berbeda dari 0.6 (TC pembulatan tidak relevan di
        // sini, tapi kesalahan skala adalah sumber kegagalan tes yang paling umum untuk BigDecimal).
        assertThat(result.getScore().compareTo(new BigDecimal("0.6"))).isEqualTo(0);
        assertThat(result.getCorrectCount()).isEqualTo(6);
        assertThat(result.getIncorrectCount()).isEqualTo(2);
        assertThat(result.getUnansweredCount()).isEqualTo(2);
        // BR-C02: salah dan kosong sama-sama bernilai 0 — tidak ada skor di bawah nol yang bisa
        // dibuktikan lewat totalQuestions * skor minimum yang mungkin terjadi (0).
        assertThat(result.getScore().compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
    }

    private QuestionEntity findQuestion(List<QuestionEntity> content, UUID questionId) {
        return content.stream().filter(q -> q.getId().equals(questionId)).findFirst().orElseThrow();
    }
}
