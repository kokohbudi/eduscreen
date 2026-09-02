package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.GradingService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Antrean penilaian essay.
 *
 * <p>Guru yang tidak ditugaskan di Ruangan Assignment itu mendapat {@code 404} — identik dengan
 * Assignment yang tidak ada (BR-G01, AC-G01, TC-09). Penegakannya ada di service, bukan di
 * sini, sehingga tidak bisa dilewati dengan permintaan langsung.
 *
 * <p>Perhitungan ulang Result terjadi pada permintaan yang sama, dan setiap perubahan nilai
 * meninggalkan satu baris audit permanen (BR-G03, TC-37).
 */
@Controller
public class GradingController {

    private final GradingService grading;
    private final AssignmentPublishingService publishing;

    public GradingController(GradingService grading, AssignmentPublishingService publishing) {
        this.grading = grading;
        this.publishing = publishing;
    }

    @GetMapping("/guru/assignment/{id}/penilaian")
    public String queue(@PathVariable UUID id,
                        @AuthenticationPrincipal UserPrincipal guru,
                        Model model) {
        model.addAttribute("assignment", publishing.require(id, guru));
        model.addAttribute("antrean", grading.pendingQueue(id, guru));
        return "guru/penilaian";
    }

    @PutMapping("/guru/jawaban/{sessionAnswerId}/nilai")
    public String grade(@PathVariable UUID sessionAnswerId,
                        @RequestParam int essayScore,
                        @AuthenticationPrincipal UserPrincipal guru,
                        Model model) {
        model.addAttribute("hasil", grading.grade(sessionAnswerId, essayScore, guru));
        model.addAttribute("sessionAnswerId", sessionAnswerId);
        return "guru/penilaian :: baris";
    }
}
