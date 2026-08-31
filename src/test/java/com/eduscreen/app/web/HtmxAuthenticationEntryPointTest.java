package com.eduscreen.app.web;

import com.eduscreen.app.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tes regresi untuk TC-30.
 *
 * <p>Implementasi pertama menaruh penanganan ini di {@code @ControllerAdvice} dan tidak pernah
 * bekerja: permintaan tak terautentikasi ditolak rangkaian filter sebelum controller mana pun
 * dipanggil. Kegagalannya diam — yang terlihat hanya {@code 302} biasa — sehingga tanpa tes ini
 * ia akan kembali menyelinap masuk.
 */
@AutoConfigureMockMvc
class HtmxAuthenticationEntryPointTest extends PostgresTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("TC-30: permintaan HTMX tak terautentikasi dibalas 401 + HX-Redirect")
    void htmxRequestGetsUnauthorizedWithRedirectHeader() throws Exception {
        mockMvc.perform(get("/").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));
    }

    @Test
    @DisplayName("TC-30: navigasi biasa tetap dialihkan ke halaman login")
    void browserNavigationStillRedirects() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost/login"));
    }

    @Test
    @DisplayName("Aset klien terlayani tanpa autentikasi; gambar soal tidak ikut dibebaskan")
    void clientAssetsArePublicButQuestionImagesAreNot() throws Exception {
        mockMvc.perform(get("/vendor/htmx/htmx.min.js")).andExpect(status().isOk());
        // Gambar soal dilayani endpoint berotorisasi (TC-26), bukan sebagai berkas statis.
        mockMvc.perform(get("/gambar/01920000-0000-7000-8000-000000000099"))
                .andExpect(status().is3xxRedirection());
    }
}
