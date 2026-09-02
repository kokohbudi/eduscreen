package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.ResultKind;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.ScoreAuditEntity;
import com.eduscreen.app.modules.assessment.repository.ScoreAuditRepository;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Penilaian jawaban essay oleh Guru.
 *
 * <p>{@code essayScore} adalah bilangan bulat 0–100; poin soal itu menjadi
 * {@code essayScore ÷ 100}, sehingga bobot antar-soal tetap seragam sementara penilaian tetap
 * luwes untuk jawaban separuh benar (BR-C04).
 *
 * <p>Result dihitung ulang <b>pada permintaan yang sama</b>, dan setiap perubahan meninggalkan
 * satu baris {@code score_audit} hanya-sisip (TC-37, BR-G03) — termasuk perubahan atas Result
 * yang sudah {@code FINAL}. Di sekolah, nilai yang berubah adalah bahan sengketa, dan sistem
 * harus bisa menjawab siapa yang mengubahnya.
 */
@Service
public class GradingService {

    private final SessionAnswerRepository answers;
    private final SessionQuestionRepository sessionQuestions;
    private final ExamSessionRepository sessions;
    private final ResultRepository results;
    private final ScoreAuditRepository audits;
    private final AssignmentPublishingService publishing;
    private final SessionFinalizer finalizer;
    private final ClientClock clock;

    public GradingService(SessionAnswerRepository answers,
                          SessionQuestionRepository sessionQuestions,
                          ExamSessionRepository sessions,
                          ResultRepository results,
                          ScoreAuditRepository audits,
                          AssignmentPublishingService publishing,
                          SessionFinalizer finalizer,
                          ClientClock clock) {
        this.answers = answers;
        this.sessionQuestions = sessionQuestions;
        this.sessions = sessions;
        this.results = results;
        this.audits = audits;
        this.publishing = publishing;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    /** Antrean penilaian: jawaban essay yang belum bernilai pada satu Assignment. */
    @Transactional(readOnly = true)
    public List<SessionAnswerEntity> pendingQueue(UUID assignmentId, UserPrincipal guru) {
        publishing.require(assignmentId, guru);
        List<UUID> sessionQuestionIds = sessions.findByAssignmentId(assignmentId).stream()
                .flatMap(session -> sessionQuestions
                        .findBySessionIdOrderByPositionAsc(session.getId()).stream())
                .map(SessionQuestionEntity::getId)
                .toList();
        return sessionQuestionIds.isEmpty()
                ? List.of()
                : answers.findBySessionQuestionIdIn(sessionQuestionIds).stream()
                        .filter(answer -> answer.getEssayText() != null)
                        .filter(answer -> answer.getEssayScore() == null)
                        .toList();
    }

    /**
     * Memberi atau mengubah nilai satu jawaban essay.
     *
     * <p>Guru yang tidak ditugaskan di Ruangan Assignment itu mendapat {@code 404}, identik
     * dengan jawaban yang tidak ada (BR-G01, TC-09).
     */
    @Transactional
    public ResultEntity grade(UUID sessionAnswerId, int essayScore, UserPrincipal guru) {
        if (essayScore < 0 || essayScore > 100) {
            throw new IllegalArgumentException("Nilai essay harus di antara 0 dan 100");
        }
        SessionAnswerEntity answer = answers.findById(sessionAnswerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jawaban tidak ditemukan"));

        SessionQuestionEntity sessionQuestion = sessionQuestions.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Jawaban tidak ditemukan"));
        ExamSessionEntity session = sessions
                .findByIdAndClientId(sessionQuestion.getSessionId(), guru.requireClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Jawaban tidak ditemukan"));
        AssignmentEntity assignment = publishing.require(session.getAssignmentId(), guru);

        ResultEntity result = results.findBySessionId(session.getId())
                .orElseThrow(() -> new IllegalStateException("Sesi belum difinalisasi"));

        BigDecimal before = result.getScore();
        answer.grade(essayScore);
        answers.save(answer);

        ScoringService.Tally tally = finalizer.tallyOf(session);
        ResultStatus status = result.getKind() == ResultKind.PRACTICE || !tally.hasUngradedEssay()
                ? ResultStatus.FINAL
                : ResultStatus.PENDING_REVIEW;
        result.recompute(tally.correctCount(), tally.incorrectCount(), tally.unansweredCount(),
                tally.score(), status);
        results.save(result);

        audits.save(new ScoreAuditEntity(
                result.getId(), assignment.getClientId(), sessionAnswerId, guru.userId(),
                clock.now(), before, tally.score()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ScoreAuditEntity> trail(UUID resultId) {
        return audits.findByResultIdOrderByChangedAtAsc(resultId);
    }
}
