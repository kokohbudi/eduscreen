package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T078 — Idempotensi auto-save (TC-20). Antrean coba-ulang di klien (BR-S08) menjamin server
 * akan menerima kiriman ganda; server yang menolak kiriman identik akan mengubah mekanisme
 * pemulihan koneksi menjadi sumber kerusakan bagi Siswa yang jawabannya sebenarnya sudah aman.
 */
class PracticeIdempotencyIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    AnswerService answerService;
    @Autowired
    SessionAnswerRepository sessionAnswers;

    @Test
    @DisplayName("TC-20: kiriman ulang jawaban MCQ identik pada soal Practice yang terkunci sukses sebagai no-op, jawaban berbeda ditolak")
    void identicalResubmissionOnLockedPracticeQuestionIsNoop() {
        ClientEntity client = data.client("SD Idem1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 5A");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Matematika", "Bilangan");
        QuestionEntity question = data.mcqWithExplanation(client, topic, "Soal 1");
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Bilangan", List.of(question));
        AssignmentEntity assignment = data.publishedPractice(client, exercise, room, guru,
                OffsetDateTime.now().plusDays(1));

        UserPrincipal siswaPrincipal = data.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        SessionQuestionEntity sessionQuestion = examSessionService.snapshotOf(session.getId()).get(0);

        UUID correctOption = data.correctOptionOf(question);
        answerService.save(session.getId(), sessionQuestion.getId(), correctOption, null, siswaPrincipal);

        // Kiriman ulang BERISI JAWABAN IDENTIK pada soal yang sudah terkunci: klien yang koneksinya
        // sempat putus dan mengirim ulang dari antrean tidak boleh melihat galat untuk jawaban
        // yang sebenarnya sudah tersimpan.
        AnswerService.Saved resubmission = answerService.save(
                session.getId(), sessionQuestion.getId(), correctOption, null, siswaPrincipal);
        assertThat(resubmission.noop()).isTrue();
        assertThat(resubmission.locked()).isTrue();

        // Tidak boleh lahir baris session_answer kedua — upsert-nya berkunci session_question_id,
        // bukan menambah baris baru setiap kali disimpan (TC-20).
        List<SessionAnswerEntity> stored = sessionAnswers.findBySessionQuestionIdIn(List.of(sessionQuestion.getId()));
        assertThat(stored).hasSize(1);

        // Kiriman berisi jawaban BERBEDA pada soal yang sudah terkunci ditolak (BR-S07) — ini
        // yang membedakan "kiriman ulang" dari "Siswa berubah pikiran setelah dikunci".
        UUID differentOption = data.wrongOptionOf(question);
        assertThrows(IllegalStateException.class, () -> answerService.save(
                session.getId(), sessionQuestion.getId(), differentOption, null, siswaPrincipal));
    }

    @Test
    @DisplayName("TC-20: kiriman ulang esai identik di Quiz sukses sebagai no-op, dan esai boleh DIUBAH sampai selesai — beda dari Practice")
    void essayAnswerBehavesDifferentlyOnUnlockedQuizQuestion() {
        ClientEntity client = data.client("SD Idem2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 5B");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Bahasa", "Esai");
        QuestionEntity essayQuestion = data.essay(client, topic, "Jelaskan siklus air");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Esai", List.of(essayQuestion));
        // QUIZ, bukan PRACTICE (Practice tidak boleh memuat essay sama sekali, BR-M04) — soal
        // esainya sengaja tetap TERBUKA sepanjang sesi, kontras dengan Practice yang mengunci
        // seketika jawaban pertama dikirim.
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                60, OffsetDateTime.now().plusDays(1), 1);

        UserPrincipal siswaPrincipal = data.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        SessionQuestionEntity sessionQuestion = examSessionService.snapshotOf(session.getId()).get(0);

        answerService.save(session.getId(), sessionQuestion.getId(), null, "Jawaban pertama", siswaPrincipal);

        // Kiriman ulang berisi teks IDENTIK: no-op sukses, sama seperti Practice — aturan
        // idempotensi kiriman ganda berlaku untuk kedua mode.
        AnswerService.Saved resubmission = answerService.save(
                session.getId(), sessionQuestion.getId(), null, "Jawaban pertama", siswaPrincipal);
        assertThat(resubmission.noop()).isTrue();
        assertThat(resubmission.locked()).isFalse(); // Quiz tidak pernah mengunci soal (BR-S07 khusus Practice).

        List<SessionAnswerEntity> storedAfterResubmit =
                sessionAnswers.findBySessionQuestionIdIn(List.of(sessionQuestion.getId()));
        assertThat(storedAfterResubmit).hasSize(1);

        // Kiriman berisi teks BERBEDA: di Practice ini ditolak karena soalnya terkunci; di Quiz
        // soal tidak pernah terkunci sampai sesi selesai, sehingga perubahan justru DITERIMA —
        // Siswa boleh menyunting jawaban esainya berkali-kali sebelum menekan Selesai.
        AnswerService.Saved changed = answerService.save(
                session.getId(), sessionQuestion.getId(), null, "Jawaban revisi", siswaPrincipal);
        assertThat(changed.noop()).isFalse();

        SessionAnswerEntity finalAnswer = sessionAnswers.findBySessionQuestionId(sessionQuestion.getId()).orElseThrow();
        assertThat(finalAnswer.getEssayText()).isEqualTo("Jawaban revisi");
    }
}
