package com.eduscreen.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Setiap {@code <form method="post">} wajib memakai {@code th:action}: hanya lewat atribut itu
 * Thymeleaf-Spring menyisipkan token {@code _csrf} tersembunyi. Form massal Bank Soal sempat
 * ditulis {@code th:attr="action=..."} dan ditolak 403 di peramban tanpa satu tes MockMvc pun
 * merah — MockMvc memasang token lewat {@code .with(csrf())}, jadi ia tidak pernah melihat
 * HTML-nya kekurangan token. Tes statis ini yang menutup lubang itu, tanpa Spring.
 *
 * <p>Form HTMX ({@code hx-post}/{@code hx-put}/…) tidak diperiksa di sini: tokennya datang dari
 * {@code hx-headers} di {@code <body>} (layout/base), bukan dari input tersembunyi.
 */
class FormCsrfGuardTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    /** Satu tag <form ...> utuh, lintas baris. */
    private static final Pattern FORM = Pattern.compile("<form\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern METHOD_POST = Pattern.compile("method\\s*=\\s*\"post\"", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("TC-30: form POST biasa memakai th:action supaya token _csrf ikut terkirim")
    void formPostMemakaiThAction() throws IOException {
        List<String> pelanggar = new ArrayList<>();
        try (Stream<Path> berkas = Files.walk(TEMPLATES)) {
            for (Path p : berkas.filter(f -> f.toString().endsWith(".html")).toList()) {
                String html = Files.readString(p);
                Matcher m = FORM.matcher(html);
                while (m.find()) {
                    String tag = m.group();
                    boolean postBiasa = METHOD_POST.matcher(tag).find();
                    boolean pakaiThAttrAction = tag.contains("th:attr=\"action=");
                    if ((postBiasa && !tag.contains("th:action=")) || pakaiThAttrAction) {
                        pelanggar.add(TEMPLATES.relativize(p) + ": " + tag.replaceAll("\\s+", " "));
                    }
                }
            }
        }
        assertThat(pelanggar).as("form POST tanpa th:action (token _csrf tidak akan tersisip)").isEmpty();
    }
}
