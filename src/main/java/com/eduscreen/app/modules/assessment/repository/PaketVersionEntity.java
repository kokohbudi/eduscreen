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
 * Satu versi isi sebuah Paket (ADR-0021).
 *
 * <p>{@code publishedAt} null berarti versi kerja yang masih boleh diubah; terisi berarti beku
 * — item di dalamnya tidak lagi ditambah, dibuang, atau dipindah. Setiap Paket punya paling
 * banyak satu versi kerja (index parsial {@code paket_version_single_draft}). Isinya bukan di
 * sini melainkan di {@link PaketItemEntity}; versi hanya wadah yang memberi nomor.
 *
 * <p>{@code clientId} disalin dari Paket dan dikunci FK komposit {@code paket_version_same_owner}:
 * ia ada semata-mata supaya {@code paket_item} bisa menegakkan batas tenant di database (TC-36).
 */
@Entity
@Table(name = "paket_version")
public class PaketVersionEntity {

    @Id
    private UUID id;

    @Column(name = "paket_id", nullable = false)
    private UUID paketId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false)
    private int nomor;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PaketVersionEntity() {
    }

    private PaketVersionEntity(PaketEntity paket, int nomor, UUID createdBy) {
        this.id = UuidV7.randomUuid();
        this.paketId = paket.getId();
        this.clientId = paket.getClientId();
        this.nomor = nomor;
        this.createdBy = createdBy;
    }

    /** Versi kerja baru untuk sebuah Paket. */
    public static PaketVersionEntity draft(PaketEntity paket, int nomor, UUID createdBy) {
        return new PaketVersionEntity(paket, nomor, createdBy);
    }

    /** Membekukan versi; idempoten. */
    public void publish(OffsetDateTime now) {
        if (publishedAt == null) {
            publishedAt = now;
        }
    }

    /** Ditandai saat versi terbit berikutnya lahir; penanda informasi, bukan gerbang. */
    public void supersede(OffsetDateTime now) {
        if (supersededAt == null) {
            supersededAt = now;
        }
    }

    public boolean isDraft() {
        return publishedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaketId() {
        return paketId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public int getNomor() {
        return nomor;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getSupersededAt() {
        return supersededAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
