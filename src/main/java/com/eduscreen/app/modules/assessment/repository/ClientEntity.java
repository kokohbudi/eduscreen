package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Tenant. Akar isolasi data; setiap tabel milik Client menunjuk ke sini. */
@Entity
@Table(name = "client")
public class ClientEntity {

    /**
     * Indonesia terbagi tiga zona waktu; hanya tiga ini yang berarti bagi jadwal Assignment
     * (BR-T01, BR-T02). Daftarnya duduk di entity, bukan di service, agar onboarding dan
     * pengubahan zona belakangan (BR-O08) tidak bisa berbeda pendapat soal zona mana yang sah.
     */
    private static final Set<String> ZONA_DIDUKUNG =
            Set.of("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura");

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Satu zona per Client; seluruh batas akhir dan tampilan waktu memakainya (BR-T02). */
    @Column(nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ClientEntity() {
    }

    public ClientEntity(String name, ZoneId timezone) {
        this.id = UuidV7.randomUuid();
        this.name = name;
        this.timezone = timezone.getId();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ZoneId getTimezone() {
        return ZoneId.of(timezone);
    }

    /**
     * Mengubah zona <b>tidak</b> menggeser satu pun tanggal yang sudah tersimpan (BR-O08). Setiap
     * Expiration Date ditafsirkan ulang di zona baru, dan itu memang konsekuensi yang diinginkan:
     * memindahkan tanggalnya diam-diam akan mengubah tenggat yang sudah diumumkan ke Siswa.
     */
    public void setTimezone(ZoneId timezone) {
        this.timezone = requireSupportedTimezone(timezone.getId()).getId();
    }

    /** Melempar {@link IllegalArgumentException} (→ 400) bila zona di luar tiga zona Indonesia. */
    public static ZoneId requireSupportedTimezone(String timezone) {
        if (timezone == null || !ZONA_DIDUKUNG.contains(timezone)) {
            throw new IllegalArgumentException(
                    "Zona waktu tidak didukung: " + timezone
                            + " (hanya Asia/Jakarta, Asia/Makassar, Asia/Jayapura)");
        }
        return ZoneId.of(timezone);
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }



}
