package com.eduscreen.app.modules.identity.port.out;

/**
 * Identitas seorang pengguna menurut penyedia identitas.
 *
 * <p>Sengaja tipis: hanya pengenal dan email. Data profil lain milik {@code app_user}, tabel
 * kami sendiri. Yang ada di balik port hanyalah hal yang dikendalikan pihak ketiga.
 */
public record UserIdentity(String externalId, String email) {
}
