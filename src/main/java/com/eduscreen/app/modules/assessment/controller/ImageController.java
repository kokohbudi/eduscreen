package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.StoredImageEntity;
import com.eduscreen.app.modules.assessment.service.ImageService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Penyajian gambar soal yang berotorisasi.
 *
 * <p>Tanpa endpoint ini, empat lapis anti-IDOR yang menjaga bank soal dan sesi ujian bisa
 * dilewati begitu saja: siapa pun yang memegang satu tautan {@code .png}/{@code .jpg} bisa
 * membaca isinya tanpa pernah menyentuh endpoint Soal atau Session yang dijaga. Soal ujian
 * besok bisa bocor lewat berkas, bukan lewat endpoint yang sudah diperiksa (TC-26, TC-27).
 * Karena itu jalur ini sengaja TIDAK didaftarkan {@code permitAll()} di {@link
 * com.eduscreen.app.config.SecurityConfig} dan MUST memeriksa {@code clientId} lewat {@link
 * ImageService#require} sebelum menyajikan satu byte pun.
 */
@RestController
public class ImageController {

    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    @GetMapping("/gambar/{id}")
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
}
