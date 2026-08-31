package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
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
 * Keanggotaan Ruangan, many-to-many di kedua sisi (FR-008).
 *
 * <p>Satu Siswa boleh berada di `Kelas 4B` dan `Bimbel Intensif SBMPTN Group B` sekaligus —
 * keduanya sumbu yang berbeda, bukan pilihan yang saling meniadakan.
 */
@Entity
@Table(name = "ruangan_member")
public class RuanganMemberEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "ruangan_id", nullable = false)
    private UUID ruanganId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    private MemberRole memberRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected RuanganMemberEntity() {
    }

    public RuanganMemberEntity(UUID clientId, UUID ruanganId, UUID userId, MemberRole memberRole) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.ruanganId = ruanganId;
        this.userId = userId;
        this.memberRole = memberRole;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getRuanganId() {
        return ruanganId;
    }

    public UUID getUserId() {
        return userId;
    }

    public MemberRole getMemberRole() {
        return memberRole;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
