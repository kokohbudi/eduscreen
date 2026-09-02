package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pembacaan Eduscreen Admin selama jendela dukungan (BR-P05, ADR-0015, TC-46). Hanya-sisip,
 * tanpa setter: bisa ditunjukkan kepada Client sebagai bukti "baca-saja berbatas waktu", bukan
 * sekadar janji.
 */
@Entity
@Table(name = "support_access_read")
public class SupportAccessReadEntity {

    @Id
    private UUID id;

    @Column(name = "grant_id", nullable = false)
    private UUID grantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "read_by", nullable = false)
    private UUID readBy;

    @Column(nullable = false)
    private String resource;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    protected SupportAccessReadEntity() {
    }

    public SupportAccessReadEntity(UUID grantId, UUID clientId, UUID readBy, String resource, OffsetDateTime readAt) {
        this.id = UuidV7.randomUuid();
        this.grantId = grantId;
        this.clientId = clientId;
        this.readBy = readBy;
        this.resource = resource;
        this.readAt = readAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getReadBy() {
        return readBy;
    }

    public String getResource() {
        return resource;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }
}
