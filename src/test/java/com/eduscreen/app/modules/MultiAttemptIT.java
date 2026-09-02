package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.security.UserPrincipal;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T105 — Pengulangan (Attempt): tiap Attempt adalah Session baru dengan Snapshot baru (BR-S04),
 * batasnya berbeda antara Quiz dan Practice (BR-S05), dan skor resmi adalah yang tertinggi
 * (BR-L03, BR-C07).
 */
class MultiAttemptIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    AnswerService answerService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    ReportService reportService;
    @Autowired
    AssignmentRepository assignments;

    @Test
    @DisplayName("AC-S05: tiap Attempt Quiz melahirkan Session dan Snapshot acak baru, dan Attempt melebihi maxAttempts ditolak; Practice tidak dibatasi")
    void quizAttemptsAreCappedWithFreshShuffledSnapshotsWhilePracticeIsUnlimited() {
        ClientEntity client = data.client("SD Attempt1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 6A");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Matematika", "Aljabar");
        List<QuestionEntity> content = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            content.add(data.mcq(client, topic, "Soal " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Aljabar", content);
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                30, OffsetDateTime.now().plusDays(1), 3);
        // TestData.publishedQuiz tidak menyalakan pengacakan; dinyalakan di sini lewat setter lalu
        // disimpan ulang lewat repository, sebagaimana diarahkan — bukan lewat gerbang penerbitan,
        // karena Assignment ini memang sudah PUBLISHED sejak awal.
        assignment.setShuffleQuestions(true);
        assignments.save(assignment);

        UserPrincipal siswaPrincipal = data.principal(siswa);

        ExamSessionEntity attempt1 = examSessionService.start(assignment.getId(), siswaPrincipal);
        assertThat(attempt1.getAttemptNumber()).isEqualTo(1);
        List<UUID> order1 = questionOrderOf(attempt1);
        finalizer.submit(attempt1.getId(), client.getId());

        ExamSessionEntity attempt2 = examSessionService.start(assignment.getId(), siswaPrincipal);
        assertThat(attempt2.getAttemptNumber()).isEqualTo(2);
        List<UUID> order2 = questionOrderOf(attempt2);
        // BR-S04: Snapshot BARU per Attempt. Dengan shuffleQuestions = true dan 8 soal, peluang
        // urutan acak kedua identik dengan yang pertama praktis nol (1 dari 8!) — jauh lebih kuat
        // sebagai bukti daripada sekadar "baris Snapshot-nya berbeda", yang trivial karena setiap
        // Attempt memang selalu mendapat baris session_question baru.
        assertThat(order2).isNotEqualTo(order1);
        finalizer.submit(attempt2.getId(), client.getId());

        ExamSessionEntity attempt3 = examSessionService.start(assignment.getId(), siswaPrincipal);
        assertThat(attempt3.getAttemptNumber()).isEqualTo(3);
        finalizer.submit(attempt3.getId(), client.getId());

        // maxAttempts = 3 sudah tercapai; Attempt ke-4 pada Quiz harus ditolak (BR-S05).
        assertThrows(IllegalStateException.class,
                () -> examSessionService.start(assignment.getId(), siswaPrincipal));

        // Pada Practice, sebaliknya, permintaan Attempt ke berapa pun tetap diterima (BR-S05) —
        // latihan memang dirancang untuk diulang tanpa batas.
        ExerciseEntity practiceExercise = data.exercise(client, guru, "Latihan Aljabar",
                List.of(data.mcqWithExplanation(client, topic, "Soal Latihan")));
        AssignmentEntity practice = data.publishedPractice(client, practiceExercise, room, guru,
                OffsetDateTime.now().plusDays(1));
        for (int attemptNumber = 1; attemptNumber <= 3; attemptNumber++) {
            ExamSessionEntity session = examSessionService.start(practice.getId(), siswaPrincipal);
            finalizer.submit(session.getId(), client.getId());
        }
        ExamSessionEntity practiceAttempt4 = examSessionService.start(practice.getId(), siswaPrincipal);
        assertThat(practiceAttempt4.getAttemptNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-L02: skor resmi adalah yang TERTINGGI di antara seluruh Attempt, dan ketiganya tetap bisa dibuka Guru")
    void officialScoreIsTheHighestAcrossAttempts() {
        ClientEntity client = data.client("SD Attempt2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 6B");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Matematika", "Geometri");
        List<QuestionEntity> content = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            content.add(data.mcq(client, topic, "Soal " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Geometri", content);
        // shuffleQuestions tetap false (bawaan publishedQuiz): urutan snapshot mengikuti urutan
        // Exercise, sehingga "N soal pertama benar" bisa dipakai untuk mengatur jumlah benar
        // secara presisi di tiap Attempt tanpa perlu menelusuri urutan acak.
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                30, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal siswaPrincipal = data.principal(siswa);

        // Berurutan rendah (2/10 = 0,2) — tinggi (9/10 = 0,9) — menengah (5/10 = 0,5), persis
        // urutan yang disebut di AC-L02, supaya "skor resmi bukan skor Attempt terakhir" benar-benar
        // teruji (Attempt terakhir di sini skornya menengah, bukan tertinggi ataupun terendah).
        BigDecimal score1 = scoreFromAttempt(assignment, siswaPrincipal, content, 2);
        BigDecimal score2 = scoreFromAttempt(assignment, siswaPrincipal, content, 9);
        BigDecimal score3 = scoreFromAttempt(assignment, siswaPrincipal, content, 5);

        assertThat(score1.compareTo(score3)).isLessThan(0);
        assertThat(score3.compareTo(score2)).isLessThan(0);

        UserPrincipal guruPrincipal = data.principal(guru);
        List<ReportService.Row> rows = reportService.recap(assignment.getId(), guruPrincipal);
        ReportService.Row row = rows.stream()
                .filter(r -> r.student().getId().equals(siswa.getId())).findFirst().orElseThrow();
        assertThat(row.officialScore().compareTo(score2)).isEqualTo(0);

        List<ExamSessionEntity> attempts = reportService.attemptsOf(assignment.getId(), siswa.getId(), guruPrincipal);
        assertThat(attempts).hasSize(3);
    }

    private BigDecimal scoreFromAttempt(AssignmentEntity assignment, UserPrincipal siswaPrincipal,
                                        List<QuestionEntity> content, int correctCount) {
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        for (int i = 0; i < snapshot.size(); i++) {
            SessionQuestionEntity sq = snapshot.get(i);
            QuestionEntity question = content.stream()
                    .filter(q -> q.getId().equals(sq.getQuestionId())).findFirst().orElseThrow();
            UUID option = i < correctCount ? data.correctOptionOf(question) : data.wrongOptionOf(question);
            answerService.save(session.getId(), sq.getId(), option, null, siswaPrincipal);
        }
        ResultEntity result = finalizer.submit(session.getId(), assignment.getClientId());
        return result.getScore();
    }

    private List<UUID> questionOrderOf(ExamSessionEntity session) {
        return examSessionService.snapshotOf(session.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId)
                .toList();
    }
}
