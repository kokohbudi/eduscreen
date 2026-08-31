package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface RuanganMemberRepository extends JpaRepository<RuanganMemberEntity, UUID> {

    List<RuanganMemberEntity> findByClientIdAndRuanganIdAndMemberRole(
            UUID clientId, UUID ruanganId, MemberRole memberRole);

    List<RuanganMemberEntity> findByClientIdAndRuanganId(UUID clientId, UUID ruanganId);

    /** Dasar portal Siswa: seluruh Ruangan yang diikuti, lintas kelas dan grup bimbel. */
    List<RuanganMemberEntity> findByClientIdAndUserId(UUID clientId, UUID userId);

    boolean existsByRuanganIdAndUserId(UUID ruanganId, UUID userId);

    /** Dipakai penegakan izin: Guru hanya boleh menyentuh Ruangan yang ditugaskan padanya. */
    boolean existsByRuanganIdAndUserIdAndMemberRole(UUID ruanganId, UUID userId, MemberRole memberRole);

    void deleteByRuanganIdAndUserId(UUID ruanganId, UUID userId);
}
