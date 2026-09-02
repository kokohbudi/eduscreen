package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.domain.TerminalReason;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lahir hanya saat Siswa menekan Mulai (BR-S01). {@code effectiveDeadline} dibekukan sejak
 * konstruksi (BR-T04): Global Expiration selalu memangkas Timer, dan pemangkasan itu terlihat
 * sejak detik pertama, bukan sebagai pemutusan mendadak di tengah jalan.
 */
@Entity
@Table(name = "exam_session")
public class ExamSessionEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "effective_deadline", nullable = false)
    private OffsetDateTime effectiveDeadline;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_reason")
    private TerminalReason terminalReason;

    protected ExamSessionEntity() {
    }

    public ExamSessionEntity(
            UUID clientId,
            UUID assignmentId,
            UUID studentId,
            int attemptNumber,
            OffsetDateTime startedAt,
            OffsetDateTime effectiveDeadline) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
        this.effectiveDeadline = effectiveDeadline;
    }

    /** MANUAL_SUBMIT berakhir COMPLETED; sebab lain (timeout, expiration) berakhir EXPIRED (BR-T06). */
    public void finalizeWith(TerminalReason reason, OffsetDateTime now) {
        this.status = reason == TerminalReason.MANUAL_SUBMIT ? SessionStatus.COMPLETED : SessionStatus.EXPIRED;
        this.terminalReason = reason;
        this.finalizedAt = now;
    }

    /** Dipakai saat Guru memperpanjang expires_at; hanya berlaku selagi sesi IN_PROGRESS (BR-T06). */
    public void recomputeDeadline(OffsetDateTime newDeadline) {
        this.effectiveDeadline = newDeadline;
    }

    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEffectiveDeadline() {
        return effectiveDeadline;
    }

    public OffsetDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public TerminalReason getTerminalReason() {
        return terminalReason;
    }
}
