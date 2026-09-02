package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Netral terhadap mode (Practice/Assignment). Terkunci begitu Assignment pertamanya lahir;
 * setelah itu read-only dan perubahan dilakukan dengan menduplikasinya (FR-026).
 */
@Entity
@Table(name = "exercise")
@SQLRestriction("deleted_at is null")
public class ExerciseEntity {

    @Id
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_by")
    private UUID createdBy;

    /** Terisi saat penerbitan pertama; setelahnya tidak berubah lagi (FR-026). */
    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    /**
     * Terisi berarti terbit dan terlihat di katalog seluruh Client; kosong berarti masih digarap
     * dan hanya terlihat Eduscreen Admin (FR-066).
     *
     * <p>Hanya bermakna bagi konten master. Check constraint {@code exercise_publish_master_only} menolaknya
     * terisi pada baris milik sebuah Client.
     */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected ExerciseEntity() {
    }

    public ExerciseEntity(UUID clientId, String title, UUID createdBy) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.title = title;
        this.createdBy = createdBy;
    }

    /** Idempoten: begitu terkunci, panggilan berikutnya tidak mengubah {@code lockedAt} (FR-026). */
    public void lock(OffsetDateTime now) {
        if (this.lockedAt == null) {
            this.lockedAt = now;
        }
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    /** Penghapusan bersifat soft delete (TC-35). */
    public void softDelete(OffsetDateTime now) {
        this.deletedAt = now;
    }

    /** Menerbitkan; idempoten, penerbitan ulang tidak menggeser waktu terbit pertama (FR-066). */
    public void publish(OffsetDateTime now) {
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    /**
     * Menarik dari peredaran (FR-068). Tidak menyentuh satu pun salinan yang sudah diadopsi
     * Client: adopsi adalah salinan penuh, bukan tautan hidup (ADR-0001).
     */
    public void unpublish() {
        this.publishedAt = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
