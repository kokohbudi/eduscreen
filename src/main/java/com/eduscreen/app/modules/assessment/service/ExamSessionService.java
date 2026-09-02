package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.AssignmentStatus;
import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.GoneException;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kelahiran dan pembacaan sesi pengerjaan.
 *
 * <p>Sesi lahir <b>hanya</b> saat Siswa menekan Mulai (BR-S01, ADR-0002). Tidak ada pembuatan
 * massal: Ruangan berisi 30 Siswa yang belum satu pun menekan Mulai tidak punya satu baris sesi
 * pun, dan Siswa yang tidak pernah mengerjakan tampil {@code NOT_STARTED} di rekap tanpa baris
 * sesi dibuat untuknya.
 *
 * <p>Saat sesi lahir, dua hal dibekukan sekaligus dan tidak pernah berubah lagi: snapshot
 * urutan soal beserta urutan Option-nya (BR-S02), dan {@code effective_deadline} (BR-T04).
 */
@Service
public class ExamSessionService {

    private static final SecureRandom SHUFFLE = new SecureRandom();

    private final AssignmentRepository assignments;
    private final ExamSessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;
    private final ExerciseItemRepository exerciseItems;
    private final QuestionOptionRepository options;
    private final QuestionRepository questions;
    private final SessionAnswerRepository answers;
    private final RuanganService ruangan;
    private final SessionFinalizer finalizer;
    private final ClientClock clock;

