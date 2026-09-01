package com.eduscreen.app.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.HtmlUtils;

/**
 * Satu tempat seluruh galat dirender (TC-31).
 *
 * <p>Permintaan tak terautentikasi TIDAK ditangani di sini: rangkaian filter Spring Security
 * menolaknya sebelum controller mana pun dipanggil, sehingga penangan di lapisan ini tidak akan
 * pernah melihatnya. Itu pekerjaan {@code HtmxAwareAuthenticationEntryPoint} (TC-30).
 */
@RestControllerAdvice
public class GlobalExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);
    /**
     * Bahkan untuk akses yang ditolak, balasannya {@code 404}: seluruh permukaan bersasaran
     * tunduk pada aturan yang sama (TC-09).
     */
    @ExceptionHandler({ResourceNotFoundException.class, AccessDeniedException.class})
    public ResponseEntity<String> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(fragment("Data tidak ditemukan."));
    }

    /** Jendela pengerjaan sudah tertutup: batas waktu terlewat atau Assignment ditutup (BR-T08). */
    @ExceptionHandler(GoneException.class)
    public ResponseEntity<String> handleGone(GoneException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(fragment(escape(exception.getMessage())));
    }

    /** Gerbang validasi penerbitan: muatan terbaca, aturannya yang tidak terpenuhi (ADR-0003). */
    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<String> handleUnprocessable(UnprocessableException exception) {
        return ResponseEntity.unprocessableEntity().body(fragment(escape(exception.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(fragment(escape(exception.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(fragment(escape(exception.getMessage())));
    }

    /**
     * Berkas statis yang tidak ada adalah 404 biasa, bukan kegagalan internal.
     *
     * <p>Tanpa penangan ini ia jatuh ke {@link #handleUnexpected} dan menghasilkan satu
     * {@code log.error} berikut stack trace penuh untuk setiap permintaan — termasuk
     * {@code /favicon.ico} yang diminta setiap tab browser tanpa diminta siapa pun. Log
     * operasional lalu dipenuhi jejak yang bukan masalah, dan galat sungguhan tenggelam di
     * antaranya. Dicatat pada level debug: alamat yang salah masih berguna saat menelusuri
     * tautan aset yang putus.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleStaticNotFound(NoResourceFoundException exception) {
        log.debug("Berkas statis tidak ditemukan: {}", exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(fragment("Berkas tidak ditemukan."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception exception) {
        // Pesan galat internal tidak pernah sampai ke pengguna; ia bisa membocorkan struktur
        // data dan keberadaan objek.
        log.error("Galat tak terduga", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(fragment("Terjadi kesalahan. Silakan coba lagi."));
    }

    private String fragment(String message) {
        return "<div class=\"eduscreen-error\" role=\"alert\">" + message + "</div>";
    }

    private String escape(String raw) {
        return raw == null ? "Permintaan tidak sah." : HtmlUtils.htmlEscape(raw);
    }
}
