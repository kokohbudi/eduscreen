package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadata gambar soal; isinya hidup di FileStoragePort (TC-28). Tabel ini ada supaya endpoint
 * gambar bisa memeriksa {@code clientId} sebelum melayani berkas (TC-26) — tanpanya, satu URL
 * gambar bisa membocorkan soal ujian besok lewat berkas, bukan lewat endpoint Session.
 */
@Entity
@Table(name = "stored_image")
public class StoredImageEntity {

    @Id
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StoredImageEntity() {
    }

    public StoredImageEntity(UUID clientId, UUID fileId, String contentType, int byteSize, UUID uploadedBy) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.fileId = fileId;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getContentType() {
        return contentType;
    }

    public int getByteSize() {
        return byteSize;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
