package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Membuktikan bentuk skema setelah V8 dan V11, bukan perilaku layanan. */
class BankSoalMigrationIT extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestData data;
    @Autowired
    PaketService pakets;

    @Test
    @DisplayName("AC-B02: topic menunjuk paket, dan kolom taksonomi lamanya sudah tidak ada")
    void topicBelongsToPaket() {
        List<String> topicColumns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'topic'",
                String.class);

        assertThat(topicColumns).contains("paket_id", "title", "position");
        assertThat(topicColumns).doesNotContain("subject_id", "origin", "client_id", "source_topic_id", "name");
    }

    @Test
    @DisplayName("TC-36 (ADR-0021): question tidak lagi membawa penempatan; paket_item dan paket_version yang memikulnya")
    void questionTanpaPenempatan() {
        List<String> questionColumns = kolom("question");
        assertThat(questionColumns).doesNotContain("paket_id", "position", "topic_id");
        assertThat(questionColumns).contains("superseded_by_id");

        assertThat(kolom("paket_item"))
                .contains("paket_version_id", "client_id", "topic_id", "question_id", "position");
        assertThat(kolom("paket_version"))
                .contains("paket_id", "client_id", "nomor", "published_at", "superseded_at");
    }

    @Test
    @DisplayName("AC-B01 (ADR-0021): setiap Paket lahir dengan tepat satu versi kerja")
    void paketLahirDenganSatuVersiKerja() {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 Versi", "Paket versi kerja");
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);

        PaketVersionEntity versi = data.versionOf(topic);
        assertThat(versi.getPaketId()).isEqualTo(paket.getId());
        assertThat(versi.getNomor()).isEqualTo(1);
        assertThat(versi.isDraft()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from paket_version where paket_id = ?",
                Long.class, paket.getId())).isEqualTo(1L);
    }

    private List<String> kolom(String tabel) {
        return jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = ?",
                String.class, tabel);
    }

    @Test
    @DisplayName("TC-36 (BR-O02): paket membawa client_id sebagai dasar pemisahan tenant, subject_id yang dipakai bersama, dan source_paket_id sebagai jejak adopsi")
    void paketTableShape() {
        List<String> paketColumns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'paket'",
                String.class);

        assertThat(paketColumns)
                .contains("id", "client_id", "title", "subject_id", "published_at", "source_paket_id");
    }

    /**
     * {@code paket_item_same_owner} (V11, pewaris {@code question_paket_same_owner} V9) adalah
     * alasan beberapa query di cabang ini aman menghilangkan {@code clientId}: kepemilikan soal
     * dijamin sama dengan kepemilikan versi Paket yang memuatnya oleh database, bukan oleh
     * kedisiplinan kode pemanggil (TC-36).
     *
     * <p>Ditulis lewat JDBC mentah dengan sengaja: jalur layanan sudah dipagari
     * {@code TaxonomyService.requireWritableTopic}, jadi menembaknya dari sana hanya menguji
     * pagar aplikasi lagi — yang diuji di sini justru jaring terakhirnya kalau pagar itu jebol.
     */
    @Test
    @DisplayName("TC-36: composite FK paket_item_same_owner menolak soal milik Client ditempatkan di versi Paket master")
    void soalMilikClientDitolakMasukVersiMaster() {
        ClientEntity client = data.client("SD Batas FK Paket");
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Batas FK", "Paket master batas FK");
        TopicEntity topicMaster = pakets.topicsOf(master.getId()).get(0);
        UUID versiMaster = data.versionOf(topicMaster).getId();
        UUID soalClient = sisipSoal(client.getId());

        assertThatThrownBy(() -> sisipItem(versiMaster, topicMaster.getId(), soalClient, client.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("paket_item_same_owner");
        // paket_item_question_owner menyamakan client_id item dengan pemilik soalnya — selama
        // keduanya terisi. Item ber-client_id null lolos kedua FK (MATCH SIMPLE, seperti V9);
        // yang menutupnya adalah konstruktor PaketItemEntity yang selalu menyalin pemilik soal.
        // Sengaja tidak diuji sebagai penolakan: database memang tidak menjanjikannya.

        // Kontrol, supaya penolakan di atas tidak bisa lolos karena sebab lain (kolom wajib
        // yang terlewat, tipe salah): bentuk insert yang sama persis, cuma pemiliknya cocok.
        PaketEntity milikClient = data.paket(client, "Matematika Kelas 4 Batas FK", "Paket Client batas FK");
        TopicEntity topicClient = pakets.topicsOf(milikClient.getId()).get(0);
        assertThatCode(() -> sisipItem(data.versionOf(topicClient).getId(), topicClient.getId(),
                soalClient, client.getId()))
                .doesNotThrowAnyException();
        UUID soalMaster = sisipSoal(null);
        assertThatCode(() -> sisipItem(versiMaster, topicMaster.getId(), soalMaster, null))
                .as("Soal master di versi master tetap boleh; FK MATCH SIMPLE melewatkan pasangan ber-null")
                .doesNotThrowAnyException();
    }

    private UUID sisipSoal(UUID clientId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into question (id, client_id, type, body_html, body_text) "
                        + "values (?, ?, 'ESSAY', '<p>Batas FK</p>', 'Batas FK')", id, clientId);
        return id;
    }

    private void sisipItem(UUID versionId, UUID topicId, UUID questionId, UUID clientId) {
        jdbc.update("insert into paket_item (id, paket_version_id, client_id, topic_id, question_id, position) "
                        + "values (?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(), versionId, clientId, topicId, questionId);
    }
}
