package com.eduscreen.app.modules.assessment.service;

import java.util.UUID;

/**
 * Paket master ini tidak punya versi kerja: seluruh versinya sudah terbit dan beku (ADR-0021).
 * Menulis ke dalamnya butuh keputusan pengguna lebih dulu — versi baru atau instance baru — dan
 * keputusan itu tidak boleh diambil diam-diam oleh kode. Turunan {@link IllegalStateException}
 * supaya jatuh ke 409 lewat {@code GlobalExceptionAdvice} bila tidak ditangkap controller.
 */
public class NeedsVersionChoiceException extends IllegalStateException {

    private final UUID paketId;

    public NeedsVersionChoiceException(UUID paketId) {
        super("Paket ini sudah terbit dan beku. Buat versi baru atau instance baru sebelum mengubah isinya.");
        this.paketId = paketId;
    }

    public UUID getPaketId() {
        return paketId;
    }
}
