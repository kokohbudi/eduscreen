package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.modules.assessment.service.PaketBorrowService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guru merakit Exercise langsung dari soal master lewat akses Paket (ADR-0021). */
class ExerciseBuilderAccessIT extends PostgresTestBase {

    @Autowired TestData data;
    @Autowired PaketService pakets;
    @Autowired PaketAccessService access;
    @Autowired MasterPublishingService publishing;
    @Autowired ExerciseService exercises;
    @Autowired PaketBorrowService borrow;
    @Autowired QuestionRepository questions;

    private record Siap(ClientEntity sekolah, AppUserEntity guru, PaketEntity master, TopicEntity topic,
                        QuestionEntity terbit1, QuestionEntity terbit2, QuestionEntity draf, ExerciseEntity exercise) {
    }

    private Siap siap(String nama, boolean beriAkses) {
        ClientEntity sekolah = data.client("SD Builder " + nama);
        AppUserEntity guru = data.user(sekolah, UserRole.GURU, "Guru " + nama);
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Builder " + nama, "Paket Builder " + nama);
        TopicEntity topic = pakets.topicsOf(master.getId()).get(0);
        QuestionEntity terbit1 = data.publishedMasterMcq(topic, "Soal builder " + nama + " satu");
        QuestionEntity terbit2 = data.publishedMasterMcq(topic, "Soal builder " + nama + " dua");
        QuestionEntity draf = data.masterMcq(topic, "Soal builder " + nama + " draf");
        publishing.publishPaket(master.getId());
        if (beriAkses) {
            access.grant(sekolah.getId(), master.getId(), null, null);
        }
        ExerciseEntity exercise = exercises.create(sekolah.getId(), "Ulangan " + nama, guru.getId());
        return new Siap(sekolah, guru, master, topic, terbit1, terbit2, draf, exercise);
    }

    private List<java.util.UUID> isi(ExerciseEntity exercise) {
        return exercises.itemsOf(exercise.getId()).stream().map(ExerciseItemEntity::getQuestionId).toList();
    }

    @Test
    @DisplayName("BR-E01 (ADR-0021): 'Tambah seluruh Paket' memasang soal terbit master sebagai referensi — hanya exercise_item yang lahir, draf master tidak ikut")
    void tambahSeluruhPaket() {
        Siap s = siap("Bulat", true);
        long soalSebelum = questions.count();

        int ditambah = exercises.addPaket(s.exercise().getId(), s.master().getId(), s.sekolah().getId());

        assertThat(ditambah).isEqualTo(2);
        assertThat(isi(s.exercise())).containsExactly(s.terbit1().getId(), s.terbit2().getId());
        assertThat(questions.count()).isEqualTo(soalSebelum);
        // Sekali lagi tidak menggandakan.
        assertThat(exercises.addPaket(s.exercise().getId(), s.master().getId(), s.sekolah().getId())).isZero();
    }

    @Test
    @DisplayName("BR-E01 (ADR-0021): Guru boleh mengurangi dan mencampur — item master dibuang, soal sekolah sendiri ditambah")
    void kurangiDanCampur() {
        Siap s = siap("Campur", true);
        exercises.addPaket(s.exercise().getId(), s.master().getId(), s.sekolah().getId());
        TopicEntity topicSekolah = data.topic(s.sekolah(), "Matematika Kelas 4 Campur", "Buatan sendiri");
        QuestionEntity sendiri = data.mcq(s.sekolah(), topicSekolah, "Soal buatan sekolah campur", 4);

        exercises.removeQuestion(s.exercise().getId(), s.terbit2().getId(), s.sekolah().getId());
        exercises.addQuestion(s.exercise().getId(), sendiri.getId(), s.sekolah().getId());

        assertThat(isi(s.exercise())).containsExactly(s.terbit1().getId(), sendiri.getId());
    }

    @Test
    @DisplayName("TC-41 (TC-36): tanpa akses, soal master tidak bisa dipasang, Paket tidak bisa ditambahkan, Topic-nya menghasilkan nol, dan pinjam menghasilkan nol")
    void tanpaAksesSemuaTertutup() {
        Siap s = siap("Tertutup", false);

        assertThatThrownBy(() -> exercises.addQuestion(s.exercise().getId(), s.terbit1().getId(), s.sekolah().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(exercises.addQuestions(s.exercise().getId(),
                List.of(s.terbit1().getId(), s.terbit2().getId()), s.sekolah().getId())).isZero();
        assertThat(exercises.addTopic(s.exercise().getId(), s.topic().getId(), s.sekolah().getId())).isZero();
        assertThatThrownBy(() -> exercises.addPaket(s.exercise().getId(), s.master().getId(), s.sekolah().getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        PaketEntity tujuan = data.paket(s.sekolah(), "Matematika Kelas 4 Tertutup", "Paket tujuan pinjam");
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        assertThat(borrow.borrowQuestions(tujuan.getId(), topicTujuan.getId(),
                List.of(s.terbit1().getId()), s.sekolah().getId(), null)).isZero();
        assertThat(borrow.borrowTopic(tujuan.getId(), topicTujuan.getId(), s.topic().getId(),
                s.sekolah().getId(), null)).isZero();
        assertThat(isi(s.exercise())).isEmpty();
    }

    @Test
    @DisplayName("AC-B03 (ADR-0021): dengan akses, pinjam menyalin soal master ke Paket sekolah — satu-satunya jalan soal master jadi baris milik sekolah")
    void pinjamDenganAksesMenyalin() {
        Siap s = siap("Pinjam", true);
        PaketEntity tujuan = data.paket(s.sekolah(), "Matematika Kelas 4 Pinjam", "Paket tujuan pinjam");
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);

        int tersalin = borrow.borrowTopic(tujuan.getId(), topicTujuan.getId(), s.topic().getId(),
                s.sekolah().getId(), null);

        assertThat(tersalin).as("hanya soal terbit yang bisa dipinjam; draf master tidak").isEqualTo(2);
        List<QuestionEntity> salinan = data.questionsInPaket(tujuan.getId());
        assertThat(salinan).extracting(QuestionEntity::getClientId).containsOnly(s.sekolah().getId());
        assertThat(salinan).extracting(QuestionEntity::getSourceQuestionId)
                .containsExactlyInAnyOrder(s.terbit1().getId(), s.terbit2().getId());
    }

    @Test
    @DisplayName("BR-S02 (FR-068): mencabut akses tidak menyentuh Exercise yang sudah dirakit — itemnya tetap menunjuk soal master")
    void cabutTidakMenyentuhExercise() {
        Siap s = siap("Cabut", true);
        exercises.addPaket(s.exercise().getId(), s.master().getId(), s.sekolah().getId());

        access.revoke(access.activeFor(s.sekolah().getId()).get(0).getId());

        assertThat(isi(s.exercise())).containsExactly(s.terbit1().getId(), s.terbit2().getId());
        assertThat(questions.findAllForSnapshot(isi(s.exercise()))).hasSize(2);
        assertThatThrownBy(() -> exercises.addQuestion(s.exercise().getId(), s.terbit1().getId(), s.sekolah().getId()))
                .as("pemakaian baru tertutup").isInstanceOf(ResourceNotFoundException.class);
    }
}
