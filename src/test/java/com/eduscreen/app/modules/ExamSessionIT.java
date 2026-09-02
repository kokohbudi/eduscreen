package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T041 — kelahiran dan pembacaan sesi pengerjaan (AC-S01, AC-S02, AC-S03), plus perakitan
 * Exercise lintas Subject (AC-E02).
 *
 * <p>Sengaja tidak memakai {@code @Transactional} pada kelas ini: sebagian jalur yang diuji di
 * berkas saudaranya (finalisasi) memakai {@code REQUIRES_NEW}, dan tes yang membungkus semuanya
 * dalam satu transaksi luar akan menyembunyikan bug commit/visibility antar transaksi yang justru
 * ingin dibuktikan tidak ada.
 */
class ExamSessionIT extends PostgresTestBase {

    @Autowired
    TestData data;

    @Autowired
    ExamSessionService examSessions;

    @Autowired
    AnswerService answers;

    @Autowired
    ExerciseService exercises;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    SessionAnswerRepository sessionAnswerRepository;

    @Test
    @DisplayName("AC-S01: Assignment PUBLISHED tanpa satu pun Siswa menekan Mulai tidak meninggalkan baris Session apa pun")
    void publishedAssignmentWithoutAnyStartLeavesNoSessionRow() {
        ClientEntity client = data.client("SD Diam");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru Diam");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa Diam");
        RuanganEntity room = data.ruangan(client, "Kelas Diam");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Pecahan");
        QuestionEntity question = data.mcq(client, topic, "Soal diam", 4);
        ExerciseEntity exercise = data.exercise(client, guru, "Ujian Diam", List.of(question));
        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, 60, OffsetDateTime.now().plusDays(1), 3);

        // Tidak ada pembuatan massal saat penerbitan (BR-S01, ADR-0002): Siswa yang tidak pernah
        // menekan Mulai tidak boleh meninggalkan satu baris pun untuk dibersihkan atau salah
        // terhitung sebagai "sedang mengerjakan" di rekap Guru.
        assertThat(examSessionRepository.findByAssignmentId(assignment.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-S02: dua Siswa pada Assignment shuffleQuestions=true mendapat urutan berbeda yang masing-masing stabil saat dibaca ulang")
    void shuffledAssignmentGivesDifferentButStablePerStudentOrder() {
        ClientEntity client = data.client("SD Acak");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru Acak");
        AppUserEntity siswaA = data.user(client, UserRole.SISWA, "Siswa A");
        AppUserEntity siswaB = data.user(client, UserRole.SISWA, "Siswa B");
        RuanganEntity room = data.ruangan(client, "Kelas Acak");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswaA, MemberRole.SISWA);
        data.join(room, siswaB, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Aljabar");

        // >= 8 soal: dengan 8! (40.320) kemungkinan urutan, peluang dua pengacakan independen
        // kebetulan menghasilkan urutan identik nyaris nol, sehingga tes ini tidak flaky tanpa
        // perlu menambatkan RNG-nya.
        List<QuestionEntity> questions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            questions.add(data.mcq(client, topic, "Soal acak " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ujian Acak", questions);

        // TestData.publishedQuiz tidak menyalakan shuffle; Assignment dirakit langsung lewat
        // konstruktornya dengan shuffleQuestions = true, lalu diterbitkan manual (bukan lewat
        // AssignmentPublishingService — tes ini tidak menguji gerbang penerbitan).
        AssignmentEntity assignment = new AssignmentEntity(
                client.getId(), exercise.getId(), room.getId(), guru.getId(),
                AssignmentMode.QUIZ, "Ulangan Acak", 60, OffsetDateTime.now().plusDays(1), 3,
                true, false, RevealAnswersAt.AFTER_SUBMIT);
        assignment.publish(OffsetDateTime.now());
        assignment = assignmentRepository.save(assignment);

        ExamSessionEntity sessionA = examSessions.start(assignment.getId(), data.principal(siswaA));
        ExamSessionEntity sessionB = examSessions.start(assignment.getId(), data.principal(siswaB));

        List<UUID> orderA = examSessions.snapshotOf(sessionA.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId).toList();
        List<UUID> orderB = examSessions.snapshotOf(sessionB.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId).toList();

        assertThat(orderA).isNotEqualTo(orderB);

        // Pengacakan terjadi sekali di freezeSnapshot(); membaca ulang snapshot tidak boleh
        // mengacak lagi (BR-S03) — urutan yang tersimpan adalah kebenaran tunggal sepanjang sesi.
        List<UUID> orderAReread = examSessions.snapshotOf(sessionA.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId).toList();
        assertThat(orderAReread).isEqualTo(orderA);
    }

    @Test
    @DisplayName("AC-S03: memanggil start() lagi pada sesi yang masih berjalan mengembalikan sesi yang sama dengan jawaban dan urutan soal utuh")
    void reenteringInProgressSessionReturnsSameSessionIntact() {
        ClientEntity client = data.client("SD Ulang");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru Ulang");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa Ulang");
        RuanganEntity room = data.ruangan(client, "Kelas Ulang");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "IPA Kelas 6", "Tata Surya");
        List<QuestionEntity> questions = List.of(
                data.mcq(client, topic, "Soal 1", 4),
                data.mcq(client, topic, "Soal 2", 4),
                data.mcq(client, topic, "Soal 3", 4));
        ExerciseEntity exercise = data.exercise(client, guru, "Ujian Ulang", questions);
        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal principal = data.principal(siswa);
        ExamSessionEntity first = examSessions.start(assignment.getId(), principal);
        List<UUID> originalOrder = examSessions.snapshotOf(first.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId).toList();

        SessionQuestionEntity firstQuestion = examSessions.snapshotOf(first.getId()).get(0);
        UUID correctOption = data.correctOptionOf(questions.get(0));
        answers.save(first.getId(), firstQuestion.getId(), correctOption, null, principal);

        ExamSessionEntity second = examSessions.start(assignment.getId(), principal);

        // Sesi lahir hanya sekali saat Mulai pertama kali ditekan (BR-S06); membuka lagi
        // Assignment yang sama tidak boleh melahirkan sesi kedua dengan jawaban kosong.
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(examSessionRepository.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(
                assignment.getId(), siswa.getId())).hasSize(1);

        List<UUID> orderAfterReentry = examSessions.snapshotOf(second.getId()).stream()
                .map(SessionQuestionEntity::getQuestionId).toList();
        assertThat(orderAfterReentry).isEqualTo(originalOrder);

        SessionAnswerEntity savedAnswer = sessionAnswerRepository
                .findBySessionQuestionId(firstQuestion.getId()).orElseThrow();
        assertThat(savedAnswer.getSelectedOptionId()).isEqualTo(correctOption);
    }

    @Test
    @DisplayName("AC-E02: Exercise memuat soal dari dua Topic di bawah dua Subject berbeda tanpa penolakan apa pun")
    void exerciseAcceptsQuestionsAcrossSubjectsAndTopics() {
        ClientEntity client = data.client("SD Lintas");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru Lintas");
        TopicEntity aljabar = data.topic(client, "Matematika Kelas 4", "Aljabar");
        TopicEntity gerakLurus = data.topic(client, "Fisika Kelas 9", "Gerak Lurus");
        QuestionEntity soalAljabar = data.mcq(client, aljabar, "Soal Aljabar", 4);
        QuestionEntity soalGerak = data.mcq(client, gerakLurus, "Soal Gerak Lurus", 4);

        ExerciseEntity exercise = exercises.create(client.getId(), "Ujian Lintas Subject", guru.getId());
        // Sengaja TIDAK ada validasi Subject/Topic apa pun di addQuestion (lihat komentarnya):
        // Exercise boleh memuat soal lintas Subject dan Topic mana pun di dalam Client yang sama,
        // Guru berpindah bebas antar Topic dalam satu sesi perakitan (BR-E01, FR-024).
        exercises.addQuestion(exercise.getId(), soalAljabar.getId(), client.getId());
        exercises.addQuestion(exercise.getId(), soalGerak.getId(), client.getId());

        List<UUID> questionIds = exercises.itemsOf(exercise.getId()).stream()
                .map(ExerciseItemEntity::getQuestionId).toList();
        assertThat(questionIds).containsExactlyInAnyOrder(soalAljabar.getId(), soalGerak.getId());
    }
}
