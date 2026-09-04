package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Halaman Ruangan baru (BR-U05): nama dan anggota pertamanya lahir dari satu kiriman. */
@AutoConfigureMockMvc
class RuanganRenderTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired RuanganService ruanganService;

    @Test
    @DisplayName("AC-U05 (BR-U05): Ruangan lahir bersama Guru dan Siswa-nya dari halaman Ruangan baru, lalu mendarat di detailnya")
    void ruanganLahirBersamaAnggota() throws Exception {
        ClientEntity client = data.client("SD Ruangan Render");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Ruangan Render")));
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru Ruangan Render");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa Ruangan Render");
        // Akun Client lain tidak boleh ditawarkan sebagai kandidat (TC-08).
        AppUserEntity asing = data.user(data.client("SD Ruangan Lain"), UserRole.SISWA, "Siswa Client Lain Render");

        mvc.perform(get("/admin/ruangan").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/ruangan/baru\"")));
        mvc.perform(get("/admin/ruangan/baru").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/admin/ruangan\"")))
                .andExpect(content().string(containsString(guru.getEmail())))
                .andExpect(content().string(containsString(siswa.getEmail())))
                .andExpect(content().string(not(containsString(asing.getEmail()))));

        String location = mvc.perform(post("/admin/ruangan")
                        .param("name", "Kelas 5A Render 2026/2027")
                        .param("guruIds", guru.getId().toString())
                        .param("siswaIds", siswa.getId().toString(), asing.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/admin/ruangan/")))
                .andReturn().getResponse().getHeader("Location");

        UUID ruanganId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        assertThat(ruanganService.membersOf(ruanganId, client.getId(), MemberRole.GURU))
                .extracting(AppUserEntity::getId).containsExactly(guru.getId());
        // Id akun Client lain yang diselundupkan lewat daftar tidak pernah jadi anggota (TC-08).
        assertThat(ruanganService.membersOf(ruanganId, client.getId(), MemberRole.SISWA))
                .extracting(AppUserEntity::getId).containsExactly(siswa.getId());
        mvc.perform(get(location).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kelas 5A Render 2026/2027")))
                .andExpect(content().string(containsString("Guru Ruangan Render")));
    }
}
