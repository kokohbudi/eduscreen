package com.eduscreen.app.shared.web;

/**
 * Muatannya terbaca, tetapi aturan bisnisnya tidak terpenuhi — gerbang validasi penerbitan
 * (ADR-0003) adalah pemakai utamanya.
 *
 * <p>Dipisahkan dari {@code 400} supaya pesan yang menyebut <b>soal mana</b> penyebabnya bisa
 * dibedakan dari formulir yang sekadar salah bentuk. Guru yang menerbitkan Practice berisi satu
 * soal essay butuh tahu soal keberapa, bukan sekadar "permintaan tidak sah" (BR-M04).
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
