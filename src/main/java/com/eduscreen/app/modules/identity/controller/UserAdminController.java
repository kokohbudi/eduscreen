package com.eduscreen.app.modules.identity.controller;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Manajemen akun oleh Client Admin: pembuatan, penyuntingan, penonaktifan, dan undangan ulang.
 *
 * <p>Setiap pembacaan dan penulisan menyaring lewat {@code admin.requireClientId()} sebelum
 * memanggil {@link UserManagementService}; batas tenant hidup di klausa query service, sehingga
 * akun milik Client lain tidak pernah termuat — bukan diperiksa lalu ditolak (TC-08, TC-09).
 *
 * <p>Endpoint yang dipanggil HTMX mengembalikan fragmen baris, bukan JSON, agar satu jalur
 * render melayani muat awal maupun pembaruan parsial (TC-14).
 */
@Controller
public class UserAdminController {

    private static final int UKURAN_HALAMAN = 25;

    private final UserManagementService users;

    public UserAdminController(UserManagementService users) {
        this.users = users;
    }

    @GetMapping("/admin/pengguna")
    public String daftar(@RequestParam(required = false) UserRole role,
                         @RequestParam(defaultValue = "0") int page,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        Page<AppUserEntity> pengguna = users.list(admin.requireClientId(), role, PageRequest.of(page, UKURAN_HALAMAN));
        isiModelDaftar(model, pengguna, role);
        return "admin/pengguna";
    }

    /** Akun lahir INVITED; undangan terkirim di dalam {@code create} (FR-009). */
    @PostMapping("/admin/pengguna")
    public String tambah(@RequestParam String email,
                         @RequestParam String fullName,
                         @RequestParam UserRole role,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        AppUserEntity dibuat = users.create(admin.requireClientId(), email, fullName, role);
        model.addAttribute("daftar", List.of(dibuat));
        return "admin/pengguna :: baris";
    }

    @GetMapping("/admin/pengguna/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        UUID clientId = admin.requireClientId();
        AppUserEntity terpilih = users.require(id, clientId);
        Page<AppUserEntity> pengguna = users.list(clientId, null, PageRequest.of(0, UKURAN_HALAMAN));
        isiModelDaftar(model, pengguna, null);
        model.addAttribute("terpilih", terpilih);
        return "admin/pengguna";
    }

    @PutMapping("/admin/pengguna/{id}")
    public String ubah(@PathVariable UUID id,
                       @RequestParam String fullName,
                       @RequestParam UserRole role,
                       @AuthenticationPrincipal UserPrincipal admin,
                       Model model) {
        AppUserEntity diubah = users.update(id, admin.requireClientId(), fullName, role);
        model.addAttribute("daftar", List.of(diubah));
        return "admin/pengguna :: baris";
    }

    /** Riwayat Session dan Result tetap utuh; yang hilang hanya kemampuan login (BR-U03). */
    @PostMapping("/admin/pengguna/{id}/nonaktif")
    public String nonaktifkan(@PathVariable UUID id,
                              @AuthenticationPrincipal UserPrincipal admin,
                              Model model) {
        AppUserEntity dinonaktifkan = users.deactivate(id, admin.requireClientId());
        model.addAttribute("daftar", List.of(dinonaktifkan));
        return "admin/pengguna :: baris";
    }

    @PostMapping("/admin/pengguna/{id}/undang-ulang")
    public String undangUlang(@PathVariable UUID id,
                              @AuthenticationPrincipal UserPrincipal admin,
                              Model model) {
        users.reinvite(id, admin.requireClientId());
        model.addAttribute("pesan", "Undangan terkirim ulang.");
        return "admin/pengguna :: konfirmasi";
    }

    private void isiModelDaftar(Model model, Page<AppUserEntity> pengguna, UserRole role) {
        model.addAttribute("pengguna", pengguna);
        model.addAttribute("daftar", pengguna.getContent());
        model.addAttribute("peranTerpilih", role);
        model.addAttribute("peran", UserRole.values());
    }
}
