package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T043 — balapan finalisasi sesi (AC-T05).
 *
 * <p>Sesi diuji dirakit langsung lewat {@link ExamSessionRepository} dengan
 * {@code effectiveDeadline} yang sudah lewat, memotong jalur {@link
 * com.eduscreen.app.modules.assessment.service.ExamSessionService#start}: {@code start()} sendiri
 * menolak membuka Assignment yang {@code expiresAt}-nya sudah lewat, jadi tidak ada cara memakai
 * jalur normal untuk sampai pada keadaan "sesi berjalan tapi sudah lewat waktu" tanpa menunggu
 * sungguhan. Merakitnya langsung adalah opsi paling sedikit sihirnya di sini — jam sistem asli
 * tetap dipakai apa adanya, tidak ada jam tiruan sama sekali.
 *
 * <p>Sengaja TIDAK memakai {@code @Transactional} pada kelas ini: {@link SessionFinalizer} setiap
 * jalurnya berjalan di transaksinya sendiri ({@code REQUIRES_NEW}, TC-21), dan tes yang
 * membungkus semuanya dalam satu transaksi luar akan membuat kedua thread berbagi satu koneksi/
 * transaksi Testcontainers-nya — menyembunyikan persis balapan yang ingin dibuktikan aman di sini.
 */
class SessionFinalizationConcurrencyIT extends PostgresTestBase {

    @Autowired
    TestData data;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    SessionFinalizer finalizer;

    private ExamSessionEntity expiredSession(String label) {
        ClientEntity client = data.client("SD " + label);
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru " + label);
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa " + label);
        RuanganEntity room = data.ruangan(client, "Kelas " + label);
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        // Exercise sengaja kosong (tanpa Question): fokus tes ini adalah balapan finalisasi, bukan
        // penilaian, dan SessionFinalizer.tallyOf mentolerir snapshot kosong dengan total 0.
        ExerciseEntity exercise = data.exercise(client, guru, "Ujian " + label, List.of());

        OffsetDateTime pastDeadline = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30);
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT, pastDeadline, 3);

        ExamSessionEntity session = new ExamSessionEntity(
                client.getId(), assignment.getId(), siswa.getId(), 1,
                pastDeadline.minusMinutes(5), pastDeadline);
        return examSessionRepository.save(session);
    }

    /**
     * Menjalankan dua tugas pada dua thread yang benar-benar berangkat bersamaan: keduanya
     * menunggu di {@code ready}, lalu dilepas serentak lewat {@code go} — supaya tabrakan pada
     * kunci pesimistis sungguhan terjadi, bukan sekadar dua panggilan berurutan yang kebetulan
     * lulus.
     */
    private <T> List<T> runConcurrently(Callable<T> taskA, Callable<T> taskB, List<Throwable> failures)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<T> guardedA = () -> {
            ready.countDown();
            go.await();
            return taskA.call();
        };
        Callable<T> guardedB = () -> {
            ready.countDown();
            go.await();
            return taskB.call();
        };

        try {
            Future<T> futureA = pool.submit(guardedA);
            Future<T> futureB = pool.submit(guardedB);
            ready.await();
            go.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : List.of(futureA, futureB)) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("AC-T05: dua panggilan finalizeIfExpired bersamaan pada sesi yang sama menghasilkan tepat satu Result")
    void concurrentFinalizeIfExpiredProducesExactlyOneResult() throws InterruptedException {
        ExamSessionEntity session = expiredSession("Balap1");
        UUID sessionId = session.getId();
        UUID clientId = session.getClientId();

        Callable<Optional<ResultEntity>> finalize = () -> finalizer.finalizeIfExpired(sessionId, clientId);

        List<Throwable> failures = new ArrayList<>();
        runConcurrently(finalize, finalize, failures);

        // Kunci pesimistis pada baris exam_session mencegah dua transaksi memproses sesi yang
        // sama secara bersamaan (TC-18); unique(result.session_id) adalah jaring terakhir bila
        // ada kelalaian di jalur mana pun (TC-19). Yang diuji di sini adalah invariannya — jumlah
        // Result tetap satu — bukan panggilan mana yang "menang" dan mana yang boleh melempar.
        assertThat(resultRepository.findBySessionIdIn(List.of(sessionId))).hasSize(1);
    }

    @Test
    @DisplayName("AC-T05: finalizeIfExpired dan submit yang berjalan bersamaan pada sesi yang sama tetap menghasilkan tepat satu Result")
    void concurrentFinalizeIfExpiredAndSubmitProducesExactlyOneResult() throws InterruptedException {
        ExamSessionEntity session = expiredSession("Balap2");
        UUID sessionId = session.getId();
        UUID clientId = session.getClientId();

        Callable<Optional<ResultEntity>> finalize = () -> finalizer.finalizeIfExpired(sessionId, clientId);
        Callable<Optional<ResultEntity>> submit = () -> Optional.of(finalizer.submit(sessionId, clientId));

        List<Throwable> failures = new ArrayList<>();
        runConcurrently(finalize, submit, failures);

        // Sama seperti pasangan finalizeIfExpired+finalizeIfExpired: kunci pesimistis
        // menyerialkan keduanya, constraint unik jadi jaring terakhir, dan yang wajib benar
        // hanyalah jumlah Result-nya (TC-18, TC-19) — terlepas dari mana yang sempat melempar.
        assertThat(resultRepository.findBySessionIdIn(List.of(sessionId))).hasSize(1);
    }
}
