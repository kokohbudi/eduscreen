package com.eduscreen.app.modules.identity.service;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.identity.port.out.CreateUserCommand;
import com.eduscreen.app.modules.identity.port.out.IdentityProviderPort;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Pembuatan, pengubahan, dan penonaktifan akun di dalam satu Client.
 *
 * <p>Menonaktifkan akun <b>tidak</b> menghapus riwayat pengerjaannya (BR-U03). Nilai seorang
 * Siswa yang pindah sekolah tetap harus bisa ditunjukkan tahun berikutnya, jadi statusnya yang
 * berubah, bukan barisnya yang hilang.
 */
@Service
public class UserManagementService {

    private final AppUserRepository users;
    private final IdentityProviderPort identityProvider;
    private final InvitationService invitations;

    public UserManagementService(AppUserRepository users,
                                 IdentityProviderPort identityProvider,
                                 InvitationService invitations) {
        this.users = users;
        this.identityProvider = identityProvider;
        this.invitations = invitations;
    }

    @Transactional(readOnly = true)
    public Page<AppUserEntity> list(UUID clientId, UserRole role, Pageable pageable) {
        return role == null
                ? users.findByClientId(clientId, pageable)
                : users.findByClientIdAndRole(clientId, role, pageable);
    }

    @Transactional(readOnly = true)
    public AppUserEntity require(UUID id, UUID clientId) {
        return users.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan"));
    }

    /** Akun lahir berstatus INVITED; ia menjadi ACTIVE saat pemiliknya menetapkan password. */
    @Transactional
    public AppUserEntity create(UUID clientId, String email, String fullName, UserRole role) {
        String normalized = AppUserEntity.normalizeEmail(email);
        if (normalized == null || normalized.isBlank() || !normalized.contains("@")) {
            throw new IllegalArgumentException("Alamat email tidak sah");
        }
        if (users.existsByEmail(normalized)) {
            throw new IllegalArgumentException("Alamat email sudah terpakai");
        }
        AppUserEntity user = users.save(new AppUserEntity(clientId, normalized, fullName, role));
        identityProvider.createUser(new CreateUserCommand(user.getEmail(), user.getFullName()));
        invitations.invite(user);
        return user;
    }

    @Transactional
    public AppUserEntity update(UUID id, UUID clientId, String fullName, UserRole role) {
        AppUserEntity user = require(id, clientId);
        user.setFullName(fullName);
        user.setRole(role);
        return users.save(user);
    }

    /** Riwayat Session dan Result tetap utuh; yang hilang hanya kemampuan login (BR-U03). */
    @Transactional
    public AppUserEntity deactivate(UUID id, UUID clientId) {
        AppUserEntity user = require(id, clientId);
        user.setStatus(UserStatus.DEACTIVATED);
        return users.save(user);
    }

    @Transactional
    public void reinvite(UUID id, UUID clientId) {
        AppUserEntity user = require(id, clientId);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Akun nonaktif tidak bisa diundang ulang");
        }
        invitations.invite(user);
    }
}
