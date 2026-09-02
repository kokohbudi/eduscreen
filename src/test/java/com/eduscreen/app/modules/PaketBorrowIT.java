package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketBorrowService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaketBorrowIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    PaketService pakets;
    @Autowired
    PaketBorrowService borrow;
    @Autowired
    QuestionRepository questions;
    @Autowired
    QuestionOptionRepository options;

    @Test
    @DisplayName("AC-B03: pinjam membuat salinan baru, bukan referensi ke soal asal")
    void borrowCopies() {
        ClientEntity client = data.client("SD Pinjam Salin");
        PaketEntity sumber = data.paket(client, "Matematika Kelas 4 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "Matematika Kelas 4 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Berapa 2 + 2?", 4);

        int tersalin = borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);

        assertThat(tersalin).isEqualTo(1);
        List<QuestionEntity> isiTujuan = questions.findByPaketIdOrderByPositionAsc(tujuan.getId());
        assertThat(isiTujuan).hasSize(1);
        assertThat(isiTujuan.get(0).getId()).isNotEqualTo(asal.getId());
        assertThat(isiTujuan.get(0).getSourceQuestionId()).isEqualTo(asal.getId());
        assertThat(isiTujuan.get(0).getBodyText()).isEqualTo(asal.getBodyText());
    }

    @Test
    @DisplayName("AC-B07 (FR-016): salinan membawa seluruh Option asal, lengkap jumlah, jawaban benar, dan urutannya")
    void borrowCopiesOptionsFully() {
        ClientEntity client = data.client("SD Pinjam Opsi");
        PaketEntity sumber = data.paket(client, "IPS Kelas 6 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "IPS Kelas 6 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Ibu kota Indonesia?", 4);
        List<QuestionOptionEntity> opsiAsal = options.findByQuestionIdOrderByPositionAsc(asal.getId());

        borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);

        QuestionEntity salinan = questions.findByPaketIdOrderByPositionAsc(tujuan.getId()).get(0);
        List<QuestionOptionEntity> opsiSalinan = options.findByQuestionIdOrderByPositionAsc(salinan.getId());

        assertThat(opsiSalinan).hasSameSizeAs(opsiAsal);
        for (int i = 0; i < opsiAsal.size(); i++) {
            assertThat(opsiSalinan.get(i).getBodyText()).isEqualTo(opsiAsal.get(i).getBodyText());
            assertThat(opsiSalinan.get(i).isCorrect()).isEqualTo(opsiAsal.get(i).isCorrect());
            assertThat(opsiSalinan.get(i).getPosition()).isEqualTo(opsiAsal.get(i).getPosition());
        }
        assertThat(opsiSalinan.stream().filter(QuestionOptionEntity::isCorrect).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-B04: soal yang sudah pernah dipinjam tidak muncul lagi di daftar pinjam")
    void borrowedQuestionIsHiddenNextTime() {
        ClientEntity client = data.client("SD Pinjam Sekali");
        PaketEntity sumber = data.paket(client, "IPA Kelas 5 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "IPA Kelas 5 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Air mendidih pada suhu?", 4);

        borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);

        assertThat(borrow.borrowedSourceIds(tujuan.getId())).contains(asal.getId());
    }

    @Test
    @DisplayName("AC-B04: meminjam soal yang sama dua kali tidak melahirkan salinan kedua")
    void borrowingTwiceDoesNotDuplicate() {
        ClientEntity client = data.client("SD Pinjam Dua Kali");
        PaketEntity sumber = data.paket(client, "Bahasa Indonesia Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "Bahasa Indonesia Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Sinonim dari 'cepat'?", 4);

        int pertama = borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);
        int kedua = borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);

        assertThat(pertama).isEqualTo(1);
        assertThat(kedua).isEqualTo(0);
        assertThat(questions.findByPaketIdOrderByPositionAsc(tujuan.getId())).hasSize(1);
    }

    @Test
    @DisplayName("TC-36: meminjam dari Paket milik Client lain menghasilkan nol salinan, bukan galat")
    void borrowingFromOtherClientYieldsZero() {
        ClientEntity pemilik = data.client("SD Pemilik Soal");
        ClientEntity peminjam = data.client("SD Peminjam Lain");
        PaketEntity sumber = data.paket(pemilik, "Matematika Kelas 3 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(peminjam, "Matematika Kelas 3 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.mcq(pemilik, topicSumber, "Soal milik SD lain", 4);

        int tersalin = borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), peminjam.getId(), null);

        assertThat(tersalin).isEqualTo(0);
        assertThat(questions.findByPaketIdOrderByPositionAsc(tujuan.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-B03: mengubah salinan tidak mengubah soal asal, termasuk bodyHtml-nya")
    void editingCopyLeavesSourceAlone() {
        ClientEntity client = data.client("SD Pinjam Ubah");
        PaketEntity sumber = data.paket(client, "Fisika Kelas 8 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "Fisika Kelas 8 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        QuestionEntity asal = data.essay(client, topicSumber, "Jelaskan hukum Newton I");
        String bodyHtmlAsal = asal.getBodyHtml();

        borrow.borrowQuestions(
                tujuan.getId(), topicTujuan.getId(), List.of(asal.getId()), client.getId(), null);
        QuestionEntity salinan = questions.findByPaketIdOrderByPositionAsc(tujuan.getId()).get(0);
        salinan.reparent(tujuan.getId(), topicTujuan.getId());
        salinan.moveTo(5);
        salinan.setBodyHtml("<p>Jelaskan hukum Newton I, sudah disunting</p>");
        salinan.setBodyText("Jelaskan hukum Newton I, sudah disunting");
        questions.save(salinan);

        QuestionEntity asalSetelahDiubah = questions.findById(asal.getId()).orElseThrow();
        assertThat(asalSetelahDiubah.getPosition()).isEqualTo(asal.getPosition());
        assertThat(asalSetelahDiubah.getBodyHtml()).isEqualTo(bodyHtmlAsal);
    }

    @Test
    @DisplayName("AC-B08: salinan mendarat di position berikutnya, bukan menumpuk di 0")
    void copiesLandOnNextPosition() {
        ClientEntity client = data.client("SD Pinjam Posisi");
        PaketEntity sumber = data.paket(client, "Kimia Kelas 10 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "Kimia Kelas 10 Pinjam", "Tujuan");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuan = pakets.topicsOf(tujuan.getId()).get(0);
        // Satu soal sudah lebih dulu ada di Paket tujuan, menempati position 0.
        data.mcq(client, topicTujuan, "Soal yang sudah ada duluan", 4);
        QuestionEntity asal1 = data.mcq(client, topicSumber, "Soal pinjaman pertama", 4);
        QuestionEntity asal2 = data.mcq(client, topicSumber, "Soal pinjaman kedua", 4);

        borrow.borrowQuestions(tujuan.getId(), topicTujuan.getId(),
                List.of(asal1.getId(), asal2.getId()), client.getId(), null);

        List<QuestionEntity> isiTujuan = questions.findByPaketIdOrderByPositionAsc(tujuan.getId());
        assertThat(isiTujuan).hasSize(3);
        assertThat(isiTujuan.get(0).getPosition()).isEqualTo(0);
        assertThat(isiTujuan.get(1).getPosition()).isEqualTo(1);
        assertThat(isiTujuan.get(2).getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-B04: borrowedSourceIds hanya melaporkan jejak pinjam Paket ini, bukan Paket lain")
    void borrowedSourceIdsIsScopedToTargetPaket() {
        ClientEntity client = data.client("SD Pinjam Cakupan");
        PaketEntity sumber = data.paket(client, "Sejarah Kelas 7 Pinjam", "Sumber");
        PaketEntity tujuanA = data.paket(client, "Sejarah Kelas 7 Pinjam", "Tujuan A");
        PaketEntity tujuanB = data.paket(client, "Sejarah Kelas 7 Pinjam", "Tujuan B");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        TopicEntity topicTujuanA = pakets.topicsOf(tujuanA.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Soal hanya dipinjam Tujuan A", 4);

        borrow.borrowQuestions(
                tujuanA.getId(), topicTujuanA.getId(), List.of(asal.getId()), client.getId(), null);

        assertThat(borrow.borrowedSourceIds(tujuanA.getId())).contains(asal.getId());
        assertThat(borrow.borrowedSourceIds(tujuanB.getId())).doesNotContain(asal.getId());
    }

    @Test
    @DisplayName("AC-B02: Topic tujuan yang bukan milik Paket tujuan ditolak")
    void borrowingIntoTopicFromAnotherPaketIsRejected() {
        ClientEntity client = data.client("SD Pinjam Topic Salah");
        PaketEntity sumber = data.paket(client, "Biologi Kelas 9 Pinjam", "Sumber");
        PaketEntity tujuan = data.paket(client, "Biologi Kelas 9 Pinjam", "Tujuan");
        PaketEntity lain = data.paket(client, "Biologi Kelas 9 Pinjam", "Paket Lain");
        TopicEntity topicSumber = pakets.topicsOf(sumber.getId()).get(0);
        // Topic ini milik Paket "lain", bukan milik "tujuan" — kombinasi yang harus ditolak.
        TopicEntity topicPaketLain = pakets.topicsOf(lain.getId()).get(0);
        QuestionEntity asal = data.mcq(client, topicSumber, "Soal AC-B02", 4);

        assertThatThrownBy(() -> borrow.borrowQuestions(
                tujuan.getId(), topicPaketLain.getId(), List.of(asal.getId()), client.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
