package com.eduscreen.app.modules.identity.controller;

import com.eduscreen.app.modules.assessment.domain.InvitationPurpose;
import com.eduscreen.app.modules.identity.service.InvitationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Halaman masuk, undangan, dan pemulihan password.
 *
 * <p>Pemeriksaan kredensial sendiri tidak ada di sini: {@code POST /login} ditangani rangkaian
 * filter Spring Security, yang memanggil {@code IdentityProviderPort} lewat
 * {@code EduscreenAuthenticationProvider} (TC-07). Controller ini hanya menyajikan halaman dan
 * menjalankan alur token.
 *
 * <p>Dua kegagalan sengaja tidak bisa dibedakan dari luar: token yang tidak sah dan token yang
 * kedaluwarsa sama-sama {@code 404}, dan {@code POST /lupa-password} selalu menjawab konfirmasi
 * yang sama entah akunnya ada atau tidak — kalau tidak, ia menjadi alat memeriksa alamat email
 * mana yang terdaftar.
 */
@Controller
public class AuthController {

    private final InvitationService invitations;

    public AuthController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/undangan/{token}")
    public String invitation(@PathVariable String token, Model model) {
        return passwordForm(token, InvitationPurpose.INVITATION, "/undangan/", model);
    }

    @PostMapping("/undangan/{token}")
    public String acceptInvitation(@PathVariable String token, @RequestParam String password, Model model) {
        return redeem(token, InvitationPurpose.INVITATION, password, "/undangan/", model);
    }

    @GetMapping("/lupa-password")
    public String forgotPassword() {
        return "auth/lupa-password";
    }

    /** Konfirmasinya seragam: ada atau tidak ada akunnya, jawabannya sama. */
    @PostMapping("/lupa-password")
    public String requestReset(@RequestParam String email, Model model) {
        invitations.requestPasswordReset(email);
        model.addAttribute("terkirim", true);
        return "auth/lupa-password";
    }

    @GetMapping("/reset/{token}")
    public String resetForm(@PathVariable String token, Model model) {
        return passwordForm(token, InvitationPurpose.PASSWORD_RESET, "/reset/", model);
    }

    @PostMapping("/reset/{token}")
    public String reset(@PathVariable String token, @RequestParam String password, Model model) {
        return redeem(token, InvitationPurpose.PASSWORD_RESET, password, "/reset/", model);
    }

    private String passwordForm(String token, InvitationPurpose purpose, String action, Model model) {
        return invitations.resolve(token, purpose)
                .map(user -> {
                    model.addAttribute("action", action + token);
                    model.addAttribute("namaLengkap", user.getFullName());
                    return "auth/tetapkan-password";
                })
                .orElse("auth/token-tidak-sah");
    }

    private String redeem(String token, InvitationPurpose purpose, String password,
                          String action, Model model) {
        if (password == null || password.length() < 8) {
            model.addAttribute("action", action + token);
            model.addAttribute("galat", "Password minimal 8 karakter.");
            return "auth/tetapkan-password";
        }
        // Password mentah berhenti di IdentityProviderPort; ia tidak pernah masuk log maupun
        // tabel aplikasi (TC-06).
        return invitations.redeem(token, purpose, password)
                ? "redirect:/login?ditetapkan"
                : "auth/token-tidak-sah";
    }
}
