package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Satu tabel untuk empat peran.
 *
 * <p>Kredensial tidak disimpan di sini. Autentikasi hidup di balik
 * {@code IdentityProviderPort} sehingga migrasi ke Keycloak tidak menyentuh inti bisnis
 * (TC-06, TC-07, ADR-0008).
 */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    private UUID id;

    /** Null hanya untuk EDUSCREEN_ADMIN; ditegakkan check constraint di V1. */
    @Column(name = "client_id")
    private UUID clientId;

    /** Selalu huruf kecil, agar keunikan tidak bisa dilewati dengan mengubah kapitalisasi. */
    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.INVITED;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AppUserEntity() {
    }

    public AppUserEntity(UUID clientId, String email, String fullName, UserRole role) {
        if (role == UserRole.EDUSCREEN_ADMIN ? clientId != null : clientId == null) {
            throw new IllegalArgumentException(
                    "Hanya EDUSCREEN_ADMIN yang berdiri tanpa Client; peran lain wajib punya clientId");
        }
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.email = normalizeEmail(email);
        this.fullName = fullName;
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
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
