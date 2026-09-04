package com.eduscreen.app.web;

import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Halaman detail Client: identitas, layanan, dan Client Admin — tidak lebih (FR-083..FR-085). */
@AutoConfigureMockMvc
class EduscreenClientRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;

    @Test
    @DisplayName("AC-O03: detail Client merender form identitas, aksi layanan, dan Client Admin-nya")
    void detailMerenderKendaliManajemen() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(get("/eduscreen/client/{id}", tenants.a().client().getId())
                        .with(user(data.principal(data.eduscreenAdmin()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(tenants.a().client().getName())))
                .andExpect(content().string(containsString("Hentikan layanan")))
                .andExpect(content().string(containsString("Tambah dan undang")))
                .andExpect(content().string(containsString(tenants.a().admin().getEmail())));
    }

    @Test
    @DisplayName("BR-P04: detail Client tidak membuka jalan ke akun Guru, Siswa, atau Ruangan")
    void detailTidakMembocorkanDataOperasional() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(get("/eduscreen/client/{id}", tenants.a().client().getId())
                        .with(user(data.principal(data.eduscreenAdmin()))))
                .andExpect(status().isOk())
                // Akun Guru dan Siswa milik Client ini ada di database yang sama; yang menahannya
                // adalah lingkup query, bukan kebetulan tata letak.
                .andExpect(content().string(not(containsString(tenants.a().guru().getEmail()))))
                .andExpect(content().string(not(containsString(tenants.a().siswa().getEmail()))))
                .andExpect(content().string(not(containsString(tenants.a().ruangan().getName()))))
                // Client Admin milik Client lain tidak pernah ikut terbawa.
                .andExpect(content().string(not(containsString(tenants.b().admin().getEmail()))));
    }
}
