package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.QuestionImportParser;
import com.eduscreen.app.modules.assessment.service.QuestionImportService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.shared.web.UnprocessableException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T100 — Impor massal soal (AC-Q03, AC-Q06) ke dalam Paket yang dipilih (ADR-0018).
 *
 * <p>Kolom berkas mengikuti {@code QuestionImportParser}: {@code
 * topic,tipe,soal,opsi_a,opsi_b,opsi_c,opsi_d,kunci,pembahasan}, baris pertama header. Tujuan
 * baris BUKAN lagi dicocokkan dari kolom {@code topic} berkas: seluruh baris valid mendarat di
 * Paket dan Topic yang dipilih di layar impor.
 */
class QuestionImportIT extends PostgresTestBase {

    private static final String HEADER = "topic,tipe,soal,opsi_a,opsi_b,opsi_c,opsi_d,kunci,pembahasan";

    @Autowired
    private TestData testData;
    @Autowired
    private QuestionImportService importService;
    @Autowired
    private QuestionRepository questionRepository;

    @Test
    @DisplayName("AC-Q06: berkas 2.000 baris ditolak sebelum diproses, dengan pesan yang menyebut batas 500")
    void ac_q06_berkasMelebihiBatasDitolakSebelumDiproses() {
        ClientEntity client = testData.client("SD Impor Raksasa");
        PaketEntity paket = testData.paket(client, "Matematika Kelas 4", "Paket Raksasa");
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 2000; i++) {
            csv.append("Aljabar,PG,Soal ").append(i)
                    .append(",Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        // countRows() menolak berkas HANYA dari jumlah barisnya, sebelum satu baris pun diuraikan
        // isinya (TC-45) — jadi pesannya tidak boleh memuat laporan per baris, hanya total dan
        // batasnya.
        UnprocessableException ex = assertThrows(UnprocessableException.class,
                () -> importService.preview("soal-raksasa.csv", bytes, paket.getId(), client.getId()));
        assertThat(ex.getMessage()).contains("500");
        assertThat(ex.getMessage()).doesNotContain("baris ke");
    }

    @Test
    @DisplayName("AC-Q03: 3 dari 20 baris tanpa kunci jawaban gagal bernomor dan beralasan; commit hanya menyimpan 17 yang valid")
    void ac_q03_barisTanpaKunciGagalDenganAlasanDanCommitHanyaMenyimpanYangValid() {
        ClientEntity client = testData.client("SD Impor Valid");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Impor");
        // 20 baris dipakai, bukan 500 seperti di spec: yang diuji di sini adalah bahwa BARIS
        // GAGAL tidak membatalkan baris lain dan tetap bernomor benar, bukan daya tampung
        // berkasnya — itu sudah dibuktikan terpisah oleh AC-Q06.
        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        PaketEntity paket = testData.paketOf(topic);

        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 15; i++) {
            csv.append("Aljabar,PG,Soal PG ").append(i)
                    .append(",Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan ").append(i).append('\n');
        }
        for (int i = 1; i <= 2; i++) {
            csv.append("Aljabar,ESSAY,Soal essai ").append(i).append(",,,,,,\n");
        }
        for (int i = 1; i <= 3; i++) {
            // Kunci sengaja dikosongkan (kolom kosong di antara dua koma) — inilah 3 baris yang
            // harus gagal.
            csv.append("Aljabar,PG,Soal tanpa kunci ").append(i)
                    .append(",Pilihan A,Pilihan B,Pilihan C,Pilihan D,,Pembahasan invalid ").append(i).append('\n');
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview =
                importService.preview("soal-valid.csv", bytes, paket.getId(), client.getId());
        assertThat(preview.validCount()).isEqualTo(17);
        assertThat(preview.failures()).hasSize(3);
        // Baris data ke-18, 19, 20 (setelah header di baris 1) jatuh di nomor baris 19, 20, 21 —
        // penomoran yang dilaporkan ke pengguna memakai baris berkas asli, bukan indeks internal.
        assertThat(preview.failures()).extracting(QuestionImportParser.RowFailure::lineNumber)
                .containsExactlyInAnyOrder(19, 20, 21);
        assertThat(preview.failures())
                .allSatisfy(failure -> assertThat(failure.reason().toLowerCase()).contains("kunci"));

        QuestionImportService.ImportSummary summary = importService.commit(
                preview.token(), paket.getId(), topic.getId(), client.getId(), author.getId());
        assertThat(summary.saved()).isEqualTo(17);

        List<QuestionEntity> tersimpan = questionRepository
                .findByClientIdAndTopicIdOrderByCreatedAtDesc(client.getId(), topic.getId());
        assertThat(tersimpan).hasSize(17);
        assertThat(tersimpan).allSatisfy(q -> assertThat(q.getPaketId()).isEqualTo(paket.getId()));
    }

    @Test
    @DisplayName("AC-Q03: token pratinjau yang tidak dikenal ditolak dengan IllegalArgumentException")
    void ac_q03_tokenPratinjauTidakDikenalDitolak() {
        ClientEntity client = testData.client("SD Token Asing");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Token");
        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");

        assertThrows(IllegalArgumentException.class,
                () -> importService.commit("token-tidak-pernah-ada",
                        topic.getPaketId(), topic.getId(), client.getId(), author.getId()));
    }

    @Test
    @DisplayName("AC-Q07: seluruh baris mendarat di Paket dan Topic yang dipilih; kolom topic berkas tidak menentukan tujuan")
    void barisImporMendaratDiPaketDanTopicYangDipilih() {
        ClientEntity client = testData.client("SD Tujuan Eksplisit");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Tujuan");
        // Ada Topic bernama "Aljabar" di Paket LAIN milik Client yang sama — persis nama yang
        // ditulis di kolom topic berkas. Kalau pencocokan nama lama masih hidup, baris-baris ini
        // akan nyasar ke sana, bukan ke Topic yang dipilih.
        TopicEntity topicAljabar = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        TopicEntity topicTujuan = testData.topic(client, "Matematika Kelas 4", "Geometri");
        PaketEntity paketTujuan = testData.paketOf(topicTujuan);

        String csv = HEADER + "\n"
                + "Aljabar,PG,Soal satu,Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan\n"
                + "Aljabar,ESSAY,Soal dua,,,,,,\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview =
                importService.preview("soal-tujuan.csv", bytes, paketTujuan.getId(), client.getId());
        QuestionImportService.ImportSummary summary = importService.commit(
                preview.token(), paketTujuan.getId(), topicTujuan.getId(), client.getId(), author.getId());

        assertThat(summary.saved()).isEqualTo(2);
        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topicTujuan.getId()))
                .hasSize(2)
                .allSatisfy(q -> assertThat(q.getPaketId()).isEqualTo(paketTujuan.getId()));
        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topicAljabar.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-B08: soal hasil impor mendarat di urutan berikutnya Topic tujuan, tidak menumpuk di posisi soal yang sudah ada")
    void ac_b08_soalImporMendaratDiPosisiBerikutnya() {
        ClientEntity client = testData.client("SD Posisi Berurut");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Posisi");
        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        // Topic tujuan SUDAH berisi satu soal di posisi 0 — impor tidak boleh menimpanya.
        testData.mcq(client, topic, "Soal lama", 4);

        String csv = HEADER + "\n"
                + "Aljabar,PG,Soal impor satu,Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan\n"
                + "Aljabar,PG,Soal impor dua,Pilihan A,Pilihan B,Pilihan C,Pilihan D,B,Pembahasan\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview =
                importService.preview("soal-posisi.csv", bytes, topic.getPaketId(), client.getId());
        importService.commit(preview.token(), topic.getPaketId(), topic.getId(),
                client.getId(), author.getId());

        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topic.getId()))
                .extracting(QuestionEntity::getPosition)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("TC-36: mengimpor ke Paket milik Client lain dijawab 404, di pratinjau maupun saat menyimpan")
    void tc_36_imporKePaketClientLainDitolak404() {
        ClientEntity clientA = testData.client("SD Klien A");
        ClientEntity clientB = testData.client("SD Klien B");
        AppUserEntity authorA = testData.user(clientA, UserRole.CLIENT_ADMIN, "Admin A");
        TopicEntity topicA = testData.topic(clientA, "Matematika Kelas 4", "Aljabar");
        PaketEntity paketB = testData.paket(clientB, "Matematika Kelas 4", "Paket Milik B");

        String csv = HEADER + "\n"
                + "Aljabar,PG,Soal lintas tenant,Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        // Pratinjau sudah menolak: Paket milik Client lain tidak ditemukan, bukan terlarang.
        assertThrows(ResourceNotFoundException.class,
                () -> importService.preview("lintas.csv", bytes, paketB.getId(), clientA.getId()));

        // Token sah milik sendiri pun tidak bisa dibelokkan ke Paket Client lain saat menyimpan.
        QuestionImportService.Preview preview =
                importService.preview("lintas.csv", bytes, topicA.getPaketId(), clientA.getId());
        assertThrows(ResourceNotFoundException.class,
                () -> importService.commit(preview.token(), paketB.getId(), topicA.getId(),
                        clientA.getId(), authorA.getId()));
        assertThat(questionRepository.findByPaketIdOrderByPositionAsc(paketB.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-B02: Topic tujuan yang bukan milik Paket tujuan ditolak, tidak ada baris yang tersimpan")
    void ac_b02_topicBukanMilikPaketTujuanDitolak() {
        ClientEntity client = testData.client("SD Topic Nyasar");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Nyasar");
        TopicEntity topicPaketSatu = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        TopicEntity topicPaketDua = testData.topic(client, "Matematika Kelas 4", "Geometri");

        String csv = HEADER + "\n"
                + "Aljabar,PG,Soal nyasar,Pilihan A,Pilihan B,Pilihan C,Pilihan D,A,Pembahasan\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview = importService.preview(
                "nyasar.csv", bytes, topicPaketSatu.getPaketId(), client.getId());
        assertThrows(IllegalArgumentException.class,
                () -> importService.commit(preview.token(), topicPaketSatu.getPaketId(),
                        topicPaketDua.getId(), client.getId(), author.getId()));

        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topicPaketSatu.getId())).isEmpty();
        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topicPaketDua.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-Q03 (TC-22): baris yang isinya kosong setelah sanitasi gagal bernomor di pratinjau, tanpa membatalkan baris lain")
    void ac_q03_barisKosongSetelahSanitasiGagalTanpaMeracuniBarisLain() {
        ClientEntity client = testData.client("SD Sanitasi Kosong");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Kosong");
        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");

        // Baris kedua lolos parser (mentahnya tidak kosong) tapi menjadi kosong setelah
        // sanitasi. Tanpa penjaga, ia lolos sampai flush, melanggar question_body_not_blank
        // di database, dan MERACUNI transaksi: seluruh impor batal gara-gara satu baris —
        // persis yang FR-022 larang.
        String csv = HEADER + "\n"
                + "Aljabar,ESSAY,Soal sehat satu,,,,,,\n"
                + "Aljabar,ESSAY,<script>alert(1)</script>,,,,,,\n"
                + "Aljabar,ESSAY,Soal sehat dua,,,,,,\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview =
                importService.preview("kosong.csv", bytes, topic.getPaketId(), client.getId());
        assertThat(preview.validCount()).isEqualTo(2);
        assertThat(preview.failures()).hasSize(1);
        // Baris data kedua = baris berkas ke-3 (header di baris 1) — penomoran manusiawi yang
        // sama dengan kegagalan parser, supaya pengguna bisa memperbaiki berkas aslinya.
        assertThat(preview.failures().getFirst().lineNumber()).isEqualTo(3);
        assertThat(preview.failures().getFirst().reason().toLowerCase()).contains("kosong");

        QuestionImportService.ImportSummary summary = importService.commit(
                preview.token(), topic.getPaketId(), topic.getId(), client.getId(), author.getId());
        assertThat(summary.saved()).isEqualTo(2);
        assertThat(questionRepository.findByTopicIdOrderByPositionAsc(topic.getId()))
                .extracting(QuestionEntity::getBodyText)
                .containsExactly("Soal sehat satu", "Soal sehat dua");
    }

    @Test
    @DisplayName("TC-22: konten impor melewati sanitasi yang sama dengan editor — tag script dibuang sebelum tersimpan")
    void tc_22_kontenImporTersanitasi() {
        ClientEntity client = testData.client("SD Sanitasi Impor");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Sanitasi");
        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");

        String csv = HEADER + "\n"
                + "Aljabar,ESSAY,<script>alert(1)</script>Berapa hasil 2 tambah 3?,,,,,,\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        QuestionImportService.Preview preview =
                importService.preview("sanitasi.csv", bytes, topic.getPaketId(), client.getId());
        importService.commit(preview.token(), topic.getPaketId(), topic.getId(),
                client.getId(), author.getId());

        List<QuestionEntity> tersimpan = questionRepository.findByTopicIdOrderByPositionAsc(topic.getId());
        assertThat(tersimpan).hasSize(1);
        assertThat(tersimpan.getFirst().getBodyHtml()).doesNotContain("<script>");
        assertThat(tersimpan.getFirst().getBodyHtml()).contains("Berapa hasil 2 tambah 3?");
    }
}
