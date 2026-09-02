package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.StoredImageEntity;
import com.eduscreen.app.modules.assessment.service.ImageService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unggah dan penyajian gambar soal yang berotorisasi.
 *
 * <p>Tanpa endpoint baca ini, empat lapis anti-IDOR yang menjaga bank soal dan sesi ujian bisa
 * dilewati begitu saja: siapa pun yang memegang satu tautan {@code .png}/{@code .jpg} bisa
 * membaca isinya tanpa pernah menyentuh endpoint Soal atau Session yang dijaga. Soal ujian
 * besok bisa bocor lewat berkas, bukan lewat endpoint yang sudah diperiksa (TC-26, TC-27).
 * Karena itu jalur ini sengaja TIDAK didaftarkan {@code permitAll()} di {@link
 * com.eduscreen.app.config.SecurityConfig} dan MUST memeriksa {@code clientId} lewat {@link
 * ImageService#require} sebelum menyajikan satu byte pun.
 *
 * <p>{@code POST /gambar} pindah ke sini dari {@code QuestionBankController} lama (Task 14
 * pembersihan taksonomi): editor soal ({@code soal/editor.html}) tetap satu-satunya pemanggil,
 * dipakai baik Bank Soal Client maupun ruang kerja master. Perilakunya tidak berubah — penentuan
 * tipe dari magic bytes, encode ulang, dan penyimpanan lewat {@code FileStoragePort} tetap
 * di dalam {@link ImageService#store} (TC-26, TC-27, TC-28).
 */
@Controller
public class ImageController {

    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    @GetMapping("/gambar/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> read(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
        // clientId() dan bukan requireClientId(): Eduscreen Admin sengaja tidak punya Client,
        // dan ia harus tetap bisa melihat gambar di dalam Question master yang ditulisnya
        // (FR-063). Penyaringannya ada di ImageService.require — gambar milik sebuah Client
        // hanya terbaca Client itu, gambar master terbaca semua (TC-26, TC-09).
        StoredImageEntity image = images.require(id, user.clientId());
        // Cache PRIVAT, bukan public: gambar boleh disimpan cache milik peramban pemanggil
        // sendiri, tidak boleh disimpan cache bersama/proxy yang bisa disajikan ulang ke
        // pemanggil lain (TC-26).
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(images.read(image));
    }

    /**
     * Isi tidak pernah dicatat log (TC-44) — hanya {@code imageId} yang lahir dari
     * {@link ImageService#store} yang aman ditaruh di model.
     */
    @PostMapping("/gambar")
    public String uploadImage(@RequestParam("berkas") MultipartFile berkas,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) throws IOException {
        model.addAttribute("imageId", images.store(berkas.getBytes(), user).getId());
        return "soal/editor :: gambarTerunggah";
    }
}
