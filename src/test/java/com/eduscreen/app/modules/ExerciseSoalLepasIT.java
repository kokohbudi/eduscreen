package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Soal yang Guru tulis di dalam perakit Exercise (BR-E05, BR-E06). Dari sudut Guru tidak ada
 * istilah Paket: Paket hanya sumber referensi yang dibaca, sedangkan tulisannya sendiri hidup di
 * Exercise yang memuatnya — tanpa penempatan Paket sama sekali.
 */
class ExerciseSoalLepasIT extends PostgresTestBase {

    @Autowired TestData data;
    @Autowired ExerciseService exercises;
    @Autowired QuestionService questions;
    @Autowired QuestionRepository questionRepository;
    @Autowired PaketItemRepository paketItems;
    @Autowired ExerciseRepository exerciseRepository;

    private record Siap(ClientEntity sekolah, AppUserEntity guru, ExerciseEntity exercise) {
    }

    private Siap siap(String nama) {
        ClientEntity sekolah = data.client("SD Lepas " + nama);
        AppUserEntity guru = data.user(sekolah, UserRole.GURU, "Guru Lepas " + nama);
        ExerciseEntity exercise = exercises.create(sekolah.getId(), "Ulangan " + nama, guru.getId());
        return new Siap(sekolah, guru, exercise);
    }

    private QuestionEntity tulisDariPerakit(Siap s, String isi) {
        QuestionEntity soal = questions.create(
                QuestionService.draftOf(null, QuestionType.MULTIPLE_CHOICE, "<p>" + isi + "</p>", null,
                        List.of("Benar", "Salah"), 0),
                s.sekolah().getId(), null);
        exercises.addQuestion(s.exercise().getId(), soal.getId(), s.sekolah().getId());
        return soal;
    }

    private List<UUID> isi(ExerciseEntity exercise) {
        return exercises.itemsOf(exercise.getId()).stream().map(ExerciseItemEntity::getQuestionId).toList();
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): soal yang ditulis dari perakit lahir tanpa penempatan Paket dan langsung terpasang di Exercise")
    void soalLepasLahirTanpaPenempatan() {
        Siap s = siap("Lahir");

        QuestionEntity soal = tulisDariPerakit(s, "Ibu kota Jawa Barat lepas lahir");

        assertThat(soal.getClientId()).isEqualTo(s.sekolah().getId());
        assertThat(paketItems.findPlacements(List.of(soal.getId()))).isEmpty();
        assertThat(isi(s.exercise())).containsExactly(soal.getId());
    }

