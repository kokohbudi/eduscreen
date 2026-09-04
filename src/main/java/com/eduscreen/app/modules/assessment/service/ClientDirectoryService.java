package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Client sebagai entitas: namanya, zonanya, statusnya, dan akun Client Admin yang mengurusnya.
 *
 * <p>Zona Client adalah otoritas setiap tampilan waktu dan setiap batas akhir yang diketik
 * manusia. "Minggu 23:59" berarti 23:59 di zona Client, bukan di zona perangkat Guru yang
 * kebetulan sedang bertugas dari luar kota (BR-T02, AC-T06). Indonesia punya tiga zona, jadi ini
 * bukan kasus tepi yang jarang.
 *
 * <p>Batas kelas ini sengaja sempit. Yang boleh disentuh dari sini hanya data Client sebagai
 * entitas dan akun berperan {@code CLIENT_ADMIN}; Ruangan, akun Guru dan Siswa, serta seluruh
 * data pemakaian sekolah tetap tertutup bagi Eduscreen Admin (BR-P04, FR-085). Menambahkan
 * pembacaan data operasional ke sini akan membuka jalan yang ADR-0015 nyatakan hanya boleh
 * lewat akses dukungan.
 */
@Service
public class ClientDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(ClientDirectoryService.class);

    private final ClientRepository clients;
    private final AppUserRepository users;
    private final UserManagementService userManagement;

    public ClientDirectoryService(ClientRepository clients,
                                  AppUserRepository users,
                                  UserManagementService userManagement) {
        this.clients = clients;
        this.users = users;
        this.userManagement = userManagement;
    }

    @Transactional(readOnly = true)
    public ClientEntity require(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID clientId) {
        return require(clientId).getTimezone();
    }

    @Transactional(readOnly = true)
    public List<ClientEntity> all() {
        return clients.findAll();
    }

    /** Client Admin sebuah Client. Peran lain tidak pernah terbaca lewat jalur ini (BR-P04). */
    @Transactional(readOnly = true)
    public List<AppUserEntity> clientAdmins(UUID clientId) {
        return users.findByClientIdAndRole(clientId, UserRole.CLIENT_ADMIN,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /** BR-O07: nama adalah label administratif; tidak ada data yang merujuknya. */
    @Transactional
    public ClientEntity rename(UUID clientId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Client tidak boleh kosong");
        }
        ClientEntity client = require(clientId);
        client.setName(name.trim());
        log.info("Nama Client diubah clientId={}", clientId);
        return clients.save(client);
    }

    /**
     * BR-O08: zona baru berlaku untuk penafsiran, bukan untuk data. Tidak ada Expiration Date
     * yang digeser — layar yang memanggil ini wajib sudah menyatakan konsekuensinya.
     */
    @Transactional
    public ClientEntity changeTimezone(UUID clientId, String timezone) {
        ClientEntity client = require(clientId);
        client.setTimezone(ClientEntity.requireSupportedTimezone(timezone));
        log.info("Zona waktu Client diubah clientId={} zona={}", clientId, timezone);
        return clients.save(client);
    }

    /**
     * BR-O09: {@code SUSPENDED} menutup pintu masuk seluruh pengguna Client, ditegakkan di
     * {@code EduscreenAuthenticationProvider}. Session yang sudah berjalan dibiarkan habis
     * sendiri.
     */
    @Transactional
    public ClientEntity changeStatus(UUID clientId, ClientStatus status) {
        ClientEntity client = require(clientId);
        client.setStatus(status);
        log.info("Status Client diubah clientId={} status={}", clientId, status);
        return clients.save(client);
    }

    /**
     * BR-O10: menonaktifkan Client Admin terakhir yang masih bisa masuk mengunci sekolah dari
     * akunnya sendiri. Aturannya duduk bersama aksinya supaya tidak ada jalan memanggil yang satu
     * tanpa yang lain.
     */
    @Transactional
    public void deactivateClientAdmin(UUID clientId, UUID userId) {
        // require() di sini yang membuat userId milik Client lain menghasilkan 404 identik (TC-09).
        AppUserEntity admin = userManagement.require(userId, clientId);
        if (admin.getRole() != UserRole.CLIENT_ADMIN) {
            throw new ResourceNotFoundException("Pengguna tidak ditemukan");
        }
        // INVITED ikut dihitung: undangan yang belum ditebus tetap jalan masuk yang sah.
        long bisaMasuk = users.countByClientIdAndRoleAndStatusNot(
                clientId, UserRole.CLIENT_ADMIN, UserStatus.DEACTIVATED);
        if (admin.getStatus() != UserStatus.DEACTIVATED && bisaMasuk <= 1) {
            throw new IllegalStateException(
                    "Client harus punya minimal satu Client Admin aktif");
        }
        userManagement.deactivate(userId, clientId);
        log.info("Client Admin dinonaktifkan clientId={} userId={}", clientId, userId);
    }
}
