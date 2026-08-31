package com.eduscreen.app.modules.identity.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Menyajikan halaman masuk.
 *
 * <p>Halaman ini menggantikan halaman bawaan Spring Security, yang berhenti didaftarkan begitu
 * {@code authenticationEntryPoint} kustom dipasang untuk TC-30. Alih-alih mengakali heuristik
 * itu, halamannya dibuat sungguhan — ia juga yang pertama kali membuktikan tata letak dasar
 * dipakai.
 *
 * <p>Alur undangan dan reset password menyusul di T032.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
