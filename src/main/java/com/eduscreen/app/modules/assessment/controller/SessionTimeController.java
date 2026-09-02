package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.UUID;

/**
 * Sisa waktu dan heartbeat.
 *
 * <p>Sisa waktu selalu datang dari server (BR-T03, TC-15). Hitung mundur di layar hanyalah
 * tampilan yang menyalin angka ini; Siswa yang memundurkan jam perangkatnya tidak mendapat
 * satu detik pun tambahan.
 *
 * <p>Heartbeat menjaga <b>sesi login</b> tetap hidup selama pengerjaan berlangsung, dan tidak
 * memperpanjang batas waktu pengerjaan (TC-32). Keduanya sengaja dipisah: Siswa yang membaca
 * teks bacaan panjang selama 35 menit tanpa menekan apa pun tidak mengirim satu permintaan pun,
 * dan sesi login-nya mati meski Timer ujiannya masih tersisa.
 */
@Controller
public class SessionTimeController {

    private final ExamSessionService sessions;

    public SessionTimeController(ExamSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/siswa/sesi/{sessionId}/waktu")
    public String remaining(@PathVariable UUID sessionId,
                            @AuthenticationPrincipal UserPrincipal student,
                            Model model) {
        ExamSessionEntity session = sessions.requireOwnSession(sessionId, student);
        model.addAttribute("sisaDetik", sessions.remainingSeconds(session));
        model.addAttribute("berjalan", session.isInProgress());
        return "siswa/fragmen-waktu";
    }

    /**
     * Menyentuh sesi login tanpa menyentuh apa pun yang lain.
     *
     * <p>Pemanggilan {@code requireOwnSession} tetap dilakukan supaya endpoint ini tidak menjadi
     * cara memperpanjang sesi login dengan menyebut pengenal sesi milik orang lain.
     */
    @PostMapping("/siswa/sesi/{sessionId}/heartbeat")
    @ResponseBody
    public ResponseEntity<Void> heartbeat(@PathVariable UUID sessionId,
                                          @AuthenticationPrincipal UserPrincipal student) {
        ExamSessionEntity session = sessions.requireOwnSession(sessionId, student);
        if (!session.isInProgress()) {
            // Sesi yang sudah berakhir tidak berhak memperpanjang sesi login siapa pun.
            throw new com.eduscreen.app.shared.web.GoneException("Sesi sudah berakhir");
        }
        return ResponseEntity.noContent().build();
    }
}
