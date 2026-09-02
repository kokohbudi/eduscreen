package com.eduscreen.app.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Menyediakan jalur permintaan ke setiap templat sebagai {@code jalurAktif}, supaya shell di
 * {@code layout/base.html} bisa menandai item navigasi yang sedang aktif tanpa setiap
 * controller harus mengingat menaruh {@code menuAktif} sendiri. Keadaan aktif dengan begitu
 * dirender server dan ikut terjaga tes render (TC-13).
 */
@ControllerAdvice
public class JalurAktifAdvice {

    @ModelAttribute("jalurAktif")
    public String jalurAktif(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
