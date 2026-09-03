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
 * Akses satu sekolah ke satu Paket master: versi mana yang dibaca, sampai kapan (ADR-0021).
 *
 * <p>Ini pengganti adopsi salinan (ADR-0001): satu baris penanda, nol baris soal. Sekolah membaca
 * soal master langsung lewat {@code versionId}; pindah versi = mengganti pointer ini, dan
 * Exercise yang sudah dirakit tidak tersentuh karena itemnya menunjuk soal, bukan versi.
 */
@Entity
@Table(name = "paket_access")
public class PaketAccessEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "paket_id", nullable = false)
    private UUID paketId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private OffsetDateTime grantedAt;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected PaketAccessEntity() {
    }

    private PaketAccessEntity(UUID clientId, PaketVersionEntity version, OffsetDateTime validUntil, UUID grantedBy) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.paketId = version.getPaketId();
        this.versionId = version.getId();
        this.validUntil = validUntil;
        this.grantedBy = grantedBy;
    }

    public static PaketAccessEntity grant(UUID clientId, PaketVersionEntity version,
                                          OffsetDateTime validUntil, UUID grantedBy) {
        return new PaketAccessEntity(clientId, version, validUntil, grantedBy);
    }

    /** Masih boleh dipakai untuk pemakaian BARU: belum dicabut dan belum lewat batasnya. */
    public boolean isUsable(OffsetDateTime now) {
        return revokedAt == null && (validUntil == null || validUntil.isAfter(now));
    }

    public void revoke(OffsetDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void extend(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    /** Pindah ke versi lain dari Paket yang sama; pemanggil yang memastikan versinya terbit. */
    public void switchTo(PaketVersionEntity version) {
        if (!version.getPaketId().equals(paketId)) {
            throw new IllegalArgumentException("Versi bukan milik Paket ini");
        }
        this.versionId = version.getId();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getPaketId() {
        return paketId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public OffsetDateTime getGrantedAt() {
        return grantedAt;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
