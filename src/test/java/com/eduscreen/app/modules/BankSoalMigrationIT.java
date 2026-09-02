package com.eduscreen.app.modules;

import com.eduscreen.app.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Membuktikan bentuk skema setelah V8, bukan perilaku layanan. */
class BankSoalMigrationIT extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

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
}