    public ExamSessionService(AssignmentRepository assignments,
                              ExamSessionRepository sessions,
                              SessionQuestionRepository sessionQuestions,
                              ExerciseItemRepository exerciseItems,
                              QuestionOptionRepository options,
                              QuestionRepository questions,
                              SessionAnswerRepository answers,
                              RuanganService ruangan,
                              SessionFinalizer finalizer,
                              ClientClock clock) {
        this.assignments = assignments;
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.exerciseItems = exerciseItems;
        this.options = options;
        this.questions = questions;
        this.answers = answers;
        this.ruangan = ruangan;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    /**
     * Assignment yang boleh dibuka seorang Siswa.
     *
     * <p>Keanggotaan Ruangan ikut menentukan: Assignment milik Ruangan yang tidak diikutinya
     * menghasilkan {@code 404} yang identik dengan Assignment yang tidak ada (TC-09).
     */
    @Transactional(readOnly = true)
    public AssignmentEntity requireVisibleAssignment(UUID assignmentId, UserPrincipal student) {
        AssignmentEntity assignment = assignments
                .findByIdAndClientId(assignmentId, student.requireClientId())
                .filter(a -> a.getStatus() != AssignmentStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment tidak ditemukan"));
        if (!ruangan.isMember(assignment.getRuanganId(), student.userId())) {
            throw new ResourceNotFoundException("Assignment tidak ditemukan");
        }
        return assignment;
    }

    /**
     * Membuka sesi pengerjaan.
     *
     * <p>Mengembalikan sesi {@code IN_PROGRESS} yang sudah ada bila ada (BR-S06) — Siswa yang
     * browsernya tertutup lalu kembali harus mendapat sesi yang sama, bukan sesi baru dengan
     * jawaban kosong.
     */
    @Transactional
    public ExamSessionEntity start(UUID assignmentId, UserPrincipal student) {
        AssignmentEntity assignment = requireVisibleAssignment(assignmentId, student);
        OffsetDateTime now = clock.now();

        if (assignment.getStatus() == AssignmentStatus.CLOSED || now.isAfter(assignment.getExpiresAt())) {
            throw new GoneException("Assignment ini sudah ditutup");
        }

        Optional<ExamSessionEntity> running = sessions
                .findFirstByAssignmentIdAndStudentIdAndStatusOrderByAttemptNumberDesc(
                        assignmentId, student.userId(), SessionStatus.IN_PROGRESS);
        if (running.isPresent()) {
            // Bisa jadi batas waktunya sudah lewat sementara Siswa pergi; finalisasi dulu, lalu
            // baca ulang keadaannya.
            finalizer.finalizeIfExpired(running.get().getId(), student.requireClientId());
            ExamSessionEntity reread = sessions
                    .findByIdAndStudentIdAndClientId(
                            running.get().getId(), student.userId(), student.requireClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sesi tidak ditemukan"));
            if (reread.isInProgress()) {
                return reread;
            }
        }

        int nextAttempt = sessions.countByAssignmentIdAndStudentId(assignmentId, student.userId()) + 1;
        // Practice tidak mengenal batas pengulangan; Quiz dibatasi maxAttempts (BR-M06, BR-S05).
        if (!assignment.isPractice() && nextAttempt > assignment.getMaxAttempts()) {
            throw new IllegalStateException("Batas pengulangan sudah tercapai");
        }

        ExamSessionEntity session = sessions.save(new ExamSessionEntity(
                assignment.getClientId(), assignmentId, student.userId(), nextAttempt,
                now, effectiveDeadline(assignment, now)));

        freezeSnapshot(session, assignment);
        return session;
    }

    /**
     * Sesi milik Siswa yang sedang login.
     *
     * <p>Kepemilikan dan {@code clientId} masuk ke klausa query, bukan diperiksa setelah entitas
     * termuat: sesi milik orang lain tidak pernah sampai ke memori proses (TC-08).
     */
    @Transactional(readOnly = true)
    public ExamSessionEntity requireOwnSession(UUID sessionId, UserPrincipal student) {
        return sessions.findByIdAndStudentIdAndClientId(
                        sessionId, student.userId(), student.requireClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Sesi tidak ditemukan"));
    }

    /** Membaca sesi setelah memberinya kesempatan berakhir sendiri bila waktunya sudah lewat. */
    @Transactional
    public ExamSessionEntity readAndFinalize(UUID sessionId, UserPrincipal student) {
        ExamSessionEntity session = requireOwnSession(sessionId, student);
        if (session.isInProgress()) {
            finalizer.finalizeIfExpired(sessionId, student.requireClientId());
            return requireOwnSession(sessionId, student);
        }
        return session;
    }

    @Transactional(readOnly = true)
    public List<SessionQuestionEntity> snapshotOf(UUID sessionId) {
        return sessionQuestions.findBySessionIdOrderByPositionAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ExamSessionEntity> attemptsOf(UUID assignmentId, UUID studentId) {
        return sessions.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(assignmentId, studentId);
    }

    /** Sisa waktu menurut server; jam perangkat Siswa tidak pernah menjadi rujukan (BR-T03). */
    public long remainingSeconds(ExamSessionEntity session) {
        long seconds = java.time.Duration.between(clock.now(), session.getEffectiveDeadline()).toSeconds();
        return Math.max(seconds, 0);
    }

    /**
     * {@code min(started + timer, expiresAt)}; tanpa Timer, {@code expiresAt} adalah satu-satunya
     * batas (BR-T04, BR-T05).
     *
     * <p>Global Expiration selalu memangkas Timer, dan pemangkasan itu sudah terlihat di layar
     * sejak detik pertama — bukan sebagai 60 menit yang tiba-tiba terputus di menit ke-10.
     */
    static OffsetDateTime effectiveDeadline(AssignmentEntity assignment, OffsetDateTime startedAt) {
        if (assignment.getTimerDurationMinutes() == null) {
            return assignment.getExpiresAt();
        }
        OffsetDateTime timerEnd = startedAt.plusMinutes(assignment.getTimerDurationMinutes());
        return timerEnd.isBefore(assignment.getExpiresAt()) ? timerEnd : assignment.getExpiresAt();
    }

    /**
     * Membekukan urutan soal dan urutan Option untuk sesi ini (FR-035).
     *
     * <p>Pengacakan terjadi sekali di sini. Dua Siswa pada Assignment yang sama mendapat urutan
     * berbeda, dan urutan masing-masing tetap sama sepanjang sesinya (BR-S03).
     */
    private void freezeSnapshot(ExamSessionEntity session, AssignmentEntity assignment) {
        List<UUID> questionIds = new ArrayList<>(
                exerciseItems.findByExerciseIdOrderByPositionAsc(assignment.getExerciseId())
                        .stream().map(ExerciseItemEntity::getQuestionId).toList());
        if (assignment.isShuffleQuestions()) {
            Collections.shuffle(questionIds, SHUFFLE);
        }

        Map<UUID, List<QuestionOptionEntity>> optionsByQuestion = options
                .findByQuestionIdIn(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));

        int position = 0;
        for (UUID questionId : questionIds) {
            List<UUID> order = new ArrayList<>(
                    optionsByQuestion.getOrDefault(questionId, List.of()).stream()
                            .sorted(java.util.Comparator.comparingInt(QuestionOptionEntity::getPosition))
                            .map(QuestionOptionEntity::getId)
                            .toList());
            if (assignment.isShuffleOptions()) {
                Collections.shuffle(order, SHUFFLE);
            }
            sessionQuestions.save(new SessionQuestionEntity(
                    session.getId(), questionId, position++, order.toArray(UUID[]::new)));
        }
    }

    /**
     * Satu soal sebagaimana dilihat Siswa: batang soal, Option dalam urutan yang dibekukan untuk
     * sesi ini, dan jawaban yang sudah tersimpan.
     *
     * <p>{@code reveal} menandai Practice yang sudah mengunci soal ini, sehingga benar/salah dan
     * pembahasan boleh ditampilkan seketika (§9.5).
     */
    public record QuestionView(SessionQuestionEntity sessionQuestion,
                               QuestionEntity question,
                               List<QuestionOptionEntity> options,
                               SessionAnswerEntity answer,
                               boolean reveal,
                               int total) {
    }

    /**
     * Merakit satu soal untuk dirender.
     *
     * <p>Soal dimuat lewat jalur snapshot yang menembus soft delete: menghapus soal saat ujian
     * berjalan tidak boleh mengubah apa pun yang dilihat Siswa yang sedang mengerjakannya
     * (BR-Q04).
     */
    @Transactional(readOnly = true)
    public QuestionView view(ExamSessionEntity session, int position, boolean practice) {
        SessionQuestionEntity sessionQuestion = sessionQuestions
                .findBySessionIdAndPosition(session.getId(), position)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        QuestionEntity question = questions
                .findAllForSnapshot(List.of(sessionQuestion.getQuestionId()))
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        // Urutan Option mengikuti snapshot, bukan urutan aslinya di bank soal (BR-S02).
        Map<UUID, QuestionOptionEntity> byId = options
                .findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                .collect(Collectors.toMap(QuestionOptionEntity::getId, o -> o));
        List<QuestionOptionEntity> ordered = new ArrayList<>();
        for (UUID optionId : sessionQuestion.getOptionOrder()) {
            QuestionOptionEntity option = byId.get(optionId);
            if (option != null) {
                ordered.add(option);
            }
        }

        SessionAnswerEntity answer = answers.findBySessionQuestionId(sessionQuestion.getId()).orElse(null);
        int total = (int) sessionQuestions.countBySessionId(session.getId());
        return new QuestionView(sessionQuestion, question, ordered, answer,
                practice && sessionQuestion.isLocked(), total);
    }

    /**
     * Posisi soal yang sudah dijawab; dasar peta soal di halaman pengerjaan.
     *
     * <p>Pada Practice, peta ini juga menentukan sampai mana Siswa boleh maju: navigasinya satu
     * arah, jadi lompatan ke depan ditolak.
     */
    @Transactional(readOnly = true)
    public List<Integer> answeredPositions(UUID sessionId) {
        List<SessionQuestionEntity> snapshot = sessionQuestions.findBySessionIdOrderByPositionAsc(sessionId);
        Map<UUID, SessionAnswerEntity> saved = answers
                .findBySessionQuestionIdIn(snapshot.stream().map(SessionQuestionEntity::getId).toList())
                .stream().collect(Collectors.toMap(SessionAnswerEntity::getSessionQuestionId, a -> a));
        List<Integer> positions = new ArrayList<>();
        for (SessionQuestionEntity sessionQuestion : snapshot) {
            SessionAnswerEntity answer = saved.get(sessionQuestion.getId());
            if (answer != null && answer.isAnswered()) {
                positions.add(sessionQuestion.getPosition());
            }
        }
        return positions;
    }

    /**
     * Navigasi Practice adalah maju satu arah (§6.7): batas maju adalah satu langkah setelah
     * soal terakhir yang sudah dijawab.
     */
    public int practiceFrontier(List<Integer> answeredPositions) {
        return answeredPositions.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    /**
     * Assignment aktif dari <b>seluruh</b> Ruangan yang diikuti Siswa, dalam satu daftar
     * (FR-058, AC-U01).
     *
     * <p>Siswa yang terdaftar di kelas reguler sekaligus grup bimbel tidak boleh harus berpindah
     * konteks untuk tahu apa yang harus dikerjakan malam ini.
     */
    @Transactional(readOnly = true)
    public List<AssignmentEntity> activeAssignments(UserPrincipal student) {
        List<UUID> ruanganIds = ruangan.ruanganOf(student.requireClientId(), student.userId())
                .stream().map(r -> r.getId()).toList();
        if (ruanganIds.isEmpty()) {
            return List.of();
        }
        return assignments.findByClientIdAndRuanganIdInAndStatusOrderByExpiresAtAsc(
                student.requireClientId(), ruanganIds, AssignmentStatus.PUBLISHED);
    }

    /** Riwayat pengerjaan Siswa sendiri, terbaru lebih dulu. */
    @Transactional(readOnly = true)
    public List<ExamSessionEntity> history(UserPrincipal student) {
        return sessions.findByClientIdAndStudentIdOrderByStartedAtDesc(
                student.requireClientId(), student.userId());
    }

    @Transactional(readOnly = true)
    public AssignmentEntity assignmentOf(ExamSessionEntity session) {
        return assignments.findByIdAndClientId(session.getAssignmentId(), session.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment tidak ditemukan"));
    }
}
