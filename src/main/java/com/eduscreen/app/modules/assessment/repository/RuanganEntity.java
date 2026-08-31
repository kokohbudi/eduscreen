package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.RuanganStatus;
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
 * Kelompok belajar. Tahun ajaran ditangani lewat pengarsipan dan penamaan yang memuat periode,
 * bukan lewat entitas tahun ajaran tersendiri.
 */
@Entity
@Table(name = "ruangan")
public class RuanganEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuanganStatus status = RuanganStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected RuanganEntity() {
    }

    public RuanganEntity(UUID clientId, String name) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.name = name;
    }

    /** Ruangan terarsip menolak Assignment dan anggota baru (FR-010). */
    public boolean isArchived() {
        return status == RuanganStatus.ARCHIVED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RuanganStatus getStatus() {
        return status;
    }

    public void archive() {
        this.status = RuanganStatus.ARCHIVED;
        touch();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
