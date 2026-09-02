package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Satu-satunya pengecualian isolasi tenant (BR-P05, ADR-0015): baca-saja, dinyalakan Client
 * Admin, padam sendiri setelah 4 jam. Jalur resmi yang sempit ada supaya jalur tidak resmi tidak
 * punya alasan untuk dipakai.
 */
@Entity
@Table(name = "support_access_grant")
public class SupportAccessGrantEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected SupportAccessGrantEntity() {
    }

    public SupportAccessGrantEntity(UUID clientId, UUID grantedBy, OffsetDateTime grantedAt, OffsetDateTime expiresAt) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(OffsetDateTime now) {
        this.revokedAt = now;
    }

    /** Aktif bila belum dicabut dan belum melewati jendela 4 jamnya. */
    public boolean isActive(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public OffsetDateTime getGrantedAt() {
        return grantedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
