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
 * Kumpulan soal yang ditulis sebagai satu kesatuan dan diberi satu Subject.
 *
 * <p>Paket ber-{@code clientId} null milik Eduscreen dan bisa diterbitkan ke katalog; selebihnya
 * milik satu Client. Paket adalah satuan yang diadopsi (ADR-0018), sedangkan yang ditugaskan ke
 * Siswa tetap Exercise.
 */
@Entity
@Table(name = "paket")
@SQLRestriction("deleted_at is null")
public class PaketEntity {

    @Id
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "created_by")
    private UUID createdBy;

    /** Terisi berarti terbit di katalog. Hanya bermakna bagi Paket master (FR-066). */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** Jejak asal saja; tidak pernah dipakai untuk sinkronisasi (ADR-0001). */
    @Column(name = "source_paket_id")
    private UUID sourcePaketId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected PaketEntity() {
    }

    private PaketEntity(UUID clientId, UUID subjectId, String title, UUID createdBy) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.subjectId = subjectId;
        this.title = title;
        this.createdBy = createdBy;
    }

    public static PaketEntity forClient(UUID clientId, UUID subjectId, String title, UUID createdBy) {
        if (clientId == null) {
            throw new IllegalArgumentException("Paket milik Client wajib punya clientId");
        }
        return new PaketEntity(clientId, subjectId, title, createdBy);
    }

    public static PaketEntity master(UUID subjectId, String title, UUID createdBy) {
        return new PaketEntity(null, subjectId, title, createdBy);
    }

    public static PaketEntity adoptedFrom(UUID clientId, UUID subjectId, String title,
                                          UUID createdBy, UUID sourcePaketId) {
        PaketEntity copy = forClient(clientId, subjectId, title, createdBy);
        copy.sourcePaketId = sourcePaketId;
        return copy;
    }

    public void publish(OffsetDateTime now) {
        if (clientId != null) {
            throw new IllegalStateException("Hanya Paket master yang bisa diterbitkan");
        }
        this.publishedAt = now;
    }

    public void withdraw() {
        this.publishedAt = null;
    }

    public void rename(String title) {
        this.title = title;
    }

    /** Penghapusan bersifat soft delete (TC-35). */
    public void softDelete(OffsetDateTime now) {
        this.deletedAt = now;
    }

    public boolean isPublished() {
        return publishedAt != null;
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

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public UUID getSourcePaketId() {
        return sourcePaketId;
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
