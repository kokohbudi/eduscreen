package com.eduscreen.app.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Satu tempat seluruh galat dirender (TC-31).
 *
 * <p>Yang paling penting di sini adalah penanganan permintaan HTMX yang tidak terautentikasi
 * (TC-30). Spring Security bawaan membalas {@code 302} ke halaman login. HTMX mengikuti
 * pengalihan biasa lalu <b>menempelkan seluruh halaman login ke dalam slot fragmen</b> —
 * misalnya ke tengah lembar soal yang sedang dikerjakan Siswa. Yang benar adalah {@code 401}
 * berisi header {@code HX-Redirect} sehingga peramban berpindah halaman.
 */
@RestControllerAdvice
public class GlobalExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);
    private static final String HTMX_REQUEST_HEADER = "HX-Request";
    private static final String HTMX_REDIRECT_HEADER = "HX-Redirect";

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleUnauthenticated(HttpServletRequest request) {
        if (isHtmxRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HTMX_REDIRECT_HEADER, "/login")
                    .body(fragment("Sesi berakhir. Mengalihkan ke halaman masuk."));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/login")
                .build();
    }

    /**
     * Bahkan untuk akses yang ditolak, balasannya {@code 404}: seluruh permukaan bersasaran
     * tunduk pada aturan yang sama (TC-09).
     */
    @ExceptionHandler({ResourceNotFoundException.class, AccessDeniedException.class})
    public ResponseEntity<String> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(fragment("Data tidak ditemukan."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(fragment(escape(exception.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(fragment(escape(exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception exception) {
        // Pesan galat internal tidak pernah sampai ke pengguna; ia bisa membocorkan struktur
        // data dan keberadaan objek.
        log.error("Galat tak terduga", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(fragment("Terjadi kesalahan. Silakan coba lagi."));
    }

    private boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(HTMX_REQUEST_HEADER));
    }

    private String fragment(String message) {
        return "<div class=\"eduscreen-error\" role=\"alert\">" + message + "</div>";
    }

    private String escape(String raw) {
        if (raw == null) {
            return "Permintaan tidak sah.";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
