package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.service.QuestionImportService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Impor massal soal dari Excel/CSV ke dalam satu Paket dan Topic yang dipilih (ADR-0018).
 *
 * <p>Diproses <b>sinkron</b>, tanpa antrean pekerjaan latar (TC-45, ADR-0014). Batas 500 baris
 * per berkas adalah yang membuat itu mungkin — dan ia ada supaya tidak ada yang "memperbaikinya"
 * dengan memasukkan kembali infrastruktur pekerjaan latar yang sengaja dihindari di v1.
 *
 * <p>Alurnya tiga langkah: unggah beserta tujuan, pratinjau berisi laporan baris gagal beserta
 * nomor barisnya, lalu simpan hanya baris yang valid (BR-Q05, AC-Q03). Tujuan dibawa ulang dari
 * pratinjau ke penyimpanan lewat field tersembunyi, dan layanan memeriksanya lagi — nilai dari
 * formulir tidak pernah dipercaya begitu saja (TC-36, AC-B02).
 */
@Controller
public class ImportController {

    private final QuestionImportService imports;
    private final PaketRepository pakets;

    public ImportController(QuestionImportService imports, PaketRepository pakets) {
        this.imports = imports;
        this.pakets = pakets;
    }

    @GetMapping("/admin/impor")
    public String form(@AuthenticationPrincipal UserPrincipal admin, Model model) {
        model.addAttribute("pakets", pakets.findByClientIdOrderByTitleAsc(admin.requireClientId()));
        return "admin/impor";
    }

    @PostMapping("/admin/impor/pratinjau")
    public String preview(@RequestParam MultipartFile berkas,
                          @RequestParam UUID paketId,
                          @RequestParam UUID topicId,
                          @AuthenticationPrincipal UserPrincipal admin,
                          Model model) {
        model.addAttribute("pratinjau",
                imports.preview(berkas.getOriginalFilename(), bytesOf(berkas), paketId, admin.requireClientId()));
        // Tujuan ikut ke fragmen pratinjau supaya formulir simpan membawanya kembali; topicId
        // baru diperiksa milik Paket-nya saat menyimpan (AC-B02).
        model.addAttribute("paketId", paketId);
        model.addAttribute("topicId", topicId);
        return "admin/impor :: pratinjau";
    }

    @PostMapping("/admin/impor/simpan")
    public String commit(@RequestParam String token,
                         @RequestParam UUID paketId,
                         @RequestParam UUID topicId,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        model.addAttribute("ringkasan",
                imports.commit(token, paketId, topicId, admin.requireClientId(), admin.userId()));
        return "admin/impor :: ringkasan";
    }

    private byte[] bytesOf(MultipartFile berkas) {
        try {
            return berkas.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Berkas unggahan tidak terbaca", e);
        }
    }
}
