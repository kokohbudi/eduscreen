package com.eduscreen.app.shared.web;

/**
 * Objeknya sah dan boleh dilihat pemanggil, tetapi jendelanya sudah tertutup: Assignment
 * kedaluwarsa, atau jawaban yang tiba setelah {@code effective_deadline} (BR-T08).
 *
 * <p>Dibedakan dari {@code 404} dengan sengaja. Di sini keberadaan objek memang sudah diketahui
 * pemanggil — ia baru saja mengerjakannya — sehingga {@code 410} tidak membocorkan apa pun,
 * sementara pesan "sudah lewat waktu" adalah satu-satunya jawaban yang bisa dipahami Siswa.
 */
public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }
}
