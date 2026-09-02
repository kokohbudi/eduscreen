package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perakit Exercise benar-benar dirender, bukan sekadar dipetakan (alasan sama dengan
 * {@code BankSoalRenderTest}): galat templat pernah lolos seluruh tes layanan dan baru meledak
 * saat halamannya disentuh. Task 12 menulis ulang panel penelusuran dari Subject/Topic menjadi
 * Paket/Topic (ADR-0018) — di sinilah panel tulisan barunya disentuh lewat MockMvc (TC-13, TC-14).
 */
@AutoConfigureMockMvc
class ExerciseBuilderRenderTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;
    @Autowired ExerciseService exerciseService;
    @Autowired AssignmentPublishingService publishing;

    @Test
    @DisplayName("TC-13: perakit Exercise dirender dengan dropdown Paket dan panel hasil kosong tanpa galat templat")
    void perakitDirenderDenganDropdownPaket() throws Exception {
        ClientEntity client = data.client("SD Perakit Render");
        AppUserEntity guruEntity = data.user(client, UserRole.GURU, "Guru Render");
        var guru = user(data.principal(guruEntity));
        data.paket(client, "Matematika Kelas 4 Perakit Render", "Paket Render A");
        ExerciseEntity exercise = data.exercise(client, guruEntity, "Ulangan Render", List.of());

        mvc.perform(get("/exercise/{id}", exercise.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paket Render A")))
                .andExpect(content().string(containsString("-- Paket --")))
                .andExpect(content().string(containsString("Belum ada soal yang cocok.")))
                .andExpect(content().string(not(containsString("Subject"))));
    }

    @Test
    @DisplayName("BR-E01: panel /exercise/{id}/cari menyaring per Paket dan tidak membocorkan Paket lain, dirender tanpa galat templat")
    void panelCariMenyaringPerPaketDanDirender() throws Exception {
        ClientEntity client = data.client("SD Perakit Cari Render");
        AppUserEntity guruEntity = data.user(client, UserRole.GURU, "Guru Cari Render");
        var guru = user(data.principal(guruEntity));
        PaketEntity paketA = data.paket(client, "Matematika Kelas 4 Cari Render", "Paket Cari A");
        PaketEntity paketB = data.paket(client, "Matematika Kelas 4 Cari Render", "Paket Cari B");
        TopicEntity topicA = paketService.topicsOf(paketA.getId()).get(0);
        TopicEntity topicB = paketService.topicsOf(paketB.getId()).get(0);
        QuestionEntity soalA = data.mcq(client, topicA, "Soal render Paket A", 4);
        data.mcq(client, topicB, "Soal render Paket B", 4);
        ExerciseEntity exercise = data.exercise(client, guruEntity, "Ulangan Cari Render", List.of());

        mvc.perform(get("/exercise/{id}/cari", exercise.getId())
                        .param("paketId", paketA.getId().toString())
                        .with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soal render Paket A")))
                .andExpect(content().string(not(containsString("Soal render Paket B"))))
                .andExpect(content().string(containsString(
                        "hx-post=\"/exercise/" + exercise.getId() + "/item\"")));

        // BR-E01: soal lintas Paket tetap bisa ditambahkan meski panel sedang menyaring Paket A —
        // menyaring TAMPILAN panel tidak boleh diam-diam mengubah aturan Exercise-nya sendiri.
        mvc.perform(post("/exercise/{id}/item", exercise.getId())
                        .param("questionId", soalA.getId().toString())
                        .with(guru).with(csrf()))
                .andExpect(status().isOk());
        assertThat(exerciseService.itemsOf(exercise.getId()))
                .extracting(item -> item.getQuestionId())
                .contains(soalA.getId());
    }

    @Test
    @DisplayName("TC-36 (TC-09): panel /exercise/{id}/cari untuk Exercise milik Client lain dijawab 404, bukan menampilkan bank soal Client ini")
    void panelCariExerciseMilikClientLainMembalas404() throws Exception {
        ClientEntity clientA = data.client("SD Perakit Cari A");
        ClientEntity clientB = data.client("SD Perakit Cari B");
        var guruA = user(data.principal(data.user(clientA, UserRole.GURU, "Guru A Cari")));
        AppUserEntity guruB = data.user(clientB, UserRole.GURU, "Guru B Cari");
        ExerciseEntity exerciseB = data.exercise(clientB, guruB, "Ulangan Client B", List.of());

        mvc.perform(get("/exercise/{id}/cari", exerciseB.getId()).with(guruA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-E04 (FR-026): perakit Exercise terkunci menyembunyikan panel pencarian dan menolak penambahan item dengan 409")
    void perakitTerkunciMenyembunyikanPanelDanMenolakPenambahan() throws Exception {
        ClientEntity client = data.client("SD Perakit Terkunci Render");
        AppUserEntity guruEntity = data.user(client, UserRole.GURU, "Guru Terkunci Render");
        var guru = user(data.principal(guruEntity));
        RuanganEntity room = data.ruangan(client, "Kelas Terkunci Render");
        data.join(room, guruEntity, MemberRole.GURU);
        TopicEntity topic = data.topic(client, "IPA Kelas 4 Terkunci Render", "Ekosistem");
        QuestionEntity soal = data.mcq(client, topic, "Soal terkunci render", 4);
        QuestionEntity soalBaru = data.mcq(client, topic, "Soal baru terkunci render", 4);
        ExerciseEntity exercise = data.exercise(client, guruEntity, "Ulangan Terkunci Render", List.of(soal));

        // Mengunci lewat AssignmentPublishingService sungguhan (BR-M07): TestData.publishedQuiz
        // menulis Assignment langsung ke repository dan sengaja TIDAK mengunci Exercise-nya
        // (lihat AssignmentPublishingIT.addQuestionToLockedExerciseRejectedButDuplicateStaysEditable).
        UUID draftId = publishing.createDraft(new AssignmentPublishingService.PublishRequest(
                exercise.getId(), room.getId(), "Ulangan Terkunci Render", AssignmentMode.QUIZ, 30,
                OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                data.principal(guruEntity)).getId();
        publishing.publish(draftId, data.principal(guruEntity));

        mvc.perform(get("/exercise/{id}", exercise.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sudah diterbitkan dan terkunci")))
                .andExpect(content().string(not(containsString("Cari &amp; tambah soal"))));

        mvc.perform(post("/exercise/{id}/item", exercise.getId())
                        .param("questionId", soalBaru.getId().toString())
                        .with(guru).with(csrf()))
                .andExpect(status().isConflict());
    }
}
