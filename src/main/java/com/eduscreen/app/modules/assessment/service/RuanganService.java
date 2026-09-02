package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RuanganStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganMemberEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganMemberRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ruangan dan keanggotaannya.
 *
 * <p>Tahun ajaran tidak menjadi entitas tersendiri: Ruangan diarsipkan di akhir periode dan
 * yang baru dibuat. Ruangan {@code ARCHIVED} bersifat read-only — tidak menerima anggota
 * maupun Assignment baru — sementara seluruh riwayat Result-nya tetap terbaca (BR-U02).
 */
@Service
public class RuanganService {

    private final RuanganRepository ruangan;
    private final RuanganMemberRepository members;
    private final AppUserRepository users;

    public RuanganService(RuanganRepository ruangan,
                          RuanganMemberRepository members,
                          AppUserRepository users) {
        this.ruangan = ruangan;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RuanganEntity> list(UUID clientId) {
        return ruangan.findByClientIdOrderByNameAsc(clientId);
    }

    @Transactional(readOnly = true)
    public List<RuanganEntity> listActive(UUID clientId) {
        return ruangan.findByClientIdAndStatusOrderByNameAsc(clientId, RuanganStatus.ACTIVE);
    }

    /** Batas tenant masuk ke klausa query; Ruangan milik Client lain menghasilkan 404 (TC-08). */
    @Transactional(readOnly = true)
    public RuanganEntity require(UUID id, UUID clientId) {
        return ruangan.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruangan tidak ditemukan"));
    }

    @Transactional
    public RuanganEntity create(UUID clientId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Ruangan wajib diisi");
        }
        return ruangan.save(new RuanganEntity(clientId, name.trim()));
    }

    @Transactional(readOnly = true)
    public List<AppUserEntity> membersOf(UUID ruanganId, UUID clientId, MemberRole role) {
        require(ruanganId, clientId);
        List<UUID> userIds = members.findByClientIdAndRuanganIdAndMemberRole(clientId, ruanganId, role)
                .stream().map(RuanganMemberEntity::getUserId).toList();
        return userIds.isEmpty()
                ? List.of()
                : users.findByClientIdAndIdIn(clientId, userIds).stream()
                        .sorted(Comparator.comparing(AppUserEntity::getFullName))
                        .toList();
    }

    /** Ruangan yang diikuti seorang pengguna; dasar portal Siswa lintas kelas dan bimbel. */
    @Transactional(readOnly = true)
    public List<RuanganEntity> ruanganOf(UUID clientId, UUID userId) {
        List<UUID> ids = members.findByClientIdAndUserId(clientId, userId)
                .stream().map(RuanganMemberEntity::getRuanganId).toList();
        return ruangan.findByClientIdOrderByNameAsc(clientId).stream()
                .filter(r -> ids.contains(r.getId()))
                .toList();
    }

    @Transactional
    public void addMembers(UUID ruanganId, UUID clientId, List<UUID> userIds, MemberRole role) {
        RuanganEntity target = require(ruanganId, clientId);
        if (target.isArchived()) {
            throw new IllegalStateException("Ruangan terarsip tidak menerima anggota baru");
        }
        // Pengguna dimuat dengan penyaringan clientId, sehingga akun Client lain tidak pernah
        // bisa diselundupkan lewat daftar id (TC-08).
        for (AppUserEntity user : users.findByClientIdAndIdIn(clientId, userIds)) {
            if (!members.existsByRuanganIdAndUserId(ruanganId, user.getId())) {
                members.save(new RuanganMemberEntity(clientId, ruanganId, user.getId(), role));
            }
        }
    }

    @Transactional
    public void removeMember(UUID ruanganId, UUID clientId, UUID userId) {
        require(ruanganId, clientId);
        members.deleteByRuanganIdAndUserId(ruanganId, userId);
    }

    @Transactional
    public RuanganEntity archive(UUID ruanganId, UUID clientId) {
        RuanganEntity target = require(ruanganId, clientId);
        target.archive();
        return ruangan.save(target);
    }

    /** Penegakan BR-P01 dan BR-M01: Guru hanya menyentuh Ruangan tempat ia ditugaskan. */
    @Transactional(readOnly = true)
    public boolean isAssignedGuru(UUID ruanganId, UUID guruId) {
        return members.existsByRuanganIdAndUserIdAndMemberRole(ruanganId, guruId, MemberRole.GURU);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID ruanganId, UUID userId) {
        return members.existsByRuanganIdAndUserId(ruanganId, userId);
    }
}
