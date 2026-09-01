package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Halaman pendaratan Eduscreen Admin.
 *
 * <p>Controller sendiri, bukan menumpang {@code EduscreenAdminController}: yang ini membaca dari
 * tiga tempat sekaligus lewat {@link EduscreenDashboardService}, sementara controller itu
 * urusannya onboarding Client. Menumpuk keduanya memberi satu kelas dua alasan untuk berubah.
 *
 * <p>Rutenya di bawah {@code /eduscreen/**} yang sudah dipagari {@code hasRole("EDUSCREEN_ADMIN")}
 * di {@code SecurityConfig}.
 */
@Controller
public class EduscreenDashboardController {

    private final EduscreenDashboardService dashboard;

    public EduscreenDashboardController(EduscreenDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/eduscreen")
    public String dashboard(Model model) {
        model.addAttribute("kartu", dashboard.kartu());
        model.addAttribute("antrean", dashboard.antrean());
        model.addAttribute("menuAktif", "dashboard");
        return "eduscreen/dashboard";
    }
}
