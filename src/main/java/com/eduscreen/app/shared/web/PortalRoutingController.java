package com.eduscreen.app.shared.web;

import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Satu pintu masuk, empat tujuan.
 *
 * <p>{@code /} tidak pernah merender apa pun sendiri: ia mengalihkan ke portal sesuai peran.
 * Dengan begitu setiap portal punya alamat tetap yang bisa ditandai dan dibagikan, sementara
 * pengguna yang mengetik alamat pangkalnya tetap sampai ke tempat yang benar.
 */
@Controller
public class PortalRoutingController {

    @GetMapping("/")
    public String route(@AuthenticationPrincipal UserPrincipal user) {
        return switch (user.role()) {
            case SISWA -> "redirect:/siswa";
            case GURU -> "redirect:/guru";
            case CLIENT_ADMIN -> "redirect:/admin";
            case EDUSCREEN_ADMIN -> "redirect:/eduscreen";
        };
    }
}
