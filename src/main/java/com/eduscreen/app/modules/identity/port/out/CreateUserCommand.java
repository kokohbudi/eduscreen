package com.eduscreen.app.modules.identity.port.out;

/** Permintaan pembuatan kredensial. Password awal tidak ikut: pengguna menetapkannya sendiri. */
public record CreateUserCommand(String email, String fullName) {
}
