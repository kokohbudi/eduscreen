package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Onboarding satu Client baru: pembuatan Client, akun Client Admin pertama, dan pemberian
 * akses ke Paket master yang dipilih dalam satu langkah (FR-020, §6.1 business-rules, ADR-0021).
 */
@Service
public class ClientOnboardingService {

    private final ClientRepository clients;
    private final UserManagementService userManagement;
    private final PaketAccessService access;

    public ClientOnboardingService(ClientRepository clients,
                                   UserManagementService userManagement,
                                   PaketAccessService access) {
        this.clients = clients;
        this.userManagement = userManagement;
        this.access = access;
    }

    public record OnboardingRequest(String name, String timezone, String adminEmail,
                                    String adminFullName, List<UUID> paketIds) {}

    /**
     * BR-O01: onboarding sengaja TIDAK membuat Ruangan maupun akun Siswa. Itu keputusan desain,
     * bukan langkah yang terlewat — Client Admin yang baru login mengatur kelas dan mengundang
     * Siswanya sendiri, karena hanya sekolah itu yang tahu struktur kelasnya.
     */
    @Transactional
    public ClientEntity onboard(OnboardingRequest request) {
        ClientEntity client = clients.save(new ClientEntity(
                request.name(), ClientEntity.requireSupportedTimezone(request.timezone())));

        // create() sudah mengirim undangan lewat InvitationService; tidak perlu diulang di sini.
        AppUserEntity admin = userManagement.create(
                client.getId(), request.adminEmail(), request.adminFullName(), UserRole.CLIENT_ADMIN);

        if (request.paketIds() != null) {
            // Akses, bukan salinan (ADR-0021): sekolah membaca versi terbit terakhir tiap Paket
            // master yang dipilih; tanpa batas waktu, Eduscreen Admin bisa mengaturnya kemudian.
            for (UUID paketId : request.paketIds()) {
                access.grant(client.getId(), paketId, null, admin.getId());
            }
        }

        return client;
    }
}
