package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.eduscreen.app.support.TestData.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T027 — IDOR pada manajemen akun dan Ruangan oleh Client Admin.
 *
 * <p>{@link com.eduscreen.app.modules.identity.controller.UserAdminController} dan
 * {@link com.eduscreen.app.modules.assessment.controller.RuanganAdminController} menyaring
 * {@code clientId} lewat service masing-masing sebelum entitasnya termuat (TC-08). Tes ini
 * membuktikan permukaan itu dari luar, lewat HTTP sungguhan, bukan hanya membaca kode.
 */
@AutoConfigureMockMvc
class AccountIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired AppUserRepository users;

    @Test
    @DisplayName("TC-09: pengguna Client lain dan pengguna yang tidak ada membalas 404 dengan body identik")
    void penggunaClientLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();

        MvcResult milikClientLain = mockMvc.perform(get("/admin/pengguna/{id}", tenants.b().siswa().getId())
                        .with(user(data.principal(tenants.a().admin()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(get("/admin/pengguna/{id}", UUID.randomUUID())
                        .with(user(data.principal(tenants.a().admin()))))
                .andExpect(status().isNotFound())
                .andReturn();

        // Membedakan "milik Client lain" dari "tidak pernah ada" mengubah tembok penolakan
        // menjadi oracle: penyerang bisa memetakan id mana yang sah hanya dari perbedaan pesan,
        // kode, atau bentuk body — bukan hanya dari kode status (TC-09).
        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClientLain.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TC-09: Ruangan Client lain dan Ruangan yang tidak ada membalas 404 dengan body identik")
    void ruanganClientLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();

        MvcResult milikClientLain = mockMvc.perform(get("/admin/ruangan/{id}", tenants.b().ruangan().getId())
                        .with(user(data.principal(tenants.a().admin()))))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(get("/admin/ruangan/{id}", UUID.randomUUID())
                        .with(user(data.principal(tenants.a().admin()))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClientLain.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TC-08: menambah anggota ke Ruangan Client lain ditolak 404")
    void tambahAnggotaKeRuanganClientLainDitolak() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(post("/admin/ruangan/{id}/anggota", tenants.b().ruangan().getId())
                        .with(user(data.principal(tenants.a().admin())))
                        .with(csrf())
                        .param("userIds", tenants.a().siswaLain().getId().toString())
                        .param("memberRole", "SISWA"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-09: menonaktifkan pengguna Client lain dibalas 404 dan statusnya tetap ACTIVE")
    void nonaktifkanPenggunaClientLainTidakMenulisApaPun() throws Exception {
        Tenants tenants = data.twoTenants();
        UUID targetId = tenants.b().siswa().getId();

        mockMvc.perform(post("/admin/pengguna/{id}/nonaktif", targetId)
                        .with(user(data.principal(tenants.a().admin())))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // 404 yang diam-diam tetap menonaktifkan akun adalah kegagalan yang lebih buruk
        // daripada 403: penyerang mendapat efek nyata sementara sistem berpura-pura objeknya
        // tidak ada. Batas tenant harus menutup jalur baca MAUPUN tulis sekaligus.
        UserStatus statusSesudahnya = users.findById(targetId).orElseThrow().getStatus();
        assertEquals(UserStatus.ACTIVE, statusSesudahnya);
    }
}
