package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
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
 * Portal Siswa dan halaman pengerjaan.
 *
 * <p>Permukaan paling sensitif di sistem: setiap endpoint di bawah tunduk penuh pada empat lapis
 * anti-IDOR. Kepemilikan dan {@code clientId} masuk ke klausa query di
 * {@link ExamSessionService#requireOwnSession}, sehingga sesi milik Siswa lain tidak pernah
 * termuat ke memori, dan sesi milik orang lain menghasilkan {@code 404} yang identik dengan sesi
 * yang tidak ada (TC-08, TC-09).
 *
 * <p>Auto-save mengembalikan <b>fragmen</b>, bukan JSON, sehingga satu jalur render melayani
 * muat awal maupun pembaruan parsial (TC-14).
 */
@Controller
public class ExamSessionController {

    private final ExamSessionService sessions;
    private final AnswerService answers;
    private final SessionFinalizer finalizer;
    private final ReportService reports;
    private final ClientClock clock;

    public ExamSessionController(ExamSessionService sessions,
                                 AnswerService answers,
                                 SessionFinalizer finalizer,
                                 ReportService reports,
                                 ClientClock clock) {
        this.sessions = sessions;
        this.answers = answers;
        this.finalizer = finalizer;
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping("/siswa")
    public String portal(@AuthenticationPrincipal UserPrincipal student, Model model) {
        model.addAttribute("assignments", sessions.activeAssignments(student));
        List<ExamSessionEntity> history = sessions.history(student);
        model.addAttribute("riwayat", history);
        var results = reports.resultsFor(history.stream().map(ExamSessionEntity::getId).toList());
        model.addAttribute("hasil", results);
        // Skor terbaik per Assignment dihitung di sini, bukan di templat (BR-L03). Perbandingan
        // lintas baris di dalam ekspresi templat sulit dibaca dan mudah salah; aturannya sendiri
        // hanya "yang tertinggi di antara pengerjaan Assignment yang sama".
        java.util.Map<UUID, java.math.BigDecimal> terbaik = new java.util.HashMap<>();
        for (ExamSessionEntity attempt : history) {
            var result = results.get(attempt.getId());
            if (result == null || result.getKind() != com.eduscreen.app.modules.assessment.domain.ResultKind.GRADED) {
                continue;
            }
            terbaik.merge(attempt.getAssignmentId(), result.getScore(),
                    (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        model.addAttribute("terbaikPerAssignment", terbaik);
        model.addAttribute("sekarang", clock.now());
        return "siswa/portal";
    }

    /** Sampul Assignment: judul, mode, batas waktu, dan jumlah pengerjaan yang sudah dipakai. */
    @GetMapping("/siswa/assignment/{id}")
    public String cover(@PathVariable UUID id,
                        @AuthenticationPrincipal UserPrincipal student,
                        Model model) {
        AssignmentEntity assignment = sessions.requireVisibleAssignment(id, student);
        List<ExamSessionEntity> attempts = sessions.attemptsOf(id, student.userId());
        model.addAttribute("assignment", assignment);
        model.addAttribute("pengerjaan", attempts);
        model.addAttribute("hasil", reports.resultsFor(attempts.stream().map(ExamSessionEntity::getId).toList()));
        model.addAttribute("sekarang", clock.now());
        return "siswa/sampul";
    }

    @PostMapping("/siswa/assignment/{id}/mulai")
    public String start(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal student) {
        return "redirect:/siswa/sesi/" + sessions.start(id, student).getId();
    }

    @GetMapping("/siswa/sesi/{sessionId}")
    public String work(@PathVariable UUID sessionId,
                       @AuthenticationPrincipal UserPrincipal student,
                       Model model) {
        ExamSessionEntity session = sessions.readAndFinalize(sessionId, student);
        AssignmentEntity assignment = sessions.assignmentOf(session);
        if (!session.isInProgress()) {
            return "redirect:/siswa/sesi/" + sessionId + "/hasil";
        }
        List<Integer> answered = sessions.answeredPositions(sessionId);
        int start = assignment.isPractice() ? sessions.practiceFrontier(answered) : 0;

        model.addAttribute("sesi", session);
        model.addAttribute("assignment", assignment);
        model.addAttribute("terjawab", answered);
        model.addAttribute("sisaDetik", sessions.remainingSeconds(session));
        model.addAttribute("soal", sessions.view(session, Math.min(start, jumlahSoal(session) - 1),
                assignment.isPractice()));
        return assignment.isPractice() ? "siswa/practice" : "siswa/pengerjaan";
    }

    /**
     * Satu soal sebagai fragmen.
     *
     * <p>Pada Practice navigasinya maju satu arah: lompatan ke depan ditolak {@code 409}, bukan
     * dibiarkan lalu diperbaiki di klien — aturan yang hanya hidup di JavaScript bisa dilewati
     * dengan satu permintaan langsung (§6.7).
     */
    @GetMapping("/siswa/sesi/{sessionId}/soal/{position}")
    public String question(@PathVariable UUID sessionId,
                           @PathVariable int position,
                           @AuthenticationPrincipal UserPrincipal student,
                           Model model) {
        ExamSessionEntity session = sessions.requireOwnSession(sessionId, student);
        AssignmentEntity assignment = sessions.assignmentOf(session);
        List<Integer> answered = sessions.answeredPositions(sessionId);

        if (assignment.isPractice() && position > sessions.practiceFrontier(answered)) {
            throw new IllegalStateException("Mode latihan hanya bisa maju satu soal setiap kali");
        }
        // Fragmen soal ikut merender kerangka di sekelilingnya (peta soal, hitung mundur),
        // jadi ia butuh sesi dan sisa waktu yang sama dengan muat awal — satu jalur render
        // untuk keduanya (TC-14).
        model.addAttribute("sesi", session);
        model.addAttribute("sisaDetik", sessions.remainingSeconds(session));
        model.addAttribute("soal", sessions.view(session, position, assignment.isPractice()));
        model.addAttribute("assignment", assignment);
        model.addAttribute("terjawab", answered);
        return assignment.isPractice()
                ? "siswa/practice :: soal"
                : "siswa/pengerjaan :: soal";
    }

    @PutMapping("/siswa/sesi/{sessionId}/jawaban/{sessionQuestionId}")
    public String saveAnswer(@PathVariable UUID sessionId,
                             @PathVariable UUID sessionQuestionId,
                             @RequestParam(required = false) UUID selectedOptionId,
                             @RequestParam(required = false) String essayText,
                             @AuthenticationPrincipal UserPrincipal student,
                             Model model) {
        // Isi jawaban tidak pernah masuk log (TC-44); yang dicatat hanya pengenal sesi.
        answers.save(sessionId, sessionQuestionId, selectedOptionId, essayText, student);

        ExamSessionEntity session = sessions.requireOwnSession(sessionId, student);
        AssignmentEntity assignment = sessions.assignmentOf(session);
        model.addAttribute("sesi", session);
        model.addAttribute("sisaDetik", sessions.remainingSeconds(session));
        model.addAttribute("soal", sessions.view(session,
                positionOf(session, sessionQuestionId), assignment.isPractice()));
        model.addAttribute("assignment", assignment);
        model.addAttribute("terjawab", sessions.answeredPositions(sessionId));
        return assignment.isPractice()
                ? "siswa/practice :: soal"
                : "siswa/pengerjaan :: soal";
    }

    @PostMapping("/siswa/sesi/{sessionId}/selesai")
    public String submit(@PathVariable UUID sessionId, @AuthenticationPrincipal UserPrincipal student) {
        sessions.requireOwnSession(sessionId, student);
        finalizer.submit(sessionId, student.requireClientId());
        return "redirect:/siswa/sesi/" + sessionId + "/hasil";
    }

    /**
     * Hasil, disingkap sesuai aturan mode (§9.5).
     *
     * <p>{@code AFTER_EXPIRATION} menahan kunci dan pembahasan sampai Assignment berakhir; tanpa
     * itu, Siswa yang selesai lebih dulu bisa membocorkan kunci ke kelas yang masih mengerjakan.
     */
    @GetMapping("/siswa/sesi/{sessionId}/hasil")
    public String result(@PathVariable UUID sessionId,
                         @AuthenticationPrincipal UserPrincipal student,
                         Model model) {
        ExamSessionEntity session = sessions.readAndFinalize(sessionId, student);
        if (session.isInProgress()) {
            throw new IllegalStateException("Sesi belum berakhir");
        }
        AssignmentEntity assignment = sessions.assignmentOf(session);
        boolean bukaKunci = assignment.isPractice()
                || assignment.getRevealAnswersAt() == RevealAnswersAt.AFTER_SUBMIT
                || clock.now().isAfter(assignment.getExpiresAt());

        model.addAttribute("sesi", session);
        model.addAttribute("assignment", assignment);
        model.addAttribute("bukaKunci", bukaKunci);
        model.addAttribute("hasil", reports.resultsFor(List.of(sessionId)).get(sessionId));
        model.addAttribute("soalSesi", soalSesi(session, assignment.isPractice()));
        return "siswa/hasil";
    }

    private List<ExamSessionService.QuestionView> soalSesi(ExamSessionEntity session, boolean practice) {
        int total = jumlahSoal(session);
        return java.util.stream.IntStream.range(0, total)
                .mapToObj(position -> sessions.view(session, position, practice))
                .toList();
    }

    private int jumlahSoal(ExamSessionEntity session) {
        return sessions.snapshotOf(session.getId()).size();
    }

    private int positionOf(ExamSessionEntity session, UUID sessionQuestionId) {
        return sessions.snapshotOf(session.getId()).stream()
                .filter(sessionQuestion -> sessionQuestion.getId().equals(sessionQuestionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"))
                .getPosition();
    }
}
