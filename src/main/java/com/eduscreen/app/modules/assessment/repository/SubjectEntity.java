package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ContentOrigin;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Subject: GLOBAL milik Eduscreen dan dibaca semua Client, atau CLIENT milik satu sekolah
 * (FR-013). Nama memuat jenjang, mis. "Matematika Kelas 4" (ADR-0004).
 */
@Entity
@Table(name = "subject")
@SQLRestriction("deleted_at is null")
public class SubjectEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentOrigin origin;

    /** Null hanya untuk origin GLOBAL. */
    @Column(name = "client_id")
    private UUID clientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected SubjectEntity() {
    }

    private SubjectEntity(UUID clientId, String name, ContentOrigin origin) {
        if (origin == ContentOrigin.GLOBAL ? clientId != null : clientId == null) {
            throw new IllegalArgumentException(
                    "Origin GLOBAL wajib clientId null; origin CLIENT wajib clientId terisi");
        }
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.name = name;
        this.origin = origin;
    }

    /** Subject master milik Eduscreen, dibaca semua Client. */
    public static SubjectEntity global(String name) {
        return new SubjectEntity(null, name, ContentOrigin.GLOBAL);
    }

    /** Subject buatan satu Client, hanya dibaca Client itu sendiri. */
    public static SubjectEntity forClient(UUID clientId, String name) {
        return new SubjectEntity(clientId, name, ContentOrigin.CLIENT);
    }

    /** Memperbaiki nama yang salah ketik; asal dan pemilik tidak ikut berubah. */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Subject wajib diisi");
        }
        this.name = name.trim();
    }

    /** Penghapusan bersifat soft delete (TC-35). */
    public void softDelete(OffsetDateTime now) {
        this.deletedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ContentOrigin getOrigin() {
        return origin;
    }

    public UUID getClientId() {
        return clientId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
