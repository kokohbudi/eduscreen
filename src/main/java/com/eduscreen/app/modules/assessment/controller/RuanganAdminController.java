package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Ruangan dan keanggotaannya, dikelola Client Admin.
 *
 * <p>Batas tenant menyaring lewat {@link RuanganService}: Ruangan milik Client lain
 * menghasilkan {@code 404} yang identik dengan Ruangan yang tidak ada, bukan {@code 403}
 * yang membocorkan keberadaannya (TC-08, TC-09, TC-36). Ruangan {@code ARCHIVED} tetap terbaca
 * tetapi menolak anggota baru — penolakan itu ditegakkan di service, bukan di sini.
 *
 * <p>Setiap endpoint yang dipanggil HTMX mengembalikan fragmen, bukan JSON (TC-14).
 */
@Controller
public class RuanganAdminController {

    /** Ukuran daftar kandidat cukup besar untuk memuat seluruh pengguna Client dalam satu layar. */
    private static final Pageable KANDIDAT = Pageable.ofSize(200);

    private final RuanganService ruangan;
    private final UserManagementService users;

    public RuanganAdminController(RuanganService ruangan, UserManagementService users) {
        this.ruangan = ruangan;
        this.users = users;
    }

    @GetMapping("/admin/ruangan")
    public String daftar(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        model.addAttribute("ruangan", ruangan.list(admin.requireClientId()));
        return "admin/ruangan";
    }

    /** Halaman Ruangan baru (BR-U05): nama dan anggota pertamanya di satu layar. */
    @GetMapping("/admin/ruangan/baru")
    public String baru(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        UUID clientId = admin.requireClientId();
        model.addAttribute("guru", users.list(clientId, UserRole.GURU, KANDIDAT).getContent());
        model.addAttribute("siswa", users.list(clientId, UserRole.SISWA, KANDIDAT).getContent());
        return "admin/ruangan-baru";
    }

    /**
     * Ruangan lahir bersama anggotanya (BR-U05). Form biasa + redirect ke detailnya, bukan
     * fragmen HTMX: yang dibuat bukan lagi satu baris tabel, melainkan Ruangan berisi anggota.
     */
    @PostMapping("/admin/ruangan")
    public String tambah(@RequestParam String name,
                         @RequestParam(required = false) List<UUID> guruIds,
                         @RequestParam(required = false) List<UUID> siswaIds,
                         @AuthenticationPrincipal UserPrincipal admin) {
        UUID clientId = admin.requireClientId();
        RuanganEntity dibuat = ruangan.create(clientId, name);
        if (guruIds != null && !guruIds.isEmpty()) {
            ruangan.addMembers(dibuat.getId(), clientId, guruIds, MemberRole.GURU);
        }
        if (siswaIds != null && !siswaIds.isEmpty()) {
            ruangan.addMembers(dibuat.getId(), clientId, siswaIds, MemberRole.SISWA);
        }
        return "redirect:/admin/ruangan/" + dibuat.getId();
    }

    @GetMapping("/admin/ruangan/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        UUID clientId = admin.requireClientId();
        model.addAttribute("ruangan", ruangan.require(id, clientId));
        model.addAttribute("kandidat", users.list(clientId, null, KANDIDAT).getContent());
        isiModelAnggota(model, id, clientId);
        return "admin/ruangan-detail";
    }

    /** Ruangan terarsip menolak anggota baru; penolakannya lahir dari {@code RuanganService}. */
    @PostMapping("/admin/ruangan/{id}/anggota")
    public String tambahAnggota(@PathVariable UUID id,
                                @RequestParam List<UUID> userIds,
                                @RequestParam MemberRole memberRole,
                                @AuthenticationPrincipal UserPrincipal admin,
                                Model model) {
        UUID clientId = admin.requireClientId();
        ruangan.addMembers(id, clientId, userIds, memberRole);
        model.addAttribute("ruangan", ruangan.require(id, clientId));
        isiModelAnggota(model, id, clientId);
        return "admin/ruangan-detail :: anggota";
    }

    @DeleteMapping("/admin/ruangan/{id}/anggota/{userId}")
    public String hapusAnggota(@PathVariable UUID id,
                               @PathVariable UUID userId,
                               @AuthenticationPrincipal UserPrincipal admin,
                               Model model) {
        UUID clientId = admin.requireClientId();
        ruangan.removeMember(id, clientId, userId);
        model.addAttribute("ruangan", ruangan.require(id, clientId));
        isiModelAnggota(model, id, clientId);
        return "admin/ruangan-detail :: anggota";
    }

    /** Keluarkan beberapa anggota sekaligus dari centangan (AC-B26). Form biasa + redirect. */
    @PostMapping("/admin/ruangan/{id}/anggota/massal")
    public String massalAnggota(@PathVariable UUID id,
                                @RequestParam String aksi,
                                @RequestParam(required = false) List<UUID> userIds,
                                @AuthenticationPrincipal UserPrincipal admin) {
        UUID clientId = admin.requireClientId();
        if (!"keluarkan".equals(aksi)) {
            throw new IllegalArgumentException("Aksi massal tidak dikenal: " + aksi);
        }
        for (UUID userId : userIds == null ? List.<UUID>of() : userIds) {
            ruangan.removeMember(id, clientId, userId);
        }
        return "redirect:/admin/ruangan/" + id;
    }

    /** Arsipkan beberapa Ruangan sekaligus dari centangan (AC-B26). Form biasa + redirect. */
    @PostMapping("/admin/ruangan/massal")
    public String massalRuangan(@RequestParam String aksi,
                                @RequestParam(required = false) List<UUID> ruanganIds,
                                @AuthenticationPrincipal UserPrincipal admin) {
        UUID clientId = admin.requireClientId();
        if (!"arsip".equals(aksi)) {
            throw new IllegalArgumentException("Aksi massal tidak dikenal: " + aksi);
        }
        for (UUID id : ruanganIds == null ? List.<UUID>of() : ruanganIds) {
            ruangan.archive(id, clientId);
        }
        return "redirect:/admin/ruangan";
    }

    /** Riwayat Result Ruangan tetap terbaca sesudahnya; yang berhenti hanya aktivitas baru (FR-010). */
    @PostMapping("/admin/ruangan/{id}/arsip")
    public String arsipkan(@PathVariable UUID id,
                           @AuthenticationPrincipal UserPrincipal admin,
                           Model model) {
        RuanganEntity diarsipkan = ruangan.archive(id, admin.requireClientId());
        model.addAttribute("ruangan", List.of(diarsipkan));
        return "admin/ruangan :: baris";
    }

    private void isiModelAnggota(Model model, UUID ruanganId, UUID clientId) {
        model.addAttribute("guru", ruangan.membersOf(ruanganId, clientId, MemberRole.GURU));
        model.addAttribute("siswa", ruangan.membersOf(ruanganId, clientId, MemberRole.SISWA));
    }
}
