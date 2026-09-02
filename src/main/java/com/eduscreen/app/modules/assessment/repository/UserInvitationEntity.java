package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.InvitationPurpose;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Undangan akun dan reset password (BR-U04). Yang disimpan adalah hash token, bukan tokennya —
 * bocornya isi tabel ini tidak boleh cukup untuk mengambil alih akun (TC-06).
 */
@Entity
@Table(name = "user_invitation")
public class UserInvitationEntity {

    @Id
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserInvitationEntity() {
    }

    public UserInvitationEntity(UUID clientId, UUID userId, String tokenHash, InvitationPurpose purpose, OffsetDateTime expiresAt) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }

    /** Belum dipakai dan belum kedaluwarsa. */
    public boolean isUsable(OffsetDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationPurpose getPurpose() {
        return purpose;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
