package com.eduscreen.app.modules.assessment.controller;

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

/**
 * Impor massal soal dari Excel/CSV.
 *
 * <p>Diproses <b>sinkron</b>, tanpa antrean pekerjaan latar (TC-45, ADR-0014). Batas 500 baris
 * per berkas adalah yang membuat itu mungkin — dan ia ada supaya tidak ada yang "memperbaikinya"
 * dengan memasukkan kembali infrastruktur pekerjaan latar yang sengaja dihindari di v1.
 *
 * <p>Alurnya tiga langkah: unggah, pratinjau berisi laporan baris gagal beserta nomor barisnya,
 * lalu simpan hanya baris yang valid (BR-Q05, AC-Q03).
 */
@Controller
public class ImportController {

    private final QuestionImportService imports;

    public ImportController(QuestionImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/admin/impor")
    public String form() {
        return "admin/impor";
    }

    @PostMapping("/admin/impor/pratinjau")
    public String preview(@RequestParam MultipartFile berkas,
                          @AuthenticationPrincipal UserPrincipal admin,
                          Model model) {
        model.addAttribute("pratinjau",
                imports.preview(berkas.getOriginalFilename(), bytesOf(berkas), admin.requireClientId()));
        return "admin/impor :: pratinjau";
    }

    @PostMapping("/admin/impor/simpan")
    public String commit(@RequestParam String token,
                         @AuthenticationPrincipal UserPrincipal admin,
                         Model model) {
        model.addAttribute("ringkasan",
                imports.commit(token, admin.requireClientId(), admin.userId()));
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
