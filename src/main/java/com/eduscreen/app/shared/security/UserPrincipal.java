package com.eduscreen.app.shared.security;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pengguna yang sedang login.
 *
 * <p>Membawa {@code clientId} karena setiap query yang menyentuh data milik Client menyaring
 * kolom itu secara eksplisit (TC-08, TC-36). Batas tenant harus tersedia di setiap lapisan
 * tanpa perlu membaca ulang database.
 *
 * <p>{@code clientId} bernilai null hanya untuk Eduscreen Admin, satu-satunya peran yang
 * berdiri di luar Client mana pun.
 */
public record UserPrincipal(
        UUID userId,
        UUID clientId,
        String email,
        String fullName,
        UserRole role
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Kredensial tidak pernah tersimpan di aplikasi; pemeriksaannya milik
     * {@code IdentityProviderPort} (TC-06).
     */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean isEduscreenAdmin() {
        return role == UserRole.EDUSCREEN_ADMIN;
    }

    /**
     * Batas tenant untuk query. Memanggil ini pada Eduscreen Admin adalah kesalahan
     * pemrograman: ia tidak punya Client, dan setiap jalur yang membutuhkannya harus
     * menanganinya secara eksplisit.
     */
    public UUID requireClientId() {
        if (clientId == null) {
            throw new IllegalStateException(
                    "Eduscreen Admin tidak terikat Client; jalur ini membutuhkan batas tenant");
        }
        return clientId;
    }
}
