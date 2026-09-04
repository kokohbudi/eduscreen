package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService.OnboardingRequest;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Portal Eduscreen Admin: daftar Client, detail dan manajemen tiap Client, dan konten master.
 *
 * <p>Ini satu-satunya peran yang berdiri di luar Client mana pun, dan karena itu satu-satunya
 * yang tidak boleh punya jalan membaca data operasional sebuah sekolah tanpa izin. Onboarding
 * dan pengelolaan katalog master ada di sini; membaca bank soal sebuah Client tidak.
 */
@Controller
public class EduscreenAdminController {

    private final ClientOnboardingService onboarding;
    private final ClientDirectoryService clients;
    private final UserManagementService userManagement;
    private final PaketRepository pakets;

    public EduscreenAdminController(ClientOnboardingService onboarding,
                                    ClientDirectoryService clients,
                                    UserManagementService userManagement,
                                    PaketRepository pakets) {
        this.onboarding = onboarding;
        this.clients = clients;
        this.userManagement = userManagement;
        this.pakets = pakets;
    }

    @GetMapping("/eduscreen/client")
    public String clients(Model model) {
        model.addAttribute("clients", clients.all());
        // Paket master yang bisa diberikan saat onboarding, lintas Subject (ADR-0018, ADR-0021):
        // hanya yang sudah TERBIT. Paket yang masih digarap tidak boleh terbaca sekolah baru lewat
        // pintu belakang onboarding (FR-067).
        model.addAttribute("paket", pakets.findAllMasterPublished());
        return "eduscreen/client";
    }

    /**
     * Membuat Client, akun Client Admin pertamanya beserta undangan, lalu memberi akses ke Paket
     * terpilih (FR-020, ADR-0021).
     *
     * <p>Onboarding sengaja <b>tidak</b> membuat Ruangan maupun akun Siswa: keduanya milik Client
     * Admin, yang tahu susunan kelasnya (BR-O01).
     */
    @PostMapping("/eduscreen/client")
    public String onboard(@RequestParam String name,
                          @RequestParam String timezone,
                          @RequestParam String adminEmail,
                          @RequestParam String adminFullName,
                          @RequestParam(required = false) List<UUID> paketIds) {
        var client = onboarding.onboard(new OnboardingRequest(
                name, timezone, adminEmail, adminFullName,
                paketIds == null ? List.of() : paketIds));
        // Ke detail Client, sebagaimana dijanjikan contracts/content-authoring.md: layar itu yang
        // memuat langkah lanjutannya — memperbaiki identitas, dan mengurus akun Client Admin-nya.
        return "redirect:/eduscreen/client/" + client.getId() + "?dibuat";
    }

    /**
     * Detail satu Client: identitasnya, statusnya, dan akun Client Admin-nya (FR-083..FR-085).
     *
     * <p>Yang sengaja <b>tidak</b> ada di sini: jumlah Ruangan, akun Guru dan Siswa, dan angka
     * pemakaian apa pun. Menampilkannya — bahkan sebagai hitungan — adalah membaca data
     * operasional sekolah, dan itu hanya boleh lewat akses dukungan (BR-P04, ADR-0015).
     */
    @GetMapping("/eduscreen/client/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("client", clients.require(id));
        model.addAttribute("admins", clients.clientAdmins(id));
        return "eduscreen/client-detail";
    }

    /** BR-O07, BR-O08. Zona baru tidak menggeser satu pun tanggal yang sudah tersimpan. */
    @PostMapping("/eduscreen/client/{id}")
    public String ubah(@PathVariable UUID id,
                       @RequestParam String name,
                       @RequestParam String timezone) {
        clients.rename(id, name);
        clients.changeTimezone(id, timezone);
        return "redirect:/eduscreen/client/" + id + "?disimpan";
    }

    /** BR-O09. Penegakannya ada di jalur login, bukan di sini. */
    @PostMapping("/eduscreen/client/{id}/status")
    public String ubahStatus(@PathVariable UUID id, @RequestParam ClientStatus status) {
        clients.changeStatus(id, status);
        return "redirect:/eduscreen/client/" + id + "?disimpan";
    }

    /** Peran dikunci CLIENT_ADMIN: layar ini tidak boleh jadi jalan membuat akun Guru atau Siswa. */
    @PostMapping("/eduscreen/client/{id}/admin")
    public String tambahAdmin(@PathVariable UUID id,
                              @RequestParam String email,
                              @RequestParam String fullName) {
        userManagement.create(id, email, fullName, UserRole.CLIENT_ADMIN);
        return "redirect:/eduscreen/client/" + id + "?diundang";
    }

    @PostMapping("/eduscreen/client/{id}/admin/{userId}/undang-ulang")
    public String undangUlang(@PathVariable UUID id, @PathVariable UUID userId) {
        // require() di dalamnya menyaring clientId, jadi userId milik Client lain berakhir 404
        // yang identik dengan tidak ada (TC-09).
        userManagement.reinvite(userId, id);
        return "redirect:/eduscreen/client/" + id + "?diundang";
    }

    /** BR-O10 dijaga di service; Client Admin terakhir yang masih bisa masuk tidak boleh hilang. */
    @PostMapping("/eduscreen/client/{id}/admin/{userId}/nonaktif")
    public String nonaktifkanAdmin(@PathVariable UUID id, @PathVariable UUID userId) {
        clients.deactivateClientAdmin(id, userId);
        return "redirect:/eduscreen/client/" + id + "?disimpan";
    }
}
