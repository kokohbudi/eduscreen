package com.eduscreen.app.shared.security;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.identity.port.out.IdentityProviderPort;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Menyatukan dua sumber yang sengaja dipisah: <b>siapa</b> pengguna itu (tabel
 * {@code app_user}, milik kami) dan <b>apakah kredensialnya benar</b>
 * ({@code IdentityProviderPort}, milik pihak ketiga kelak).
 *
 * <p>Pemisahan ini yang membuat migrasi ke Keycloak tidak menyentuh inti bisnis (ADR-0008).
 *
 * <p>Seluruh kegagalan menghasilkan {@link BadCredentialsException} yang sama — akun tidak ada,
 * password salah, akun nonaktif, dan Client yang sedang {@code SUSPENDED} tidak boleh dibedakan.
 * Membedakannya mengubah formulir login menjadi alat memeriksa keberadaan email, dan alat
 * memeriksa sekolah mana yang sedang bermasalah (BR-O09).
 */
@Component
public class EduscreenAuthenticationProvider implements AuthenticationProvider {

    private final AppUserRepository users;
    private final ClientRepository clients;
    private final IdentityProviderPort identityProvider;
    private final LoginRateLimiter rateLimiter;

    public EduscreenAuthenticationProvider(AppUserRepository users,
                                           ClientRepository clients,
                                           IdentityProviderPort identityProvider,
                                           LoginRateLimiter rateLimiter) {
        this.users = users;
        this.clients = clients;
        this.identityProvider = identityProvider;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = AppUserEntity.normalizeEmail(authentication.getName());
        String rawPassword = String.valueOf(authentication.getCredentials());
        String ip = currentIpAddress(authentication);

        if (rateLimiter.isBlocked(email, ip)) {
            // Pesannya tetap seragam: penyerang tidak boleh tahu bahwa akun ini nyata dan
            // sedang terkunci.
            throw new BadCredentialsException("Email atau password salah");
        }

        applyPenaltyDelay(email, ip);

        Optional<AppUserEntity> found = users.findByEmail(email);
        boolean credentialsValid = identityProvider.authenticate(email, rawPassword);

        // Client yang disuspend menutup pintu masuk seluruh penggunanya (BR-O09). clientId null
        // hanya benar untuk Eduscreen Admin, yang tidak bernaung di Client mana pun.
        boolean clientActive = found.isPresent()
                && (found.get().getClientId() == null
                    || clients.existsByIdAndStatus(found.get().getClientId(), ClientStatus.ACTIVE));

        // Pemeriksaan kredensial tetap dijalankan meski akun tidak ada, agar waktu tanggap
        // tidak membocorkan keberadaan email.
        if (found.isEmpty() || !credentialsValid || found.get().getStatus() != UserStatus.ACTIVE
                || !clientActive) {
            rateLimiter.recordFailure(email, ip);
            throw new BadCredentialsException("Email atau password salah");
        }

        AppUserEntity user = found.get();
        rateLimiter.recordSuccess(email, ip);

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getClientId(), user.getEmail(), user.getFullName(), user.getRole());

        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void applyPenaltyDelay(String email, String ip) {
        long millis = rateLimiter.penaltyDelay(email, ip).toMillis();
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String currentIpAddress(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof org.springframework.security.web.authentication.WebAuthenticationDetails web) {
            return web.getRemoteAddress();
        }
        return "unknown";
    }
}
