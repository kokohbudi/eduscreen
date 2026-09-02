package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.service.AssignmentLifecycleService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService.PublishRequest;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Portal Guru dan siklus hidup Assignment.
 *
 * <p>Daftar Ruangan tujuan pada formulir penerbitan hanya memuat Ruangan {@code ACTIVE} tempat
 * Guru itu ditugaskan (BR-M01). Pembatasan yang sama diulang di service: menyembunyikan pilihan
 * di antarmuka bukan penegakan, dan permintaan langsung ke Ruangan lain tetap harus ditolak
 * (AC-P01, AC-M03).
 */
@Controller
public class AssignmentController {

    private final AssignmentPublishingService publishing;
    private final AssignmentLifecycleService lifecycle;
    private final RuanganService ruangan;
    private final ExerciseService exercises;
    private final ClientDirectoryService clients;
    private final com.eduscreen.app.shared.domain.ClientClock clock;

    public AssignmentController(AssignmentPublishingService publishing,
                                AssignmentLifecycleService lifecycle,
                                RuanganService ruangan,
                                ExerciseService exercises,
                                ClientDirectoryService clients,
                                com.eduscreen.app.shared.domain.ClientClock clock) {
        this.publishing = publishing;
        this.lifecycle = lifecycle;
        this.ruangan = ruangan;
        this.exercises = exercises;
        this.clients = clients;
        this.clock = clock;
    }

    @GetMapping("/guru")
    public String portal(@AuthenticationPrincipal UserPrincipal guru, Model model) {
        model.addAttribute("ruangan", ruanganMilikGuru(guru));
        model.addAttribute("assignments", publishing.listPublished(guru.requireClientId()));
        model.addAttribute("sekarang", clock.now());
        return "guru/beranda";
    }

    @GetMapping("/guru/assignment")
    public String list(@RequestParam(required = false) UUID ruanganId,
                       @AuthenticationPrincipal UserPrincipal guru,
                       Model model) {
        List<AssignmentEntity> assignments = ruanganId == null
                ? publishing.listPublished(guru.requireClientId())
                : publishing.listForRuangan(ruanganId, guru);
        model.addAttribute("assignments", assignments);
        model.addAttribute("ruangan", ruanganMilikGuru(guru));
        model.addAttribute("sekarang", clock.now());
        return "guru/assignment";
    }

    @GetMapping("/guru/assignment/baru")
    public String form(@RequestParam(required = false) UUID exerciseId,
                       @AuthenticationPrincipal UserPrincipal guru,
                       Model model) {
        model.addAttribute("exerciseId", exerciseId);
        model.addAttribute("exercises",
                exercises.list(guru.requireClientId(), null, PageRequest.of(0, 100)).getContent());
        // Hanya Ruangan ACTIVE tempat Guru ditugaskan yang muncul (BR-M01, AC-M03).
        model.addAttribute("ruangan", ruanganMilikGuru(guru));
        return "guru/terbit";
    }

    /**
     * Membuat draf lalu langsung menerbitkannya.
     *
     * <p>Gerbang validasi berjalan di langkah penerbitan, bukan saat perakitan Exercise
     * (ADR-0003), sehingga satu kumpulan soal yang sama boleh terbit sebagai Quiz hari ini dan
     * sebagai Practice minggu depan.
     */
    @PostMapping("/guru/assignment")
    public String publish(@RequestParam UUID exerciseId,
                          @RequestParam List<UUID> ruanganIds,
                          @RequestParam String title,
                          @RequestParam AssignmentMode mode,
                          @RequestParam(required = false) Integer timerDurationMinutes,
                          @RequestParam String expiresAt,
                          @RequestParam(defaultValue = "1") int maxAttempts,
                          @RequestParam(defaultValue = "false") boolean shuffleQuestions,
                          @RequestParam(defaultValue = "false") boolean shuffleOptions,
                          @RequestParam(required = false) RevealAnswersAt revealAnswersAt,
                          @AuthenticationPrincipal UserPrincipal guru) {

        PublishRequest template = new PublishRequest(
                exerciseId, null, title, mode, timerDurationMinutes,
                parseExpiry(expiresAt, guru), maxAttempts, shuffleQuestions, shuffleOptions,
                revealAnswersAt);

        // Menerbitkan ke tiga Ruangan menghasilkan tiga Assignment terpisah (BR-M02, AC-M02).
        List<AssignmentEntity> published = publishing.publishBulk(template, ruanganIds, guru);
        return "redirect:/guru/assignment/" + published.getFirst().getId();
    }

    @GetMapping("/guru/assignment/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserPrincipal guru,
                         Model model) {
        model.addAttribute("assignment", publishing.require(id, guru));
        model.addAttribute("sekarang", clock.now());
        return "guru/assignment-detail";
    }

    /** Hanya diperpanjang; memajukan batas akhir dijawab {@code 422} (BR-A02, AC-A01). */
    @PatchMapping("/guru/assignment/{id}/perpanjang")
    public String extend(@PathVariable UUID id,
                         @RequestParam String expiresAt,
                         @AuthenticationPrincipal UserPrincipal guru,
                         Model model) {
        model.addAttribute("assignment", lifecycle.extend(id, parseExpiry(expiresAt, guru), guru));
        model.addAttribute("sekarang", clock.now());
        return "guru/assignment-detail :: ringkas";
    }

    @PostMapping("/guru/assignment/{id}/tutup")
    public String close(@PathVariable UUID id,
                        @AuthenticationPrincipal UserPrincipal guru,
                        Model model) {
        model.addAttribute("assignment", lifecycle.closeEarly(id, guru));
        model.addAttribute("sekarang", clock.now());
        return "guru/assignment-detail :: ringkas";
    }

    @DeleteMapping("/guru/assignment/{id}")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal guru) {
        lifecycle.deleteDraft(id, guru);
        return "redirect:/guru/assignment";
    }

    private List<com.eduscreen.app.modules.assessment.repository.RuanganEntity> ruanganMilikGuru(
            UserPrincipal guru) {
        return ruangan.listActive(guru.requireClientId()).stream()
                .filter(r -> ruangan.isAssignedGuru(r.getId(), guru.userId()))
                .toList();
    }

    /**
     * Menafsirkan waktu yang diketik Guru sebagai waktu <b>Client</b>, lalu menyimpannya UTC
     * (BR-T01, BR-T02, AC-T06).
     *
     * <p>"Minggu 23:59" berarti 23:59 di zona Client — bukan di zona perangkat yang kebetulan
     * dipakai Guru saat mengisinya.
     */
    private OffsetDateTime parseExpiry(String value, UserPrincipal guru) {
        return LocalDateTime.parse(value)
                .atZone(clients.zoneOf(guru.requireClientId()))
                .toOffsetDateTime();
    }
}
