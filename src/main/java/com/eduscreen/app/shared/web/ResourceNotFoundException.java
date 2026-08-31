package com.eduscreen.app.shared.web;

/**
 * Objek tidak ada, <b>atau</b> bukan milik pemanggil.
 *
 * <p>Kedua sebab itu sengaja tidak dibedakan. Membalas {@code 403} untuk milik orang lain
 * memberi tahu penyerang bahwa pengenal itu sah, mengubah tembok menjadi oracle yang bisa
 * ditanyai (TC-09).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
