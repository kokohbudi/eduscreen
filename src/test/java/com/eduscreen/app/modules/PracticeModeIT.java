package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.ResultKind;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService.PublishRequest;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.UnprocessableException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T077 — Mode Practice: gerbang penerbitan (BR-M04, BR-Q03), penguncian jawaban seketika
 * (BR-S07), Result yang selalu FINAL (BR-R01), dan pemisahannya dari rekap nilai (BR-L04).
 */
class PracticeModeIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    AssignmentPublishingService publishing;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    AnswerService answerService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    ReportService reportService;
    @Autowired
    ResultRepository results;
    @Autowired
    SessionQuestionRepository sessionQuestions;

    @Test
    @DisplayName("AC-Q05: Practice ditolak bila ada soal MCQ tanpa pembahasan, dan pesannya menyebut soal mana; Quiz tetap diterbitkan")
    void practicePublishRejectsQuestionsWithoutExplanationButQuizAccepts() {
        ClientEntity client = data.client("SD Practice1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4A");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Bahasa", "Membaca");
        // Soal ke-1 dan ke-3 sengaja tanpa pembahasan; soal ke-2 dan ke-4 punya pembahasan —
        // gerbang penerbitan harus menyebut PERSIS soal 1 dan 3, bukan sekadar "ada yang kurang".
        QuestionEntity q1 = data.mcq(client, topic, "Soal tanpa pembahasan A", 4);
        QuestionEntity q2 = data.mcqWithExplanation(client, topic, "Soal berpembahasan A");
        QuestionEntity q3 = data.mcq(client, topic, "Soal tanpa pembahasan B", 4);
        QuestionEntity q4 = data.mcqWithExplanation(client, topic, "Soal berpembahasan B");
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Membaca", List.of(q1, q2, q3, q4));

        UserPrincipal guruPrincipal = data.principal(guru);
        AssignmentEntity practiceDraft = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Latihan Membaca", AssignmentMode.PRACTICE,
                null, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);

        assertThatThrownBy(() -> publishing.publish(practiceDraft.getId(), guruPrincipal))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> {
                    // BR-M04: Guru yang membina 40 soal butuh tahu soal keberapa, bukan sekadar
                    // "ada soal tanpa pembahasan" — pesan harus menyebut posisinya di Exercise.
                    assertThat(ex.getMessage()).contains("soal 1");
                    assertThat(ex.getMessage()).contains("soal 3");
                });

        // Exercise yang sama diterbitkan sebagai QUIZ tidak tunduk pada BR-Q03 (pembahasan hanya
        // wajib untuk Practice) sehingga harus lolos.
        AssignmentEntity quizDraft = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Ulangan Membaca", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        AssignmentEntity published = publishing.publish(quizDraft.getId(), guruPrincipal);
        assertThat(published.isPublished()).isTrue();
    }

    @Test
    @DisplayName("AC-M01: Practice ditolak bila Exercise memuat soal ESSAY, dan pesannya menyebut soal ESSAY itu")
    void practicePublishRejectsEssayQuestions() {
        ClientEntity client = data.client("SD Practice2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4B");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Bahasa", "Menulis");
        QuestionEntity q1 = data.mcqWithExplanation(client, topic, "Soal MCQ A");
        QuestionEntity q2 = data.mcqWithExplanation(client, topic, "Soal MCQ B");
        QuestionEntity essay = data.essay(client, topic, "Soal essay C");
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Menulis", List.of(q1, q2, essay));

        UserPrincipal guruPrincipal = data.principal(guru);
        AssignmentEntity draft = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Latihan Menulis", AssignmentMode.PRACTICE,
                null, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);

        // Essay ada di posisi ke-3; Practice tidak boleh memuat essay sama sekali (BR-M04,
        // konsisten dengan BR-C09 yang mengandalkan tidak-adanya essay agar Result selalu FINAL).
        assertThatThrownBy(() -> publishing.publish(draft.getId(), guruPrincipal))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("soal 3"));
    }

    @Test
    @DisplayName("AC-S04: jawaban Practice terkunci seketika dikirim, dan kiriman berikutnya dengan jawaban berbeda ditolak")
    void practiceAnswerLocksImmediatelyAfterSubmission() {
        ClientEntity client = data.client("SD Practice3");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 4C");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Bahasa", "Kosakata");
        List<QuestionEntity> content = List.of(
                data.mcqWithExplanation(client, topic, "Soal 1"),
                data.mcqWithExplanation(client, topic, "Soal 2"),
                data.mcqWithExplanation(client, topic, "Soal 3"),
                data.mcqWithExplanation(client, topic, "Soal 4"));
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Kosakata", content);
        AssignmentEntity assignment = data.publishedPractice(client, exercise, room, guru,
                OffsetDateTime.now().plusDays(1));

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        SessionQuestionEntity thirdQuestion = snapshot.get(2); // "soal ke-3", posisi indeks 2.
        QuestionEntity question = content.stream()
                .filter(q -> q.getId().equals(thirdQuestion.getQuestionId())).findFirst().orElseThrow();

        UUID correctOption = data.correctOptionOf(question);
        AnswerService.Saved saved = answerService.save(
                session.getId(), thirdQuestion.getId(), correctOption, null, data.principal(siswa));

        assertThat(saved.locked()).isTrue();
        SessionQuestionEntity refreshed = sessionQuestions.findById(thirdQuestion.getId()).orElseThrow();
        assertThat(refreshed.getLockedAt()).isNotNull();

        UUID differentOption = data.wrongOptionOf(question);
        assertThrows(IllegalStateException.class, () -> answerService.save(
                session.getId(), thirdQuestion.getId(), differentOption, null, data.principal(siswa)));
    }

    @Test
    @DisplayName("AC-C05: Result Practice ber-kind PRACTICE dan langsung FINAL, tidak pernah PENDING_REVIEW")
    void practiceResultIsAlwaysFinal() {
        ClientEntity client = data.client("SD Practice4");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 4D");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Bahasa", "Tata Bahasa");
        List<QuestionEntity> content = List.of(
                data.mcqWithExplanation(client, topic, "Soal 1"),
                data.mcqWithExplanation(client, topic, "Soal 2"));
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Tata Bahasa", content);
        AssignmentEntity assignment = data.publishedPractice(client, exercise, room, guru,
                OffsetDateTime.now().plusDays(1));

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        ResultEntity result = finalizer.submit(session.getId(), client.getId());

        // BR-C09: Practice tidak pernah memuat essay, sehingga tidak pernah ada essay yang belum
        // dinilai — jalur PENDING_REVIEW mustahil tercapai, dan itu harus terbukti di sini, bukan
        // sekadar diasumsikan dari larangan essay saat publish.
        assertThat(result.getKind()).isEqualTo(ResultKind.PRACTICE);
        assertThat(result.getStatus()).isEqualTo(ResultStatus.FINAL);
    }

    @Test
    @DisplayName("AC-C03: rekap nilai Quiz tidak memuat Result Practice; aktivitas Practice tampil di laporan terpisah")
    void practiceResultsAreSeparateFromGradeReport() {
        ClientEntity client = data.client("SD Practice5");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 4E");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Bahasa", "Campuran");
        List<QuestionEntity> practiceContent = List.of(data.mcqWithExplanation(client, topic, "Soal Latihan"));
        List<QuestionEntity> quizContent = List.of(data.mcq(client, topic, "Soal Ulangan", 4));

        ExerciseEntity practiceExercise1 = data.exercise(client, guru, "Latihan 1", practiceContent);
        ExerciseEntity practiceExercise2 = data.exercise(client, guru, "Latihan 2", practiceContent);
        ExerciseEntity quizExercise = data.exercise(client, guru, "Ulangan", quizContent);

        AssignmentEntity practice1 = data.publishedPractice(client, practiceExercise1, room, guru,
                OffsetDateTime.now().plusDays(1));
        AssignmentEntity practice2 = data.publishedPractice(client, practiceExercise2, room, guru,
                OffsetDateTime.now().plusDays(1));
        AssignmentEntity quiz = data.publishedQuiz(client, quizExercise, room, guru,
                30, OffsetDateTime.now().plusDays(1), 1);

        UserPrincipal siswaPrincipal = data.principal(siswa);
        finalizer.submit(examSessionService.start(practice1.getId(), siswaPrincipal).getId(), client.getId());
        finalizer.submit(examSessionService.start(practice2.getId(), siswaPrincipal).getId(), client.getId());
        finalizer.submit(examSessionService.start(quiz.getId(), siswaPrincipal).getId(), client.getId());

        UserPrincipal guruPrincipal = data.principal(guru);
        List<ReportService.Row> quizRows = reportService.recap(quiz.getId(), guruPrincipal);
        assertThat(quizRows).hasSize(1);
        // Satu-satunya Siswa di Ruangan ini sudah mengerjakan Quiz-nya; statusnya tidak boleh
        // NOT_STARTED, dan skornya berasal dari Result kind GRADED — bukan salah satu Result
        // Practice yang kebetulan juga miliknya (BR-L04).
        assertThat(quizRows.get(0).status()).isNotEqualTo("NOT_STARTED");

        List<ResultEntity> practiceActivity = reportService.practiceActivity(room.getId(), guruPrincipal);
        assertThat(practiceActivity).hasSize(2);
        assertThat(practiceActivity).allMatch(r -> r.getKind() == ResultKind.PRACTICE);
    }
}
