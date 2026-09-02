package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Jejak nilai yang tidak boleh bisa dihapus (TC-37, BR-G03). Hanya-sisip: tidak ada satu pun
 * setter dan tidak ada method yang mengubah state setelah baris dibuat — nilai yang berubah
 * adalah bahan sengketa di sekolah, dan sistem harus bisa menjawab siapa yang mengubahnya.
 *
 * <p>{@code sessionAnswerId} null berarti baris ini mencatat perhitungan ulang Result secara
 * keseluruhan, bukan perubahan satu jawaban essay.
 */
@Entity
@Table(name = "score_audit")
public class ScoreAuditEntity {

    @Id
    private UUID id;

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "session_answer_id")
    private UUID sessionAnswerId;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "old_value", precision = 6, scale = 4)
    private BigDecimal oldValue;

    @Column(name = "new_value", precision = 6, scale = 4)
    private BigDecimal newValue;

    protected ScoreAuditEntity() {
    }

    public ScoreAuditEntity(
            UUID resultId,
            UUID clientId,
            UUID sessionAnswerId,
            UUID changedBy,
            OffsetDateTime changedAt,
            BigDecimal oldValue,
            BigDecimal newValue) {
        this.id = UuidV7.randomUuid();
        this.resultId = resultId;
        this.clientId = clientId;
        this.sessionAnswerId = sessionAnswerId;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getId() {
        return id;
    }

    public UUID getResultId() {
        return resultId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSessionAnswerId() {
        return sessionAnswerId;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public BigDecimal getOldValue() {
        return oldValue;
    }

    public BigDecimal getNewValue() {
        return newValue;
    }
}
