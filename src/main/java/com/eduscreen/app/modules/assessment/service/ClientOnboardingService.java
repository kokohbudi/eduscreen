package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Onboarding satu Client baru: pembuatan Client, akun Client Admin pertama, dan penyalinan
 * paket master yang dipilih dalam satu langkah (FR-020, §6.1 business-rules).
 */
@Service
public class ClientOnboardingService {

    /**
     * Indonesia terbagi tiga zona waktu; hanya tiga ini yang berarti bagi jadwal Assignment
     * (BR-T01, BR-T02). Zona lain tidak punya sekolah yang dilayani sistem ini.
     */
    private static final Set<String> ALLOWED_TIMEZONES = Set.of("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura");

    private final ClientRepository clients;
    private final UserManagementService userManagement;
    private final ContentAdoptionService adoption;

    public ClientOnboardingService(ClientRepository clients,
                                   UserManagementService userManagement,
                                   ContentAdoptionService adoption) {
        this.clients = clients;
        this.userManagement = userManagement;
        this.adoption = adoption;
    }

    public record OnboardingRequest(String name, String timezone, String adminEmail,
                                    String adminFullName, List<UUID> exerciseIds) {}

    /**
     * BR-O01: onboarding sengaja TIDAK membuat Ruangan maupun akun Siswa. Itu keputusan desain,
     * bukan langkah yang terlewat — Client Admin yang baru login mengatur kelas dan mengundang
     * Siswanya sendiri, karena hanya sekolah itu yang tahu struktur kelasnya.
     */
    @Transactional
    public ClientEntity onboard(OnboardingRequest request) {
        if (!ALLOWED_TIMEZONES.contains(request.timezone())) {
            throw new IllegalArgumentException(
                    "Zona waktu tidak didukung: " + request.timezone()
                            + " (hanya Asia/Jakarta, Asia/Makassar, Asia/Jayapura)");
        }

        ClientEntity client = clients.save(new ClientEntity(request.name(), ZoneId.of(request.timezone())));

        // create() sudah mengirim undangan lewat InvitationService; tidak perlu diulang di sini.
        AppUserEntity admin = userManagement.create(
                client.getId(), request.adminEmail(), request.adminFullName(), UserRole.CLIENT_ADMIN);

        if (request.exerciseIds() != null && !request.exerciseIds().isEmpty()) {
            // Salinan paket master lahir milik Client Admin yang baru dibuat: dialah yang akan
            // menyusun dan menerbitkannya lebih lanjut.
            adoption.adoptExercises(client.getId(), request.exerciseIds(), admin.getId());
        }

        return client;
    }
}
