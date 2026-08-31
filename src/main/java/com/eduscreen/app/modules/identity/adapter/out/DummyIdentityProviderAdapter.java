package com.eduscreen.app.modules.identity.adapter.out;

import com.eduscreen.app.modules.identity.port.out.CreateUserCommand;
import com.eduscreen.app.modules.identity.port.out.IdentityProviderPort;
import com.eduscreen.app.modules.identity.port.out.UserIdentity;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Adapter identity sementara sampai Keycloak terpasang (ADR-0016).
 *
 * <p><b>Ini pintu belakang berjalan.</b> Ia menerima satu password untuk semua akun: siapa pun
 * yang mengetahuinya masuk sebagai Siswa, Guru, atau Client Admin mana pun — membaca seluruh
 * bank soal dan mengubah nilai.
 *
 * <p>Karena itu ia dikurung <b>dua lapis</b> (TC-04):
 *
 * <ol>
 *   <li>{@code @Profile({"local", "demo"})} — Spring tidak membuatnya di profil lain.</li>
 *   <li>Pemeriksaan gagal-cepat di bawah — profil Spring bisa salah pasang, dan kegagalan
 *       diam-diam pada jalur autentikasi berarti seluruh platform terbuka tanpa ada yang
 *       menyadarinya.</li>
 * </ol>
 *
 * <p>Batas yang sebenarnya adalah <b>isi data</b>, bukan nama environment: server bernama
 * "demo" yang diisi data sekolah sungguhan telah menjadi produksi, apa pun labelnya (TC-34).
 */
@Component
@Profile({"local", "demo"})
public class DummyIdentityProviderAdapter implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(DummyIdentityProviderAdapter.class);
    private static final Set<String> ALLOWED_ENVS = Set.of("local", "demo");
    private static final String SHARED_PASSWORD = "password123";

    @PostConstruct
    void refuseToRunOutsideAllowedEnvs() {
        String env = System.getenv("EDUSCREEN_ENV");
        // Variabel yang tidak diset adalah kegagalan konfigurasi paling mungkin saat menyiapkan
        // server baru. Pada jalur autentikasi ia harus berbunyi keras, bukan diam.
        if (env == null || !ALLOWED_ENVS.contains(env)) {
            throw new IllegalStateException(
                    "DummyIdentityProviderAdapter aktif di environment '" + env
                            + "'. Menolak start. Hanya 'local' dan 'demo' yang diizinkan (TC-04, TC-34).");
        }
        log.warn("=== IDENTITY DUMMY AKTIF ({}) — SATU PASSWORD BERLAKU UNTUK SEMUA AKUN ===", env);
        log.warn("=== JANGAN PERNAH ISI ENVIRONMENT INI DENGAN DATA SISWA SUNGGUHAN (TC-34) ===");
    }

    @Override
    public boolean authenticate(String username, String rawPassword) {
        // Password mentah tidak pernah masuk log maupun pesan galat (TC-06).
        return SHARED_PASSWORD.equals(rawPassword);
    }

    @Override
    public UserIdentity createUser(CreateUserCommand command) {
        return new UserIdentity(UUID.randomUUID().toString(), command.email());
    }

    @Override
    public void updatePassword(String userId, String newPassword) {
        // Tidak ada penyimpan kredensial sampai Keycloak; perubahan password tidak berefek.
        log.warn("updatePassword diabaikan: adapter dummy tidak menyimpan kredensial (userId={})", userId);
    }
}
