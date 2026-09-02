package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
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

/** Membuktikan bentuk skema setelah V8, bukan perilaku layanan. */
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
    @DisplayName("AC-B02: question membawa paket_id dan position")
    void questionCarriesPaketAndPosition() {
        List<String> questionColumns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'question'",
                String.class);

        assertThat(questionColumns).contains("paket_id", "position", "topic_id");
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
     * {@code question_paket_same_owner} (V9) adalah alasan beberapa query di cabang ini aman
     * menghilangkan {@code clientId}: kepemilikan Question dijamin sama dengan kepemilikan Paket
     * induknya oleh database, bukan oleh kedisiplinan kode pemanggil (TC-36).
     *
     * <p>{@code BankSoalDataMigrationIT} membuktikan V9 MEMBERSIHKAN data lama. Yang belum
     * dibuktikan siapa pun: V9 MENCEGAH data baru. Tanpa tes ini, constraint-nya bisa hilang di
     * migrasi berikutnya dan seluruh suite tetap hijau.
     *
     * <p>Ditulis lewat JDBC mentah dengan sengaja: jalur layanan sudah dipagari
     * {@code TaxonomyService.requireWritableTopic}, jadi menembaknya dari sana hanya menguji
     * pagar aplikasi lagi — yang diuji di sini justru jaring terakhirnya kalau pagar itu jebol.
     */
    @Test
    @DisplayName("TC-36: composite FK question_paket_same_owner menolak Question milik Client masuk ke Paket master")
    void questionMilikClientDitolakMasukPaketMaster() {
        ClientEntity client = data.client("SD Batas FK Paket");
        PaketEntity master = data.masterPaket("Matematika Kelas 4 Batas FK", "Paket master batas FK");
        TopicEntity topicMaster = pakets.topicsOf(master.getId()).get(0);

        assertThatThrownBy(() -> sisipSoal(client.getId(), master.getId(), topicMaster.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_paket_same_owner");

        // Kontrol, supaya penolakan di atas tidak bisa lolos karena sebab lain (kolom wajib
        // yang terlewat, tipe salah): bentuk insert yang sama persis, cuma pemiliknya cocok.
        PaketEntity milikClient = data.paket(client, "Matematika Kelas 4 Batas FK", "Paket Client batas FK");
        TopicEntity topicClient = pakets.topicsOf(milikClient.getId()).get(0);
        assertThatCode(() -> sisipSoal(client.getId(), milikClient.getId(), topicClient.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> sisipSoal(null, master.getId(), topicMaster.getId()))
                .as("Question master di Paket master tetap boleh; FK MATCH SIMPLE melewatkan pasangan ber-null")
                .doesNotThrowAnyException();
    }

    private void sisipSoal(UUID clientId, UUID paketId, UUID topicId) {
        jdbc.update("insert into question "
                        + "(id, client_id, paket_id, topic_id, position, type, body_html, body_text) "
                        + "values (?, ?, ?, ?, 0, 'ESSAY', '<p>Batas FK</p>', 'Batas FK')",
                UUID.randomUUID(), clientId, paketId, topicId);
    }
}
