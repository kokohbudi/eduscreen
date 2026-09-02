package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.TerminalReason;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
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
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.GoneException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * T042 — waktu server, batas efektif, dan penolakan kiriman terlambat (AC-T01, AC-T03, AC-T04,
 * AC-T06).
 *
 * <p><b>Pilihan kontrol waktu</b>: kelas ini memakai {@code @MockitoBean ClientClock} (opsi a) —
 * bukan Assignment ber-{@code expiresAt} nyaris lewat (opsi b) — karena AC-T01 dan AC-T04 harus
 * membuktikan perilaku SETELAH batas waktu terlewati sementara batas itu sendiri harus berada
 * belasan menit di depan saat sesi dibuka (supaya {@code start()} tidak menolaknya lebih dulu).
 * Menunggu belasan menit sungguhan per tes tidak praktis; menjembataninya dengan jam yang bisa
 * dimajukan eksplisit adalah jalan yang paling sedikit sihirnya untuk skenario itu. AC-T06 murni
 * aritmetika zona waktu dan tidak menyentuh jam sama sekali, jadi jam tiruan di kelas ini tidak
 * mempengaruhinya.
 *
 * <p>Jam tiruan dibekukan di {@link #freezeClock()} lewat sebuah {@link AtomicReference} yang
 * dibaca ulang setiap kali {@code clock.now()} dipanggil — bukan {@code thenReturn} berurutan —
 * supaya tidak bergantung pada berapa kali persisnya kode produksi memanggil jam dalam satu
 * transaksi.
 */
class SessionTimingIT extends PostgresTestBase {

    @Autowired
    TestData data;

    @Autowired
    ExamSessionService examSessions;

    @Autowired
    AnswerService answers;

    @Autowired
    SessionFinalizer finalizer;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ClientRepository clients;

    @MockitoBean
    ClientClock clock;

    private final AtomicReference<OffsetDateTime> now = new AtomicReference<>();

    @BeforeEach
    void freezeClock() {
        // Dipotong ke mikrodetik: timestamptz PostgreSQL hanya menyimpan sampai mikrodetik,
        // dan Assignment yang dibaca ulang di dalam transaksi baru (mis. saat start()) memuat
        // presisi yang sudah dipotong itu — menyamakan presisi sejak awal menghindari
        // perbandingan yang gagal karena nanodetik yang tidak pernah tersimpan.
        now.set(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        when(clock.now()).thenAnswer(invocation -> now.get());
    }

    private record Fixture(ClientEntity client, AppUserEntity guru, AppUserEntity siswa,
                            RuanganEntity room, ExerciseEntity exercise, List<QuestionEntity> questions) {
    }

    private Fixture fixture(String label, int questionCount) {
        ClientEntity client = data.client("SD " + label);
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru " + label);
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa " + label);
        RuanganEntity room = data.ruangan(client, "Kelas " + label);
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "Matematika", "Waktu");
        List<QuestionEntity> questions = new java.util.ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            questions.add(data.mcq(client, topic, "Soal " + label + " " + i, 4));
        }
        ExerciseEntity exercise = data.exercise(client, guru, "Ujian " + label, questions);
        return new Fixture(client, guru, siswa, room, exercise, questions);
    }

    @Test
    @DisplayName("AC-T01: Global Expiration lebih dekat daripada Timer memangkas effectiveDeadline dan berakhir EXPIRATION_REACHED")
    void expirationClosestPrunesTimerAndEndsWithExpirationReached() {
        Fixture fx = fixture("ExpirasiDekat", 1);
        OffsetDateTime expiresAt = now.get().plusMinutes(10).truncatedTo(ChronoUnit.MICROS);
        AssignmentEntity assignment = data.publishedQuiz(fx.client(), fx.exercise(), fx.room(), fx.guru(), 60, expiresAt, 3);

        ExamSessionEntity session = examSessions.start(assignment.getId(), data.principal(fx.siswa()));

        // Timer 60 menit berakhir jauh lebih lambat daripada expiresAt 10 menit lagi;
        // effectiveDeadline harus mengikuti batas yang lebih dekat, bukan mulai + 60 menit (BR-T04).
        assertThat(session.getEffectiveDeadline()).isEqualTo(expiresAt);
        long remaining = examSessions.remainingSeconds(session);
        assertThat(remaining).isBetween(595L, 600L);

        now.set(expiresAt.plusSeconds(1));
        Optional<ResultEntity> result = finalizer.finalizeIfExpired(session.getId(), fx.client().getId());
        assertThat(result).isPresent();

        ExamSessionEntity finalized = examSessionRepository
                .findByIdAndClientId(session.getId(), fx.client().getId()).orElseThrow();
        assertThat(finalized.getTerminalReason()).isEqualTo(TerminalReason.EXPIRATION_REACHED);
    }

    @Test
    @DisplayName("AC-T01: Timer lebih dekat daripada Global Expiration memangkas effectiveDeadline dan berakhir TIMER_TIMEOUT")
    void timerClosestPrunesExpirationAndEndsWithTimerTimeout() {
        Fixture fx = fixture("TimerDekat", 1);
        OffsetDateTime expiresAt = now.get().plusDays(30).truncatedTo(ChronoUnit.MICROS);
        AssignmentEntity assignment = data.publishedQuiz(fx.client(), fx.exercise(), fx.room(), fx.guru(), 5, expiresAt, 3);

        ExamSessionEntity session = examSessions.start(assignment.getId(), data.principal(fx.siswa()));

        OffsetDateTime expectedDeadline = session.getStartedAt().plusMinutes(5);
        assertThat(session.getEffectiveDeadline()).isEqualTo(expectedDeadline);

        now.set(expectedDeadline.plusSeconds(1));
        Optional<ResultEntity> result = finalizer.finalizeIfExpired(session.getId(), fx.client().getId());
        assertThat(result).isPresent();

        ExamSessionEntity finalized = examSessionRepository
                .findByIdAndClientId(session.getId(), fx.client().getId()).orElseThrow();
        assertThat(finalized.getTerminalReason()).isEqualTo(TerminalReason.TIMER_TIMEOUT);
    }

    @Test
    @DisplayName("AC-T03: sisa waktu pengerjaan murni fungsi jam server dan tidak berubah oleh kiriman jawaban klien")
    void remainingSecondsFollowsServerClockOnlyRegardlessOfClientSubmissions() {
        Fixture fx = fixture("Sisa", 2);
        OffsetDateTime expiresAt = now.get().plusMinutes(30).truncatedTo(ChronoUnit.MICROS);
        AssignmentEntity assignment = data.publishedQuiz(fx.client(), fx.exercise(), fx.room(), fx.guru(), TestData.TIMER_TAK_MENGIKAT, expiresAt, 3);

        UserPrincipal principal = data.principal(fx.siswa());
        ExamSessionEntity session = examSessions.start(assignment.getId(), principal);
        long remainingBeforeSaves = examSessions.remainingSeconds(session);

        List<SessionQuestionEntity> snapshot = examSessions.snapshotOf(session.getId());
        UUID correctOption0 = data.correctOptionOf(fx.questions().get(0));
        // AnswerService.save(...) tidak menerima parameter jam klien sama sekali — jalur ini
        // sengaja diuji dengan memanggilnya berkali-kali (termasuk kiriman identik yang menjadi
        // no-op) untuk membuktikan tidak ada satu pun cara bagi klien mengubah sisa waktu.
        answers.save(session.getId(), snapshot.get(0).getId(), correctOption0, null, principal);
        answers.save(session.getId(), snapshot.get(0).getId(), correctOption0, null, principal);
        UUID correctOption1 = data.correctOptionOf(fx.questions().get(1));
        answers.save(session.getId(), snapshot.get(1).getId(), correctOption1, null, principal);

        long remainingAfterSaves = examSessions.remainingSeconds(session);
        // Jam belum dimajukan sama sekali di antara kedua pembacaan: nilainya harus identik,
        // bukan sekadar "tidak berkurang" — kiriman jawaban tidak boleh menyentuhnya sedikit pun.
        assertThat(remainingAfterSaves).isEqualTo(remainingBeforeSaves);

        now.updateAndGet(t -> t.plusSeconds(120));
        long remainingAfterClockAdvance = examSessions.remainingSeconds(session);
        // Satu-satunya hal yang mengurangi sisa waktu adalah jam server yang benar-benar maju.
        assertThat(remainingAfterClockAdvance).isEqualTo(remainingAfterSaves - 120);
    }

    @Test
    @DisplayName("AC-T04: jawaban yang tiba setelah effectiveDeadline ditolak GoneException dan sesinya difinalisasi pada permintaan yang sama")
    void answerAfterDeadlineIsRejectedAndFinalizesSessionInSameRequest() {
        Fixture fx = fixture("Lewat", 1);
        OffsetDateTime expiresAt = now.get().plusMinutes(10).truncatedTo(ChronoUnit.MICROS);
        AssignmentEntity assignment = data.publishedQuiz(fx.client(), fx.exercise(), fx.room(), fx.guru(), TestData.TIMER_TAK_MENGIKAT, expiresAt, 3);

        UserPrincipal principal = data.principal(fx.siswa());
        ExamSessionEntity session = examSessions.start(assignment.getId(), principal);
        List<SessionQuestionEntity> snapshot = examSessions.snapshotOf(session.getId());
        UUID correctOption = data.correctOptionOf(fx.questions().get(0));

        now.set(expiresAt.plusSeconds(5));

        assertThatThrownBy(() -> answers.save(
                session.getId(), snapshot.get(0).getId(), correctOption, null, principal))
                .isInstanceOf(GoneException.class);

        ExamSessionEntity finalized = examSessionRepository
                .findByIdAndClientId(session.getId(), fx.client().getId()).orElseThrow();
        assertThat(finalized.isInProgress()).isFalse();
        assertThat(resultRepository.findBySessionIdIn(List.of(session.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("AC-T06: batas akhir 23:59 waktu Client Asia/Makassar tersimpan sebagai UTC 15:59")
    void makassarClientDeadlineStoredAsUtcFifteenFiftyNine() {
        // TestData.client(...) selalu memakai Asia/Jakarta; Client Makassar dirakit langsung
        // lewat repository memakai konstruktor ClientEntity(name, ZoneId) sesuai arahan tugas.
        ClientEntity makassar = clients.save(new ClientEntity("SD Makassar", ZoneId.of("Asia/Makassar")));
        AppUserEntity guru = data.user(makassar, UserRole.GURU, "Guru Makassar");
        RuanganEntity room = data.ruangan(makassar, "Kelas Makassar");
        TopicEntity topic = data.topic(makassar, "Matematika", "Waktu");
        QuestionEntity question = data.mcq(makassar, topic, "Soal Makassar", 4);
        ExerciseEntity exercise = data.exercise(makassar, guru, "Ujian Makassar", List.of(question));

        OffsetDateTime deadlineClientLocal = LocalDateTime.of(2026, 9, 6, 23, 59)
                .atZone(ZoneId.of("Asia/Makassar"))
                .toOffsetDateTime();
        AssignmentEntity assignment = data.publishedQuiz(makassar, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT, deadlineClientLocal, 1);

        AssignmentEntity reread = assignmentRepository
                .findByIdAndClientId(assignment.getId(), makassar.getId()).orElseThrow();
        OffsetDateTime storedUtc = reread.getExpiresAt().withOffsetSameInstant(ZoneOffset.UTC);

        // WITA adalah UTC+8; 23:59 waktu Client jatuh pada 15:59 UTC hari yang sama. Inilah yang
        // wajib tersimpan supaya Guru maupun Siswa di zona mana pun tetap melihat "23:59" yang
        // konsisten setelah dikonversi balik ke zona Client-nya (BR-T01, BR-T02).
        assertThat(storedUtc.getHour()).isEqualTo(15);
        assertThat(storedUtc.getMinute()).isEqualTo(59);
    }
}
