package com.eduscreen.app.web;

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
 * Manajemen Client berdiri di luar Client mana pun, jadi pagarnya berbeda dari layar lain: bukan
 * {@code clientId} milik pemanggil, melainkan peran pemanggil dan pasangan (Client, pengguna) di
 * jalur URL-nya.
 */
@AutoConfigureMockMvc
class EduscreenClientIdorTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;

    @Test
    @DisplayName("TC-09: Client Admin milik Client lain dan yang tidak ada membalas 404 identik")
    void adminClientLainDanTidakAdaMembalas404Identik() throws Exception {
        Tenants tenants = data.twoTenants();
        var eduscreenAdmin = user(data.principal(data.eduscreenAdmin()));
        UUID clientA = tenants.a().client().getId();

        MvcResult milikClientLain = mockMvc.perform(
                        post("/eduscreen/client/{id}/admin/{userId}/nonaktif",
                                clientA, tenants.b().admin().getId())
                                .with(eduscreenAdmin).with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult tidakAda = mockMvc.perform(
                        post("/eduscreen/client/{id}/admin/{userId}/nonaktif",
                                clientA, UUID.randomUUID())
                                .with(eduscreenAdmin).with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(tidakAda.getResponse().getContentAsString(),
                milikClientLain.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TC-09: Guru milik Client itu sendiri tidak bisa membuka layar manajemennya")
    void guruTidakBisaMembukaManajemenClient() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(get("/eduscreen/client/{id}", tenants.a().client().getId())
                        .with(user(data.principal(tenants.a().guru()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-09: Client Admin tidak bisa menghentikan layanan Client-nya sendiri")
    void clientAdminTidakBisaMenyuspendClientnya() throws Exception {
        Tenants tenants = data.twoTenants();

        mockMvc.perform(post("/eduscreen/client/{id}/status", tenants.a().client().getId())
                        .param("status", "SUSPENDED")
                        .with(user(data.principal(tenants.a().admin()))).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