    @Test
    @DisplayName("AC-E07 (BR-E05): soal lepas tidak pernah muncul di panel referensi perakit")
    void soalLepasTidakMuncilDiPanelReferensi() {
        Siap s = siap("Panel");
        QuestionEntity lepas = tulisDariPerakit(s, "Soal lepas panel yang tak boleh muncul");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Lepas Panel", "Zat dan Wujudnya");
        QuestionEntity berpenempatan = data.mcq(s.sekolah(), topic, "Soal lepas panel berpenempatan", 4);

        List<UUID> hasil = questions.searchForBuilder(s.sekolah().getId(), null, null, null, null,
                        List.of(), "lepas panel", PageRequest.of(0, 20))
                .getContent().stream().map(QuestionEntity::getId).toList();

        assertThat(hasil).contains(berpenempatan.getId()).doesNotContain(lepas.getId());
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): soal lepas disunting dari perakit tanpa menyebut Paket")
    void soalLepasBisaDisunting() {
        Siap s = siap("Sunting");
        QuestionEntity soal = tulisDariPerakit(s, "Redaksi awal lepas sunting");

        questions.update(soal.getId(),
                QuestionService.draftOf(null, QuestionType.MULTIPLE_CHOICE, "<p>Redaksi baru lepas sunting</p>",
                        null, List.of("Benar", "Salah"), 1),
                s.sekolah().getId(), null);

        QuestionEntity sesudah = questions.require(soal.getId(), s.sekolah().getId());
        assertThat(sesudah.getBodyText()).contains("Redaksi baru lepas sunting");
        assertThat(paketItems.findPlacements(List.of(soal.getId()))).isEmpty();
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): soal yang punya penempatan Paket tidak bisa disunting lewat jalur perakit")
    void soalBerpenempatanTidakBisaDisuntingLewatJalurLepas() {
        Siap s = siap("Tolak");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Lepas Tolak", "Gaya dan Gerak");
        QuestionEntity berpenempatan = data.mcq(s.sekolah(), topic, "Soal lepas tolak berpenempatan", 4);

        assertThatThrownBy(() -> questions.update(berpenempatan.getId(),
                QuestionService.draftOf(null, QuestionType.MULTIPLE_CHOICE, "<p>Coba timpa</p>", null,
                        List.of("Benar", "Salah"), 0),
                s.sekolah().getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC-E06 (BR-E05): jalur soal lepas menolak Topic, bukan mengabaikannya diam-diam")
    void jalurLepasMenolakTopic() {
        Siap s = siap("Topik");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Lepas Topik", "Cahaya");

        assertThatThrownBy(() -> questions.create(
                QuestionService.draftOf(topic.getId(), QuestionType.MULTIPLE_CHOICE,
                        "<p>Soal lepas dengan Topic</p>", null, List.of("Benar", "Salah"), 0),
                s.sekolah().getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-E08 (BR-E05): melepas soal lepas dari Exercise terakhir ikut menghapus soalnya")
    void melepasSoalLepasMenghapusnya() {
        Siap s = siap("Hapus");
        QuestionEntity soal = tulisDariPerakit(s, "Soal lepas hapus");

        exercises.removeQuestion(s.exercise().getId(), soal.getId(), s.sekolah().getId());

        assertThat(isi(s.exercise())).isEmpty();
        assertThat(questionRepository.findById(soal.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-E08 (BR-E05): melepas soal yang punya penempatan Paket hanya melepasnya dari Exercise")
    void melepasSoalBerpenempatanTidakMenghapusnya() {
        Siap s = siap("Sisa");
        TopicEntity topic = data.topic(s.sekolah(), "IPA Lepas Sisa", "Energi");
        QuestionEntity berpenempatan = data.mcq(s.sekolah(), topic, "Soal lepas sisa berpenempatan", 4);
        exercises.addQuestion(s.exercise().getId(), berpenempatan.getId(), s.sekolah().getId());

        exercises.removeQuestion(s.exercise().getId(), berpenempatan.getId(), s.sekolah().getId());

        assertThat(isi(s.exercise())).isEmpty();
        assertThat(questionRepository.findById(berpenempatan.getId())).isPresent();
    }

    @Test
    @DisplayName("AC-E08 (BR-E05): soal lepas yang masih dipakai Exercise lain tidak ikut terhapus")
    void soalLepasYangMasihDipakaiTidakTerhapus() {
        Siap s = siap("Berbagi");
        QuestionEntity soal = tulisDariPerakit(s, "Soal lepas berbagi");
        ExerciseEntity kedua = exercises.create(s.sekolah().getId(), "Ulangan Berbagi Dua", s.guru().getId());
        exercises.addQuestion(kedua.getId(), soal.getId(), s.sekolah().getId());

        exercises.removeQuestion(s.exercise().getId(), soal.getId(), s.sekolah().getId());

        assertThat(questionRepository.findById(soal.getId())).isPresent();
        assertThat(isi(kedua)).containsExactly(soal.getId());
    }

    @Test
    @DisplayName("AC-E09 (BR-E06): Exercise lahir berjudul bawaan saat dibuat tanpa judul, lalu judulnya diubah dari perakit")
    void judulBawaanDanGantiJudul() {
        ClientEntity sekolah = data.client("SD Lepas Judul");
        AppUserEntity guru = data.user(sekolah, UserRole.GURU, "Guru Lepas Judul");

        ExerciseEntity exercise = exercises.create(sekolah.getId(), null, guru.getId());
        assertThat(exercise.getTitle()).isEqualTo(ExerciseService.JUDUL_BAWAAN);

        exercises.rename(exercise.getId(), "Ulangan Harian Pecahan", sekolah.getId());

        assertThat(exercises.require(exercise.getId(), sekolah.getId()).getTitle())
                .isEqualTo("Ulangan Harian Pecahan");
    }

    @Test
    @DisplayName("AC-E09 (BR-E06, BR-E04): Exercise terkunci menolak perubahan judul")
    void exerciseTerkunciMenolakGantiJudul() {
        Siap s = siap("Kunci");
        ExerciseEntity terkunci = exerciseRepository.findById(s.exercise().getId()).orElseThrow();
        terkunci.lock(OffsetDateTime.now());
        exerciseRepository.save(terkunci);

        assertThatThrownBy(() -> exercises.rename(s.exercise().getId(), "Judul baru", s.sekolah().getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
