package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
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

import java.util.UUID;

/**
 * Auto-save jawaban.
 *
 * <p>Upsert berkunci {@code session_question_id} (TC-20). Kiriman ulang berisi jawaban identik
 * adalah <b>no-op yang sukses</b>, bukan galat: antrean coba-ulang di klien (BR-S08) menjamin
 * server akan menerima kiriman ganda, dan server yang menolaknya mengubah mekanisme pemulihan
 * menjadi sumber kerusakan — Siswa melihat galat untuk jawaban yang sebenarnya sudah tersimpan.
 *
 * <p>Hanya jawaban <b>berbeda</b> untuk SessionQuestion yang sudah terkunci di mode Practice
 * yang ditolak (BR-S07).
 */
@Service
public class AnswerService {

    /** Hasil satu penyimpanan; {@code revealed} menandai Practice yang membuka pembahasan. */
    public record Saved(SessionAnswerEntity answer, boolean locked, boolean noop) {
    }

    private final SessionQuestionRepository sessionQuestions;
    private final SessionAnswerRepository answers;
    private final QuestionOptionRepository options;
    private final AssignmentRepository assignments;
    private final ExamSessionService examSessions;
    private final SessionFinalizer finalizer;
    private final ClientClock clock;

    public AnswerService(SessionQuestionRepository sessionQuestions,
                         SessionAnswerRepository answers,
                         QuestionOptionRepository options,
                         AssignmentRepository assignments,
                         ExamSessionService examSessions,
                         SessionFinalizer finalizer,
                         ClientClock clock) {
        this.sessionQuestions = sessionQuestions;
        this.answers = answers;
        this.options = options;
        this.assignments = assignments;
        this.examSessions = examSessions;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    @Transactional
    public Saved save(UUID sessionId,
                      UUID sessionQuestionId,
                      UUID selectedOptionId,
                      String essayText,
                      UserPrincipal student) {

        ExamSessionEntity session = examSessions.requireOwnSession(sessionId, student);

        if (!session.isInProgress()) {
            throw new GoneException("Sesi sudah berakhir");
        }
        if (clock.now().isAfter(session.getEffectiveDeadline())) {
            // Jawaban yang tiba setelah batas waktu ditolak, dan sesinya difinalisasi pada
            // permintaan yang sama (BR-T08, FR-044).
            finalizer.finalizeIfExpired(sessionId, student.requireClientId());
            throw new GoneException("Waktu pengerjaan sudah habis");
        }

        SessionQuestionEntity sessionQuestion = sessionQuestions
                .findByIdAndSessionId(sessionQuestionId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        SessionAnswerEntity answer = answers.findBySessionQuestionId(sessionQuestionId)
                .orElseGet(() -> new SessionAnswerEntity(sessionQuestionId));

        boolean identical = answer.getAnsweredAt() != null
                && answer.sameAs(selectedOptionId, essayText);

        if (sessionQuestion.isLocked()) {
            if (identical) {
                return new Saved(answer, true, true);
            }
            throw new IllegalStateException("Jawaban soal ini sudah terkunci dan tidak bisa diubah");
        }
        if (identical) {
            return new Saved(answer, false, true);
        }

        if (selectedOptionId != null) {
            // is_correct dihitung dan disimpan saat itu juga; skor tidak pernah dihitung ulang
            // saat dibaca (BR-T09).
            boolean correct = options.findByIdAndQuestionId(selectedOptionId, sessionQuestion.getQuestionId())
                    .map(QuestionOptionEntity::isCorrect)
                    .orElseThrow(() -> new IllegalArgumentException("Pilihan jawaban tidak sah"));
            answer.recordChoice(selectedOptionId, correct, clock.now());
        } else {
            answer.recordEssay(essayText, clock.now());
        }
        answers.save(answer);

        AssignmentEntity assignment = assignments
                .findByIdAndClientId(session.getAssignmentId(), session.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment tidak ditemukan"));

        // Pada Practice jawaban terkunci saat dikirim, dan pembahasan terbuka seketika
        // (BR-S07, §9.5).
        if (assignment.isPractice()) {
            sessionQuestion.lock(clock.now());
            sessionQuestions.save(sessionQuestion);
            return new Saved(answer, true, false);
        }
        return new Saved(answer, false, false);
    }
}
