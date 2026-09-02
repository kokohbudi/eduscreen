package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.SupportAccessGrantEntity;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.modules.assessment.service.SupportAccessService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Beranda portal Client Admin: ringkasan Ruangan dan pengguna, serta saklar akses dukungan.
 *
 * <p>Akses dukungan adalah satu-satunya pengecualian isolasi tenant (BR-P05, ADR-0015):
 * dinyalakan di sini oleh Client Admin sendiri, baca-saja, padam sendiri sesudah 4 jam, dan
 * setiap pembacaan Eduscreen Admin selama jendela itu tercatat lalu bisa ditinjau lewat jejak
 * (TC-46). Controller ini tidak menyentuh jendela itu langsung — semuanya lewat
 * {@link SupportAccessService}.
 */
@Controller
public class ClientAdminPortalController {

    private final RuanganService ruangan;
    private final UserManagementService users;
    private final SupportAccessService supportAccess;

    public ClientAdminPortalController(RuanganService ruangan,
                                       UserManagementService users,
                                       SupportAccessService supportAccess) {
        this.ruangan = ruangan;
        this.users = users;
        this.supportAccess = supportAccess;
    }

    @GetMapping("/admin")
    public String beranda(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        isiModelRingkasan(model, admin.requireClientId());
        return "admin/beranda";
    }

    @PostMapping("/admin/akses-dukungan")
    public String nyalakanAksesDukungan(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        SupportAccessGrantEntity grant = supportAccess.grant(admin.requireClientId(), admin.userId());
        model.addAttribute("aksesDukungan", grant);
        return "admin/beranda :: aksesDukungan";
    }

    @DeleteMapping("/admin/akses-dukungan")
    public String cabutAksesDukungan(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        supportAccess.revoke(admin.requireClientId());
        model.addAttribute("aksesDukungan", null);
        return "admin/beranda :: aksesDukungan";
    }

    @GetMapping("/admin/akses-dukungan/jejak")
    public String jejakAksesDukungan(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        UUID clientId = admin.requireClientId();
        isiModelRingkasan(model, clientId);
        model.addAttribute("jejak", supportAccess.trail(clientId));
        return "admin/beranda";
    }

    private void isiModelRingkasan(Model model, UUID clientId) {
        model.addAttribute("jumlahRuanganAktif", ruangan.listActive(clientId).size());
        model.addAttribute("jumlahPerPeran", jumlahPenggunaPerPeran(clientId));
        model.addAttribute("aksesDukungan", supportAccess.activeGrant(clientId).orElse(null));
    }

    /** Hanya peran operasional Client yang dihitung; EDUSCREEN_ADMIN berdiri di luar Client. */
    private Map<UserRole, Long> jumlahPenggunaPerPeran(UUID clientId) {
        Map<UserRole, Long> jumlah = new EnumMap<>(UserRole.class);
        for (UserRole role : List.of(UserRole.CLIENT_ADMIN, UserRole.GURU, UserRole.SISWA)) {
            jumlah.put(role, users.list(clientId, role, Pageable.ofSize(1)).getTotalElements());
        }
        return jumlah;
    }
}
