package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.AssignmentStatus;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Penerbitan satu Exercise ke satu Ruangan (BR-M02). Menerbitkan ke tiga Ruangan menghasilkan
 * tiga baris — waktu, penutupan, dan rekap tiap Ruangan berdiri sendiri.
 *
 * <p>Field yang hanya boleh berubah saat {@link AssignmentStatus#DRAFT} punya setter di bawah;
 * aturan "tolak jika sudah PUBLISHED" adalah tanggung jawab service, bukan entitas ini (BR-A01).
 */
@Entity
@Table(name = "assignment")
public class AssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "ruangan_id", nullable = false)
    private UUID ruanganId;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(nullable = false)
    private String title;

    @Column(name = "timer_duration_minutes")
    private Integer timerDurationMinutes;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Enumerated(EnumType.STRING)
    @Column(name = "reveal_answers_at", nullable = false)
    private RevealAnswersAt revealAnswersAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AssignmentEntity() {
    }

    public AssignmentEntity(
            UUID clientId,
            UUID exerciseId,
            UUID ruanganId,
            UUID publishedBy,
            AssignmentMode mode,
            String title,
            Integer timerDurationMinutes,
            OffsetDateTime expiresAt,
            int maxAttempts,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            RevealAnswersAt revealAnswersAt) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.exerciseId = exerciseId;
        this.ruanganId = ruanganId;
        this.publishedBy = publishedBy;
        this.mode = mode;
        this.title = title;
        this.timerDurationMinutes = timerDurationMinutes;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.shuffleQuestions = shuffleQuestions;
        this.shuffleOptions = shuffleOptions;
        this.revealAnswersAt = revealAnswersAt;
    }

    public void publish(OffsetDateTime now) {
        this.status = AssignmentStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void close(OffsetDateTime now) {
        this.status = AssignmentStatus.CLOSED;
        this.closedAt = now;
    }

    /** Hanya mengubah field; menolak pemajuan tanggal (deadline maju, bukan mundur) milik service. */
    public void extendExpiry(OffsetDateTime newExpiry) {
        this.expiresAt = newExpiry;
    }

    public boolean isDraft() {
        return status == AssignmentStatus.DRAFT;
    }

    public boolean isPublished() {
        return status == AssignmentStatus.PUBLISHED;
    }

    public boolean isPractice() {
        return mode == AssignmentMode.PRACTICE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    /** Hanya boleh berubah saat DRAFT (BR-A01) — pengecekan itu tugas service. */
    public void setExerciseId(UUID exerciseId) {
        this.exerciseId = exerciseId;
    }

    public UUID getRuanganId() {
        return ruanganId;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public AssignmentMode getMode() {
        return mode;
    }

    public void setMode(AssignmentMode mode) {
        this.mode = mode;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getTimerDurationMinutes() {
        return timerDurationMinutes;
    }

    public void setTimerDurationMinutes(Integer timerDurationMinutes) {
        this.timerDurationMinutes = timerDurationMinutes;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isShuffleQuestions() {
        return shuffleQuestions;
    }

    public void setShuffleQuestions(boolean shuffleQuestions) {
        this.shuffleQuestions = shuffleQuestions;
    }

    public boolean isShuffleOptions() {
        return shuffleOptions;
    }

    public void setShuffleOptions(boolean shuffleOptions) {
        this.shuffleOptions = shuffleOptions;
    }

    public RevealAnswersAt getRevealAnswersAt() {
        return revealAnswersAt;
    }

    public void setRevealAnswersAt(RevealAnswersAt revealAnswersAt) {
        this.revealAnswersAt = revealAnswersAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
