package com.eduscreen.app.modules.identity.service;

import com.eduscreen.app.modules.assessment.domain.InvitationPurpose;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.UserInvitationEntity;
import com.eduscreen.app.modules.assessment.repository.UserInvitationRepository;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.identity.port.out.IdentityProviderPort;
import com.eduscreen.app.modules.notification.port.out.NotificationPort;
import com.eduscreen.app.shared.domain.ClientClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Undangan akun dan reset password (BR-U04).
 *
 * <p>Tanpa jalur ini setiap password lupa menjadi tiket ke Client Admin, dan Client Admin yang
 * kelelahan akan menyetel ulang password lewat kanal yang tidak bisa diaudit.
 *
 * <p>Yang disimpan adalah <b>hash</b> token, bukan tokennya. Bocornya isi tabel undangan tidak
 * boleh cukup untuk mengambil alih akun (TC-06). Token mentah hanya ada satu kali: di tautan
 * yang dikirim lewat {@link NotificationPort}.
 */
@Service
public class InvitationService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final Duration RESET_TTL = Duration.ofHours(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserInvitationRepository invitations;
    private final AppUserRepository users;
    private final NotificationPort notifications;
    private final IdentityProviderPort identityProvider;
    private final ClientClock clock;
    private final String baseUrl;

    public InvitationService(UserInvitationRepository invitations,
                             AppUserRepository users,
                             NotificationPort notifications,
                             IdentityProviderPort identityProvider,
                             ClientClock clock,
                             @Value("${eduscreen.base-url:http://localhost:8080}") String baseUrl) {
        this.invitations = invitations;
        this.users = users;
        this.notifications = notifications;
        this.identityProvider = identityProvider;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /** Mengirim undangan penetapan password ke akun yang baru dibuat atau yang diundang ulang. */
    @Transactional
    public void invite(AppUserEntity user) {
        String token = issue(user, InvitationPurpose.INVITATION, INVITATION_TTL);
        notifications.sendInvitation(user.getEmail(), user.getFullName(), baseUrl + "/undangan/" + token);
    }

    /**
     * Memulai reset password.
     *
     * <p>Diam saja bila akunnya tidak ada. Pemanggilnya tetap menjawab konfirmasi yang sama,
     * agar endpoint ini tidak bisa dipakai memeriksa keberadaan sebuah alamat email.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(AppUserEntity.normalizeEmail(email))
                .filter(user -> user.getStatus() != UserStatus.DEACTIVATED)
                .ifPresent(user -> {
                    String token = issue(user, InvitationPurpose.PASSWORD_RESET, RESET_TTL);
                    notifications.sendPasswordReset(
                            user.getEmail(), user.getFullName(), baseUrl + "/reset/" + token);
                });
    }

    /** @return akun pemilik token bila token masih sah; kosong bila tidak sah atau kedaluwarsa. */
    @Transactional(readOnly = true)
    public Optional<AppUserEntity> resolve(String rawToken, InvitationPurpose purpose) {
        return invitations.findByTokenHash(hash(rawToken))
                .filter(invitation -> invitation.getPurpose() == purpose)
                .filter(invitation -> invitation.isUsable(clock.now()))
                .flatMap(invitation -> users.findById(invitation.getUserId()));
    }

    /**
     * Menetapkan password lewat token, lalu menghanguskan tokennya.
     *
     * <p>Password mentah diteruskan ke {@link IdentityProviderPort} dan tidak pernah menyentuh
     * tabel aplikasi maupun log (TC-06, TC-07).
     */
    @Transactional
    public boolean redeem(String rawToken, InvitationPurpose purpose, String newPassword) {
        Optional<UserInvitationEntity> found = invitations.findByTokenHash(hash(rawToken))
                .filter(invitation -> invitation.getPurpose() == purpose)
                .filter(invitation -> invitation.isUsable(clock.now()));
        if (found.isEmpty()) {
            return false;
        }
        UserInvitationEntity invitation = found.get();
        AppUserEntity user = users.findById(invitation.getUserId()).orElseThrow();

        identityProvider.updatePassword(user.getId().toString(), newPassword);
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
            users.save(user);
        }
        invitation.markUsed(clock.now());
        invitations.save(invitation);
        return true;
    }

    private String issue(AppUserEntity user, InvitationPurpose purpose, Duration ttl) {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        invitations.save(new UserInvitationEntity(
                user.getClientId(), user.getId(), hash(token), purpose, clock.now().plus(ttl)));
        return token;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
