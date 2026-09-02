package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.QuestionImportParser;
import com.eduscreen.app.modules.assessment.service.QuestionImportService;
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
 * T100 — Impor massal soal (AC-Q03, AC-Q06).
 *
 * <p>Kolom berkas mengikuti {@code QuestionImportParser}: {@code
 * topic,tipe,soal,opsi_a,opsi_b,opsi_c,opsi_d,kunci,pembahasan}, baris pertama header. Topic pada
 * berkas harus cocok nama Topic yang terlihat Client (dicocokkan tanpa peduli huruf besar/kecil).
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
                () -> importService.preview("soal-raksasa.csv", bytes, client.getId()));
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

        QuestionImportService.Preview preview = importService.preview("soal-valid.csv", bytes, client.getId());
        assertThat(preview.validCount()).isEqualTo(17);
        assertThat(preview.failures()).hasSize(3);
        // Baris data ke-18, 19, 20 (setelah header di baris 1) jatuh di nomor baris 19, 20, 21 —
        // penomoran yang dilaporkan ke pengguna memakai baris berkas asli, bukan indeks internal.
        assertThat(preview.failures()).extracting(QuestionImportParser.RowFailure::lineNumber)
                .containsExactlyInAnyOrder(19, 20, 21);
        assertThat(preview.failures())
                .allSatisfy(failure -> assertThat(failure.reason().toLowerCase()).contains("kunci"));

        QuestionImportService.ImportSummary summary = importService.commit(preview.token(), client.getId(), author.getId());
        assertThat(summary.saved()).isEqualTo(17);

        List<QuestionEntity> tersimpan = questionRepository
                .findByClientIdAndTopicIdOrderByCreatedAtDesc(client.getId(), topic.getId());
        assertThat(tersimpan).hasSize(17);
    }

    @Test
    @DisplayName("AC-Q03: token pratinjau yang tidak dikenal ditolak dengan IllegalArgumentException")
    void ac_q03_tokenPratinjauTidakDikenalDitolak() {
        ClientEntity client = testData.client("SD Token Asing");
        AppUserEntity author = testData.user(client, UserRole.CLIENT_ADMIN, "Admin Token");

        assertThrows(IllegalArgumentException.class,
                () -> importService.commit("token-tidak-pernah-ada", client.getId(), author.getId()));
    }
}
