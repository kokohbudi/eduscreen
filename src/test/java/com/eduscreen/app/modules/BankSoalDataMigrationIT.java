package com.eduscreen.app.modules;

import com.eduscreen.app.support.PostgresTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Menjaga bagian migrasi yang <b>memindahkan data</b>, bukan yang mengubah bentuk tabel.
 *
 * <p>Suite biasa tidak bisa menjaganya: Flyway berjalan sekali saat konteks Spring naik, di
 * database yang masih kosong, sehingga setiap {@code insert ... select} dan {@code update} di
 * dalam migrasi tidak menyentuh satu baris pun. Dua kali cacat perpindahan data lolos karena
 * itu, dan keduanya baru ketahuan lewat percobaan manual yang hilang bersama databasenya.
 *
 * <p>Tes ini membuat database sendiri di container yang sama, menjalankan Flyway sampai V7 —
 * bentuk lama, sebelum Paket ada — menyeed data yang mewakili pola nyata, lalu melanjutkan
 * migrasi sampai V9 dan memeriksa hasilnya. Jumlah baris diperiksa <b>persis</b>, bukan
 * "lebih dari nol": cacat yang dijaga di sini adalah cacat yang berlipat diam-diam tanpa
 * membuat migrasinya gagal.
 */
class BankSoalDataMigrationIT extends PostgresTestBase {

    private static final String DB = "v9_regresi";

    private static final UUID CLIENT_A = UUID.fromString("11111111-1111-1111-1111-11111111000a");
    private static final UUID CLIENT_B = UUID.fromString("11111111-1111-1111-1111-11111111000b");
    private static final UUID SUBJECT_GLOBAL = UUID.fromString("22222222-2222-2222-2222-222222222001");
    private static final UUID SUBJECT_A = UUID.fromString("22222222-2222-2222-2222-222222222002");
    /** Topic GLOBAL yang hidup; tempat pola lama "soal sekolah di bawah Topic milik Eduscreen". */
    private static final UUID TOPIC_GLOBAL = UUID.fromString("33333333-3333-3333-3333-333333333001");
    /** Topic GLOBAL yang sudah dihapus lunak; salinannya harus ikut terhapus lunak (TC-35). */
    private static final UUID TOPIC_GLOBAL_TERHAPUS = UUID.fromString("33333333-3333-3333-3333-333333333002");
    /** Topic milik Client A sendiri; bukan pelanggar, harus dibiarkan apa adanya. */
    private static final UUID TOPIC_MILIK_A = UUID.fromString("33333333-3333-3333-3333-333333333003");

    @Test
    @DisplayName("TC-36 (TC-35): V9 memindahkan soal lintas pemilik ke tepat satu Paket per pasangan, membawa deleted_at, tanpa kehilangan satu baris pun")
    void pemindahanV9TidakBerlipatDanTidakKehilanganBaris() throws SQLException {
        buatDatabase();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource(DB));

            migrasiSampai("7");
            seedBentukLama(jdbc);
            migrasiSampai("9");

            // --- tidak ada baris yang hilang -------------------------------------------------
            assertThat(jdbc.queryForObject("select count(*) from question", Long.class))
                    .as("seluruh Question tetap ada; migrasi ini memindahkan, tidak membuang")
                    .isEqualTo(9L);

            // --- tidak ada yang masih melanggar batas tenant ---------------------------------
            assertThat(jdbc.queryForObject(
                    "select count(*) from question q join paket p on p.id = q.paket_id "
                            + "where q.client_id is not null and p.client_id is distinct from q.client_id",
                    Long.class))
                    .as("tidak ada Question yang pemiliknya berbeda dari pemilik Paketnya")
                    .isZero();

