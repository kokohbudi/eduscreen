package com.eduscreen.app.modules.assessment.service;

/**
 * Soal master yang sudah terbit tidak diubah di tempat: teksnya sedang dibaca versi Paket terbit,
 * Exercise, dan sesi sekolah. Perubahan dilakukan lewat revisi ({@code QuestionService.revise}),
 * yang melahirkan baris baru (ADR-0021). 409 lewat {@code GlobalExceptionAdvice}.
 */
public class QuestionFrozenException extends IllegalStateException {

    public QuestionFrozenException() {
        super("Soal ini sudah terbit dan beku. Simpan perubahan sebagai revisi.");
    }
}
