package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** Tenant. Akar isolasi data; setiap tabel milik Client menunjuk ke sini. */
@Entity
@Table(name = "client")
public class ClientEntity {

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
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
