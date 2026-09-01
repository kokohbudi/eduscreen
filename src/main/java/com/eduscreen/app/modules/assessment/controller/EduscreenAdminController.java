package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService.OnboardingRequest;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Portal Eduscreen Admin: daftar Client dan konten master.
 *
 * <p>Ini satu-satunya peran yang berdiri di luar Client mana pun, dan karena itu satu-satunya
 * yang tidak boleh punya jalan membaca data operasional sebuah sekolah tanpa izin. Onboarding
 * dan pengelolaan katalog master ada di sini; membaca bank soal sebuah Client tidak.
 */
@Controller
public class EduscreenAdminController {

    private final ClientOnboardingService onboarding;
    private final ClientDirectoryService clients;
    private final ExerciseService exercises;

    public EduscreenAdminController(ClientOnboardingService onboarding,
                                    ClientDirectoryService clients,
                                    ExerciseService exercises) {
        this.onboarding = onboarding;
        this.clients = clients;
        this.exercises = exercises;
    }

    @GetMapping("/eduscreen/client")
    public String clients(Model model) {
        model.addAttribute("clients", clients.all());
        // Paket master yang bisa disalin saat onboarding: Exercise milik Eduscreen (clientId
        // null) yang sudah TERBIT. Paket yang masih digarap tidak boleh ikut mendarat di
        // sekolah baru lewat pintu belakang onboarding (FR-067).
        model.addAttribute("paket",
                exercises.listPublishedMaster(null, PageRequest.of(0, 100)).getContent());
        model.addAttribute("menuAktif", "client");
        return "eduscreen/client";
    }

    /**
     * Membuat Client, akun Client Admin pertamanya beserta undangan, lalu menyalin paket terpilih
     * (FR-020).
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
        return "redirect:/eduscreen/client?dibuat=" + client.getId();
    }
}
