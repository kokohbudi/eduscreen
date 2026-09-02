package com.eduscreen.app.web;

import com.eduscreen.app.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Berkas statis yang tidak ada adalah {@code 404} biasa, bukan kegagalan internal.
 *
 * <p>Sebelum ini penangkap terakhir {@code @ExceptionHandler(Exception.class)} menelan
 * {@code NoResourceFoundException} dan memperlakukannya sebagai galat tak terduga: satu
 * {@code log.error} berikut stack trace penuh, dan balasan {@code 500}. Setiap tab browser yang
 * meminta {@code /favicon.ico} memicunya sekali, sehingga log operasional dipenuhi jejak yang
 * bukan masalah — dan galat sungguhan tenggelam di antaranya.
 */
@AutoConfigureMockMvc
class StaticResourceNotFoundTest extends PostgresTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("TC-30: berkas statis yang tidak ada dibalas 404, bukan 500")
    void berkasStatisTidakAdaDibalas404() throws Exception {
        mockMvc.perform(get("/css/berkas-yang-tidak-pernah-ada.css"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-30: favicon disajikan, sehingga permintaan bawaan browser tidak jadi galat")
    void faviconDisajikan() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }
}
