package com.eduscreen.app.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Beranda sementara.
 *
 * <p>Ada supaya tata letak dasar (T026) punya sesuatu untuk dirender dan alur login punya
 * tujuan setelah berhasil. Digantikan pengalihan portal berbasis peran di T035.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
