package com.eduscreen.app.modules.identity.port.out;

/**
 * Satu-satunya jalan aplikasi menyentuh kredensial.
 *
 * <p>Seluruh autentikasi dan manajemen kredensial berada di balik interface ini agar migrasi ke
 * Keycloak tidak menyentuh satu baris pun di layer {@code service} (ADR-0008, TC-07).
 *
 * <p>Bentuk port ini dirancang dari kebutuhan aplikasi, bukan dari API Keycloak. Bila kelak
 * terbukti ada ketidakcocokan, yang menyesuaikan adalah adapter, bukan port.
 */
public interface IdentityProviderPort {

    /**
     * @return true bila kredensial cocok. Pemanggil tidak boleh membedakan sebab kegagalan;
     *         akun tidak ada dan password salah harus menghasilkan respons yang sama.
     */
    boolean authenticate(String username, String rawPassword);

    UserIdentity createUser(CreateUserCommand command);

    void updatePassword(String userId, String newPassword);
}
