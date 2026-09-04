package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.shared.security.EduscreenAuthenticationProvider;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Suspensi Client ditegakkan di satu titik: jalur login (BR-O09).
 *
 * <p>Yang dibuktikan di sini bukan hanya bahwa pintu tertutup, tetapi bahwa ia tertutup dengan
 * pesan yang sama persis dengan password salah. Pesan yang berbeda akan mengubah formulir login
 * menjadi alat memeriksa sekolah mana yang sedang menunggak.
 */
class ClientSuspensionIT extends PostgresTestBase {

    /** Password tunggal adapter dummy (ADR-0016); hanya berlaku di profil local/demo. */
    private static final String PASSWORD = "password123";
    private static final String PESAN_SERAGAM = "Email atau password salah";

    @Autowired EduscreenAuthenticationProvider provider;
    @Autowired ClientDirectoryService clients;
    @Autowired TestData data;

    @Test
    @DisplayName("AC-O04: Client SUSPENDED menolak login Client Admin, Guru, dan Siswa-nya")
    void clientSuspendedMenolakSeluruhPenggunanya() {
        Tenants tenants = data.twoTenants();
        clients.changeStatus(tenants.a().client().getId(), ClientStatus.SUSPENDED);

        for (AppUserEntity ditolak : java.util.List.of(
                tenants.a().admin(), tenants.a().guru(), tenants.a().siswa())) {
            BadCredentialsException e = assertThrows(BadCredentialsException.class,
                    () -> login(ditolak));
            // Pesan yang berbeda dari "password salah" akan membocorkan status sekolah kepada
            // siapa pun yang mencoba satu alamat email.
            assertEquals(PESAN_SERAGAM, e.getMessage());
        }
    }

    @Test
    @DisplayName("AC-O04: suspensi satu Client tidak menyentuh Client lain maupun Eduscreen Admin")
    void suspensiTidakMerembetKeLuarClientnya() {
        Tenants tenants = data.twoTenants();
        clients.changeStatus(tenants.a().client().getId(), ClientStatus.SUSPENDED);

        assertNotNull(login(tenants.b().guru()));
        // Eduscreen Admin tidak bernaung di Client mana pun (clientId null), jadi tidak ada
        // status Client yang bisa mengunci dirinya.
        assertNotNull(login(data.eduscreenAdmin()));
    }

    @Test
    @DisplayName("AC-O04: memulihkan Client ke ACTIVE mengembalikan login penggunanya")
    void memulihkanClientMengembalikanLogin() {
        Tenants tenants = data.twoTenants();
        clients.changeStatus(tenants.a().client().getId(), ClientStatus.SUSPENDED);
        assertThrows(BadCredentialsException.class, () -> login(tenants.a().guru()));

        clients.changeStatus(tenants.a().client().getId(), ClientStatus.ACTIVE);

        assertNotNull(login(tenants.a().guru()));
    }

    private Authentication login(AppUserEntity user) {
        return provider.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), PASSWORD));
    }
}
