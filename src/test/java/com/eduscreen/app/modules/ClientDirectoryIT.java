package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Identitas Client dan akun Client Admin-nya, dikelola Eduscreen Admin (FR-083, FR-085). */
class ClientDirectoryIT extends PostgresTestBase {

    @Autowired ClientDirectoryService clients;
    @Autowired UserManagementService userManagement;
    @Autowired AssignmentRepository assignments;
    @Autowired TestData data;

    @Test
    @DisplayName("AC-O03: nama dan zona Client bisa diperbaiki tanpa menggeser tanggal tersimpan")
    void namaDanZonaBisaDiperbaiki() {
        Tenants tenants = data.twoTenants();
        UUID clientId = tenants.a().client().getId();
        OffsetDateTime sebelum = assignments.findById(tenants.a().assignment().getId())
                .orElseThrow().getExpiresAt();

        clients.rename(clientId, "SMP Nusantara 1");
        clients.changeTimezone(clientId, "Asia/Makassar");

        ClientEntity ubah = clients.require(clientId);
        assertEquals("SMP Nusantara 1", ubah.getName());
        assertEquals("Asia/Makassar", ubah.getTimezone().getId());
        // BR-O08: zona baru mengubah penafsiran, bukan datanya. Menggeser tanggal diam-diam akan
        // mengubah tenggat yang sudah diumumkan ke Siswa.
        assertEquals(sebelum, assignments.findById(tenants.a().assignment().getId())
                .orElseThrow().getExpiresAt());
    }

    @Test
    @DisplayName("AC-O03: zona di luar tiga zona Indonesia ditolak, nama kosong juga")
    void zonaAsingDanNamaKosongDitolak() {
        UUID clientId = data.client("SD Zona").getId();

        assertThrows(IllegalArgumentException.class,
                () -> clients.changeTimezone(clientId, "Europe/Berlin"));
        assertThrows(IllegalArgumentException.class, () -> clients.rename(clientId, "  "));
    }

    @Test
    @DisplayName("AC-O05: Client Admin terakhir yang masih bisa masuk tidak boleh dinonaktifkan")
    void clientAdminTerakhirTidakBisaDinonaktifkan() {
        Tenants tenants = data.twoTenants();
        UUID clientId = tenants.a().client().getId();
        AppUserEntity pertama = tenants.a().admin();

        assertThrows(IllegalStateException.class,
                () -> clients.deactivateClientAdmin(clientId, pertama.getId()));

        AppUserEntity kedua = userManagement.create(
                clientId, data.uniqueEmail("admin-kedua"), "Admin Kedua", UserRole.CLIENT_ADMIN);

        clients.deactivateClientAdmin(clientId, pertama.getId());

        assertEquals(UserStatus.DEACTIVATED,
                userManagement.require(pertama.getId(), clientId).getStatus());
        // Undangan yang belum ditebus tetap jalan masuk yang sah, jadi giliran akun kedua yang
        // sekarang dijaga BR-O10.
        assertEquals(UserStatus.INVITED, userManagement.require(kedua.getId(), clientId).getStatus());
        assertThrows(IllegalStateException.class,
                () -> clients.deactivateClientAdmin(clientId, kedua.getId()));
    }
}
