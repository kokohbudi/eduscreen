package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Rekap nilai Ruangan dan aktivitas latihan.
 *
 * <p>Membuka rekap adalah tindakan yang <b>mengubah data</b>: ia memfinalisasi setiap sesi yang
 * sudah lewat batas waktunya (BR-L02, ADR-0002). Itu disengaja — tanpa scheduler, inilah momen
 * ketika Result milik Siswa yang menutup browser dan tidak pernah kembali akhirnya lahir.
 */
@Controller
public class ReportController {

    private final ReportService reports;
    private final AssignmentPublishingService publishing;
    private final UserManagementService users;

    public ReportController(ReportService reports,
                            AssignmentPublishingService publishing,
                            UserManagementService users) {
        this.reports = reports;
        this.publishing = publishing;
        this.users = users;
    }

    @GetMapping("/guru/assignment/{id}/rekap")
    public String recap(@PathVariable UUID id,
                        @AuthenticationPrincipal UserPrincipal guru,
                        Model model) {
        model.addAttribute("assignment", publishing.require(id, guru));
        // Barisnya sebanyak anggota Ruangan, bukan sebanyak sesi: Siswa yang tidak pernah Start
        // tampil NOT_STARTED tanpa satu baris sesi pun dibuat untuknya (BR-L01).
        model.addAttribute("baris", reports.recap(id, guru));
        return "guru/rekap";
    }

    @GetMapping("/guru/assignment/{id}/rekap/siswa/{studentId}")
    public String studentHistory(@PathVariable UUID id,
                                 @PathVariable UUID studentId,
                                 @AuthenticationPrincipal UserPrincipal guru,
                                 Model model) {
        List<ExamSessionEntity> attempts = reports.attemptsOf(id, studentId, guru);
        model.addAttribute("assignment", publishing.require(id, guru));
        model.addAttribute("siswa", users.require(studentId, guru.requireClientId()));
        model.addAttribute("pengerjaan", attempts);
        // Seluruh Attempt tetap bisa dibuka meski hanya yang tertinggi yang menjadi nilai resmi
        // (BR-L03, AC-L02).
        var results = reports.resultsFor(attempts.stream().map(ExamSessionEntity::getId).toList());
        model.addAttribute("hasil", results);
        // Skor tertinggi dihitung di sini, bukan di templat: perbandingan lintas baris di dalam
        // ekspresi templat sulit dibaca dan mudah salah, sementara aturannya sendiri sederhana
        // (BR-L03).
        model.addAttribute("skorTertinggi", results.values().stream()
                .map(r -> r.getScore())
                .max(java.util.Comparator.naturalOrder())
                .orElse(null));
        return "guru/riwayat-siswa";
    }

    /**
     * Aktivitas latihan, terpisah dari rekap nilai (BR-L04, AC-C03).
     *
     * <p>Practice adalah latihan, bukan penilaian. Mencampurnya ke rekap nilai membuat Siswa yang
     * paling rajin berlatih terlihat seperti Siswa yang nilainya paling jelek.
     */
    @GetMapping("/guru/ruangan/{id}/latihan")
    public String practiceActivity(@PathVariable UUID id,
                                   @AuthenticationPrincipal UserPrincipal guru,
                                   Model model) {
        model.addAttribute("ruanganId", id);
        model.addAttribute("hasil", reports.practiceActivity(id, guru));
        return "guru/latihan";
    }
}
