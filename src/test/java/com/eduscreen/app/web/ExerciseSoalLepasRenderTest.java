package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Satu layar merakit Exercise (BR-E05, BR-E06): memberi judul, mengambil soal referensi, dan
 * menulis soal sendiri terjadi di halaman yang sama. Dirender sungguhan lewat MockMvc dengan
 * alasan yang sama seperti {@code ExerciseBuilderRenderTest} — galat templat tidak terlihat oleh
 * tes layanan.
 */
@AutoConfigureMockMvc
class ExerciseSoalLepasRenderTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired ExerciseService exercises;
    @Autowired QuestionRepository questions;
    @Autowired ExerciseRepository exerciseRepository;

    private record Siap(ClientEntity sekolah, AppUserEntity guru, ExerciseEntity exercise) {
    }

    private Siap siap(String nama) {
        ClientEntity sekolah = data.client("SD Layar " + nama);
        AppUserEntity guru = data.user(sekolah, UserRole.GURU, "Guru Layar " + nama);
        ExerciseEntity exercise = data.exercise(sekolah, guru, "Ulangan Layar " + nama, List.of());
        return new Siap(sekolah, guru, exercise);
    }

    @Test
    @DisplayName("TC-13: perakit merender judul yang bisa disunting dan tombol tulis soal sendiri di layar yang sama")
    void perakitMerenderJudulDanTombolTulisSoal() throws Exception {
        Siap s = siap("Satu");

        mvc.perform(get("/exercise/{id}", s.exercise().getId()).with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "hx-put=\"/exercise/" + s.exercise().getId() + "/judul\"")))
                .andExpect(content().string(containsString(
                        "hx-get=\"/exercise/" + s.exercise().getId() + "/soal/baru\"")));
    }

    @Test
    @DisplayName("AC-E09 (BR-E06): buat Exercise tanpa judul mengalihkan ke perakit, dan judulnya disimpan dari sana")
    void buatTanpaJudulLaluGantiJudul() throws Exception {
        ClientEntity sekolah = data.client("SD Layar Judul");
        AppUserEntity guru = data.user(sekolah, UserRole.GURU, "Guru Layar Judul");

        String lokasi = mvc.perform(post("/exercise").with(user(data.principal(guru))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/exercise/")))
                .andReturn().getResponse().getHeader("Location");

        UUID id = UUID.fromString(lokasi.substring(lokasi.lastIndexOf('/') + 1));
        assertThat(exerciseRepository.findById(id).orElseThrow().getTitle())
                .isEqualTo(ExerciseService.JUDUL_BAWAAN);

        mvc.perform(put("/exercise/{id}/judul", id)
                        .param("title", "Ulangan Harian Bab 2")
                        .with(user(data.principal(guru))).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(exerciseRepository.findById(id).orElseThrow().getTitle())
                .isEqualTo("Ulangan Harian Bab 2");
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): editor soal baru dirender di dalam perakit tanpa menyebut Paket maupun Topic")
    void editorSoalBaruDirenderTanpaPaket() throws Exception {
        Siap s = siap("Editor");

        mvc.perform(get("/exercise/{id}/soal/baru", s.exercise().getId())
                        .with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/exercise/" + s.exercise().getId() + "/soal\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"topicTitle\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"paketId\""))));
    }

    @Test
    @DisplayName("TC-13 (BR-E05): perakit memuat skrip editor yang sama dengan Bank Soal, supaya editor WYSIWYG hidup di dalamnya")
    void perakitMemuatSkripEditor() throws Exception {
        Siap s = siap("Skrip");

        mvc.perform(get("/exercise/{id}", s.exercise().getId()).with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alpine.data('soalEditor'")))
                .andExpect(content().string(containsString("katex.min.js")));
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): editor soal baru membawa seed Alpine sendiri dan blok pilihan ganda")
    void editorSoalBaruMembawaSeedDanPilihanGanda() throws Exception {
        Siap s = siap("Seed");

        mvc.perform(get("/exercise/{id}/soal/baru", s.exercise().getId())
                        .with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("seedSoalEditor")))
                .andExpect(content().string(containsString("x-show=\"tipe === 'MULTIPLE_CHOICE'\"")))
                .andExpect(content().string(containsString("name=\"optionBody\"")));
    }

    @Test
    @DisplayName("TC-13 (BR-E05): kolom kaya dan pilihan jawaban sama-sama komponen rte, bukan satu editor sehalaman")
    void editorMemakaiKomponenRte() throws Exception {
        Siap s = siap("Komponen");

        String isi = mvc.perform(get("/exercise/{id}/soal/baru", s.exercise().getId())
                        .with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Isi soal, Pembahasan, dan pilihan jawaban: tiga instance komponen yang sama.
        assertThat(isi.split("x-data=\"rte\\(", -1).length - 1).isGreaterThanOrEqualTo(3);
        assertThat(isi).contains("ringkas: true");
        // Cabang \"kolom prosa vs pilihan jawaban\" di dalam satu editor sehalaman sudah tidak ada.
        assertThat(isi).doesNotContain("aktifOpsi");
        // Keadaan per kolom, tapi bilah alat dan pegangan gambar tunggal: satu soal berisi empat
        // pilihan jawaban berarti enam instance rte, dan enam bilah melayang di DOM adalah
        // salinan yang tidak pernah terlihat bersamaan.
        assertThat(isi.split("editor-bar-melayang", -1).length - 1).isEqualTo(1);
        assertThat(isi.split("editor-pegangan", -1).length - 1).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): menyimpan soal baru dari perakit memasangnya di Exercise dan membalas daftar item")
    void simpanSoalBaruDariPerakit() throws Exception {
        Siap s = siap("Simpan");

        mvc.perform(post("/exercise/{id}/soal", s.exercise().getId())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Berapa hasil 7 x 8 layar simpan</p>")
                        .param("optionBody", "56", "54")
                        .param("correctIndex", "0")
                        .with(user(data.principal(s.guru()))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Berapa hasil 7 x 8 layar simpan")));

        assertThat(exercises.itemsOf(s.exercise().getId())).hasSize(1);
    }

    @Test
    @DisplayName("TC-14 (AC-E05): paginasi panel referensi menukar fragmen lewat HTMX, bukan menavigasi ke fragmen telanjang")
    void paginasiPanelMemakaiHtmx() throws Exception {
        Siap s = siap("Halaman");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Layar Halaman", "Tata Surya");
        for (int i = 0; i < 12; i++) {
            data.mcq(s.sekolah(), topic, "Soal layar halaman nomor " + i, 4);
        }

        mvc.perform(get("/exercise/{id}/cari", s.exercise().getId())
                        .param("topicId", topic.getId().toString())
                        .with(user(data.principal(s.guru()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Berikutnya")))
                .andExpect(content().string(containsString(
                        "hx-get=\"/exercise/" + s.exercise().getId() + "/cari")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("href=\"/exercise/" + s.exercise().getId() + "/cari"))));
    }

    @Test
    @DisplayName("TC-09: menulis soal ke Exercise Client lain membalas 404, bukan menyusup ke perakitnya")
    void tulisSoalKeExerciseClientLainDitolak() throws Exception {
        Siap a = siap("Idor A");
        Siap b = siap("Idor B");

        mvc.perform(post("/exercise/{id}/soal", b.exercise().getId())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Soal selundupan layar idor</p>")
                        .param("optionBody", "Benar", "Salah")
                        .param("correctIndex", "0")
                        .with(user(data.principal(a.guru()))).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(exercises.itemsOf(b.exercise().getId())).isEmpty();
    }

    @Test
    @DisplayName("TC-09 (BR-E05): menyunting soal berpenempatan lewat jalur perakit membalas 404")
    void suntingSoalBerpenempatanLewatPerakitDitolak() throws Exception {
        Siap s = siap("Sunting");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Layar Sunting", "Suhu");
        QuestionEntity berpenempatan = data.mcq(s.sekolah(), topic, "Soal layar sunting berpenempatan", 4);

        mvc.perform(put("/exercise/{id}/soal/{soalId}", s.exercise().getId(), berpenempatan.getId())
                        .param("type", "MULTIPLE_CHOICE")
                        .param("bodyHtml", "<p>Timpa lewat perakit</p>")
                        .param("optionBody", "Benar", "Salah")
                        .param("correctIndex", "0")
                        .with(user(data.principal(s.guru()))).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(questions.findById(berpenempatan.getId()).orElseThrow().getBodyText())
                .contains("Soal layar sunting berpenempatan");
    }
}
