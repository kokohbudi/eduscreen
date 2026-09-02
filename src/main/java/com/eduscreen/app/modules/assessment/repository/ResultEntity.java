package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ResultKind;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Skor disimpan, tidak dihitung ulang saat dibaca (BR-T09): angka historis tidak boleh bergeser
 * bila aturan skoring berubah di rilis berikutnya.
 */
@Entity
@Table(name = "result")
public class ResultEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultKind kind;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "unanswered_count", nullable = false)
    private int unansweredCount;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ResultEntity() {
    }

    public ResultEntity(
            UUID sessionId,
            UUID clientId,
            ResultKind kind,
            ResultStatus status,
            int totalQuestions,
            int correctCount,
            int incorrectCount,
            int unansweredCount,
            BigDecimal score) {
        this.id = UuidV7.randomUuid();
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.kind = kind;
        this.status = status;
        this.totalQuestions = totalQuestions;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.unansweredCount = unansweredCount;
        this.score = score;
    }

    /** Dipakai saat penilaian essay mengubah skor; setiap panggilan wajib berujung pada score_audit (TC-37). */
    public void recompute(
            int correctCount, int incorrectCount, int unansweredCount, BigDecimal score, ResultStatus status) {
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.unansweredCount = unansweredCount;
        this.score = score;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public ResultKind getKind() {
        return kind;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
    }

    public int getUnansweredCount() {
        return unansweredCount;
    }

    public BigDecimal getScore() {
        return score;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
