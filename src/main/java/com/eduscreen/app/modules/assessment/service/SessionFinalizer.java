package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.ResultKind;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.modules.assessment.domain.TerminalReason;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Menutup sesi dan menghitung Result-nya.
 *
 * <p>Tidak ada scheduler (ADR-0002): finalisasi terjadi saat sesi diakses. Konsekuensinya,
 * Siswa yang memuat ulang halaman dan Guru yang membuka laporan bisa memfinalisasi sesi yang
 * sama pada saat yang sama — jadi seluruh jalur di kelas ini mengambil <b>kunci pesimistis</b>
 * lebih dulu dan memeriksa status <b>setelah</b> kunci didapat (TC-18).
 *
 * <p>Kunci mencegah balapan; {@code unique(result.session_id)} memastikan kelalaian di jalur
 * mana pun tetap tidak sanggup melahirkan Result kedua (TC-19). Aturan yang tidak boleh
 * dilanggar dijaga database, bukan niat baik kode.
 *
 * <p>Tiap pemanggilan berjalan di transaksinya sendiri ({@code REQUIRES_NEW}) sehingga
 * finalisasi borongan saat Guru membuka rekap tidak mengunci seluruh Ruangan dalam satu
 * transaksi panjang (TC-21).
 */
@Service
public class SessionFinalizer {

    private final ExamSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final SessionQuestionRepository sessionQuestions;
    private final SessionAnswerRepository sessionAnswers;
    private final QuestionRepository questions;
    private final ResultRepository results;
    private final ScoringService scoring;
    private final ClientClock clock;

    public SessionFinalizer(ExamSessionRepository sessions,
                            AssignmentRepository assignments,
                            SessionQuestionRepository sessionQuestions,
                            SessionAnswerRepository sessionAnswers,
                            QuestionRepository questions,
                            ResultRepository results,
                            ScoringService scoring,
                            ClientClock clock) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.sessionQuestions = sessionQuestions;
        this.sessionAnswers = sessionAnswers;
        this.questions = questions;
        this.results = results;
        this.scoring = scoring;
        this.clock = clock;
    }

    /**
     * Menutup sesi bila batas waktunya sudah lewat; no-op bila belum atau bila sudah terminal.
     *
     * @return Result sesi bila ada — yang baru dibuat maupun yang sudah ada sebelumnya
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultEntity> finalizeIfExpired(UUID sessionId, UUID clientId) {
        ExamSessionEntity session = lock(sessionId, clientId);

        if (!session.isInProgress()) {
            return results.findBySessionId(sessionId);   // Idempoten (BR-T07).
        }
        if (!clock.now().isAfter(session.getEffectiveDeadline())) {
            return Optional.empty();
        }

        AssignmentEntity assignment = requireAssignment(session);
        // Deadline efektif yang sama persis dengan expires_at berarti yang memangkas adalah
        // Global Expiration, bukan Timer (§8.3 business-rules).
        TerminalReason reason = session.getEffectiveDeadline().isEqual(assignment.getExpiresAt())
                ? TerminalReason.EXPIRATION_REACHED
                : TerminalReason.TIMER_TIMEOUT;

        return Optional.of(close(session, assignment, reason));
    }

    /** Siswa menekan Selesai. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultEntity submit(UUID sessionId, UUID clientId) {
        ExamSessionEntity session = lock(sessionId, clientId);
        if (!session.isInProgress()) {
            return results.findBySessionId(sessionId)
                    .orElseThrow(() -> new IllegalStateException("Sesi sudah berakhir"));
        }
        AssignmentEntity assignment = requireAssignment(session);
        return close(session, assignment, TerminalReason.MANUAL_SUBMIT);
    }

    /** Guru menutup Assignment lebih awal: seluruh sesi berjalan ikut ditutup (BR-A05). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultEntity> closeEarly(UUID sessionId, UUID clientId) {
        ExamSessionEntity session = lock(sessionId, clientId);
        if (!session.isInProgress()) {
            return results.findBySessionId(sessionId);
        }
        AssignmentEntity assignment = requireAssignment(session);
        return Optional.of(close(session, assignment, TerminalReason.EXPIRATION_REACHED));
    }

    private ExamSessionEntity lock(UUID sessionId, UUID clientId) {
        return sessions.findByIdForUpdate(sessionId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesi tidak ditemukan"));
    }

    private AssignmentEntity requireAssignment(ExamSessionEntity session) {
        return assignments.findByIdAndClientId(session.getAssignmentId(), session.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment tidak ditemukan"));
    }

    private ResultEntity close(ExamSessionEntity session,
                               AssignmentEntity assignment,
                               TerminalReason reason) {
        session.finalizeWith(reason, clock.now());
        sessions.save(session);

        ScoringService.Tally tally = tallyOf(session);
        ResultKind kind = assignment.isPractice() ? ResultKind.PRACTICE : ResultKind.GRADED;
        // Practice tidak pernah memuat essay (BR-M04), jadi Result-nya selalu langsung FINAL
        // (BR-R01, BR-C09).
        ResultStatus status = kind == ResultKind.PRACTICE || !tally.hasUngradedEssay()
                ? ResultStatus.FINAL
                : ResultStatus.PENDING_REVIEW;

        return results.save(new ResultEntity(
                session.getId(), session.getClientId(), kind, status,
                tally.totalQuestions(), tally.correctCount(), tally.incorrectCount(),
                tally.unansweredCount(), tally.score()));
    }

    /** Rekap satu sesi dari snapshot dan jawabannya; dipakai juga saat Guru menilai essay. */
    ScoringService.Tally tallyOf(ExamSessionEntity session) {
        List<SessionQuestionEntity> snapshot =
                sessionQuestions.findBySessionIdOrderByPositionAsc(session.getId());

        Map<UUID, SessionAnswerEntity> answers = sessionAnswers
                .findBySessionQuestionIdIn(snapshot.stream().map(SessionQuestionEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(SessionAnswerEntity::getSessionQuestionId, Function.identity()));

        Map<UUID, QuestionType> types = questions
                .findAllForSnapshot(snapshot.stream().map(SessionQuestionEntity::getQuestionId).toList())
                .stream()
                .collect(Collectors.toMap(QuestionEntity::getId, QuestionEntity::getType));

        return scoring.tally(snapshot, answers, types);
    }
}