            // --- tepat satu Paket per pasangan (Client, Paket asal) --------------------------
            // Client A melanggar di dua Paket master (TOPIC_GLOBAL dan TOPIC_GLOBAL_TERHAPUS)
            // dan sudah punya satu Paket sendiri; totalnya tiga, bukan satu per soal pelanggar.
            assertThat(jdbc.queryForObject(
                    "select count(*) from paket where client_id = ?", Long.class, CLIENT_A))
                    .as("A: satu Paket miliknya sendiri + satu salinan per Paket master yang dilanggarnya")
                    .isEqualTo(3L);
            assertThat(jdbc.queryForObject(
                    "select count(*) from paket where client_id = ?", Long.class, CLIENT_B))
                    .as("B melanggar di satu Paket master saja")
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "select count(*) from paket where client_id is null", Long.class))
                    .as("Paket master tetap dua; V9 tidak menyentuhnya")
                    .isEqualTo(2L);

            // --- tepat satu Topic per Paket baru ---------------------------------------------
            // Inilah yang meledak kuadratik kalau dedup-nya rusak: tiga soal pelanggar di satu
            // Topic pernah melahirkan sembilan Topic.
            assertThat(jdbc.queryForObject(
                    "select count(*) from topic t join paket p on p.id = t.paket_id where p.client_id = ?",
                    Long.class, CLIENT_A))
                    .as("A: satu Topic per Paket miliknya, tidak berlipat mengikuti jumlah soal")
                    .isEqualTo(3L);
            assertThat(jdbc.queryForObject(
                    "select count(*) from topic t join paket p on p.id = t.paket_id where p.client_id = ?",
                    Long.class, CLIENT_B))
                    .isEqualTo(1L);

            // --- penempatan deterministik ----------------------------------------------------
            assertThat(jdbc.queryForObject(
                    "select count(distinct paket_id) from question where body_text like 'A-global-%'",
                    Long.class))
                    .as("tiga soal A di bawah satu Topic master mendarat di Paket yang sama")
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "select count(distinct topic_id) from question where body_text like 'A-global-%'",
                    Long.class))
                    .as("dan di Topic yang sama")
                    .isEqualTo(1L);

            // --- deleted_at terbawa ----------------------------------------------------------
            assertThat(jdbc.queryForObject(
                    "select count(*) from paket where client_id = ? and deleted_at is not null",
                    Long.class, CLIENT_A))
                    .as("salinan dari Paket master yang sudah dihapus lunak ikut terhapus lunak")
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "select count(*) from topic t join paket p on p.id = t.paket_id "
                            + "where p.client_id = ? and t.deleted_at is not null",
                    Long.class, CLIENT_A))
                    .as("begitu juga Topic salinannya")
                    .isEqualTo(1L);

            // --- yang bukan pelanggar tidak disentuh ------------------------------------------
            assertThat(jdbc.queryForObject(
                    "select count(*) from question where topic_id = ?", Long.class, TOPIC_MILIK_A))
                    .as("soal A di bawah Topic miliknya sendiri tetap di tempatnya")
                    .isEqualTo(1L);
        } finally {
            hapusDatabase();
        }
    }

    /**
     * Data dalam bentuk sebelum V8: Topic masih punya origin dan pemilik sendiri, Question masih
     * menempel langsung padanya. Sengaja lebih dari satu soal per pasangan (Client, Topic) —
     * pada satu soal per pasangan, cacat yang dijaga tes ini persis tidak terlihat.
     */
    private void seedBentukLama(JdbcTemplate jdbc) {
        jdbc.update("insert into client (id, name, timezone) values (?, ?, ?)",
                CLIENT_A, "SD Migrasi A", "Asia/Jakarta");
        jdbc.update("insert into client (id, name, timezone) values (?, ?, ?)",
                CLIENT_B, "SD Migrasi B", "Asia/Jakarta");

        jdbc.update("insert into subject (id, name, origin, client_id) values (?, ?, 'GLOBAL', null)",
                SUBJECT_GLOBAL, "Matematika Kelas 4");
        jdbc.update("insert into subject (id, name, origin, client_id) values (?, ?, 'CLIENT', ?)",
                SUBJECT_A, "Muatan Lokal", CLIENT_A);

        jdbc.update("insert into topic (id, subject_id, name, origin, client_id) "
                        + "values (?, ?, 'Pecahan', 'GLOBAL', null)",
                TOPIC_GLOBAL, SUBJECT_GLOBAL);
        jdbc.update("insert into topic (id, subject_id, name, origin, client_id, deleted_at) "
                        + "values (?, ?, 'Pecahan Lama', 'GLOBAL', null, now())",
                TOPIC_GLOBAL_TERHAPUS, SUBJECT_GLOBAL);
        jdbc.update("insert into topic (id, subject_id, name, origin, client_id) "
                        + "values (?, ?, 'Aksara Jawa', 'CLIENT', ?)",
                TOPIC_MILIK_A, SUBJECT_A, CLIENT_A);

        // Tiga soal A dan dua soal B di bawah SATU Topic master: inti kasusnya.
        for (int i = 1; i <= 3; i++) {
            soal(jdbc, CLIENT_A, TOPIC_GLOBAL, "A-global-" + i);
        }
        for (int i = 1; i <= 2; i++) {
            soal(jdbc, CLIENT_B, TOPIC_GLOBAL, "B-global-" + i);
        }
        // Dua soal A di bawah Topic master yang sudah dihapus lunak.
        for (int i = 1; i <= 2; i++) {
            soal(jdbc, CLIENT_A, TOPIC_GLOBAL_TERHAPUS, "A-terhapus-" + i);
        }
        // Satu soal master yang memang sah, dan satu soal A di Topic miliknya sendiri.
        soal(jdbc, null, TOPIC_GLOBAL, "master-1");
        soal(jdbc, CLIENT_A, TOPIC_MILIK_A, "A-lokal-1");
    }

    private void soal(JdbcTemplate jdbc, UUID clientId, UUID topicId, String teks) {
        jdbc.update("insert into question (id, client_id, topic_id, type, body_html, body_text) "
                        + "values (?, ?, ?, 'ESSAY', ?, ?)",
                UUID.randomUUID(), clientId, topicId, "<p>" + teks + "</p>", teks);
    }

    private void migrasiSampai(String versi) {
        Flyway.configure()
                .dataSource(dataSource(DB))
                .target(versi)
                .load()
                .migrate();
    }

    private DriverManagerDataSource dataSource(String database) {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort()
                        + "/" + database,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private void buatDatabase() throws SQLException {
        jalankanDiAdmin("drop database if exists " + DB + " with (force)");
        jalankanDiAdmin("create database " + DB);
    }

    private void hapusDatabase() throws SQLException {
        jalankanDiAdmin("drop database if exists " + DB + " with (force)");
    }

    /** {@code create database} tidak boleh berjalan di dalam transaksi, jadi lewat koneksi polos. */
    private void jalankanDiAdmin(String sql) throws SQLException {
        try (Connection connection = dataSource(POSTGRES.getDatabaseName()).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
