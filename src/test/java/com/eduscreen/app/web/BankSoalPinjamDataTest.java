package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.controller.PinjamPanelData;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * Kontrak JSON panel pinjam Bank Soal Client (ADR-0019, TC-14a): bentuk balasan, penyaringan
 * tenant, dan bentuk galat {@code GET /bank-soal/paket/{id}/pinjam}.
 *
 * <p>Panel ini pindah dari fragmen HTML ke JSON supaya keadaan pilihan Alpine tidak perlu
 * diselamatkan dari swap fragmen (ADR-0019) — {@code MockMvc} tidak lagi menangkap galat render
 * pada bagian yang sekarang dirakit Alpine di klien (penyaring, tabel, tab Terpilih, sorot).
 * Kelas ini adalah SATU dari dua penjaga wajib TC-14a untuk permukaan ini; penjaga kedua (tes
 * peramban sungguhan) belum ada di proyek ini — lihat catatan eksplisit di
 * {@link BankSoalRenderTest#panelPinjamKerangkaSsrDirender}.
 */
@AutoConfigureMockMvc
class BankSoalPinjamDataTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired PaketService paketService;
    @Autowired ObjectMapper objectMapper;

    private PinjamPanelData ambil(UUID targetId, RequestPostProcessor principal,
                                  String... paramPairs) throws Exception {
        // Accept: application/json — sama seperti fetch() sungguhan di pinjamPanel() (bank/isi.html).
        MockHttpServletRequestBuilder req = get("/bank-soal/paket/{id}/pinjam", targetId)
                .accept(MediaType.APPLICATION_JSON).with(principal);
        for (int i = 0; i + 1 < paramPairs.length; i += 2) {
            req = req.param(paramPairs[i], paramPairs[i + 1]);
        }
        String body = mvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PinjamPanelData.class);
    }

    @Test
    @DisplayName("AC-B19: JSON panel pinjam menawarkan sumber lintas Subject sejak dibuka, termasuk untuk Paket yang Subject-nya masih sendirian")
    void menawarkanSumberLintasSubjectSejakDibuka() throws Exception {
        ClientEntity client = data.client("SD Pinjam JSON Lintas Subject");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin JSON Lintas")));
        // Sejarah: Paket satu-satunya di Subject-nya, persis kasus yang ditemukan pemilik produk.
        PaketEntity sejarah = data.paket(client, "Sejarah Kelas 9 JSON", "Paket Sejarah JSON Sendirian");
        PaketEntity matematika = data.paket(client, "Matematika Kelas 4 JSON", "Paket Matematika JSON Sumber");
        TopicEntity topikMatematika = paketService.topicsOf(matematika.getId()).get(0);
        data.mcq(client, topikMatematika, "Soal matematika json lintas subject", 4);

        PinjamPanelData hasil = ambil(sejarah.getId(), admin);

        assertThat(hasil.pakets()).extracting(PinjamPanelData.Opsi::label)
                .contains("Paket Matematika JSON Sumber");
        assertThat(hasil.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .contains("Soal matematika json lintas subject");
    }

    @Test
    @DisplayName("AC-B20: JSON panel pinjam tidak menawarkan soal milik Paket tujuan sendiri sebagai sumber")
    void tidakMenawarkanSoalMilikPaketTujuanSendiri() throws Exception {
        ClientEntity client = data.client("SD Pinjam JSON Diri Sendiri");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin JSON Diri")));
        PaketEntity target = data.paket(client, "Kimia JSON", "Paket Target JSON Diri Sendiri");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);
        data.mcq(client, topikTarget, "Soal milik Paket tujuan json sendiri", 4);

        PinjamPanelData hasil = ambil(target.getId(), admin);

        assertThat(hasil.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .doesNotContain("Soal milik Paket tujuan json sendiri");
        assertThat(hasil.pakets()).extracting(PinjamPanelData.Opsi::label)
                .doesNotContain("Paket Target JSON Diri Sendiri");
    }

    @Test
    @DisplayName("AC-B04: JSON panel pinjam berhenti menawarkan soal begitu salinannya ada di Paket tujuan")
    void tidakMenawarkanSoalYangSudahDipinjam() throws Exception {
        ClientEntity client = data.client("SD Pinjam JSON B04");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin JSON B04")));
        PaketEntity sumber = data.paket(client, "Fisika JSON B04", "Paket Sumber JSON B04");
        TopicEntity topikSumber = paketService.topicsOf(sumber.getId()).get(0);
        QuestionEntity dipinjam = data.mcq(client, topikSumber, "Soal json sudah dipinjam", 4);
        data.mcq(client, topikSumber, "Soal json belum dipinjam", 4);
        PaketEntity target = data.paket(client, "Fisika JSON B04", "Paket Target JSON B04");
        TopicEntity topikTarget = paketService.topicsOf(target.getId()).get(0);

        PinjamPanelData sebelum = ambil(target.getId(), admin);
        assertThat(sebelum.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .contains("Soal json sudah dipinjam", "Soal json belum dipinjam");

        mvc.perform(post("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("topicTitle", topikTarget.getTitle())
                        .param("questionIds", dipinjam.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        PinjamPanelData sesudah = ambil(target.getId(), admin);
        assertThat(sesudah.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .doesNotContain("Soal json sudah dipinjam")
                .contains("Soal json belum dipinjam");
    }

    @Test
    @DisplayName("TC-36 (TC-09): Paket milik Client lain di GET .../pinjam dijawab 404, bukan galat yang membedakannya")
    void paketMilikClientLainDijawab404() throws Exception {
        ClientEntity milikku = data.client("SD Pinjam JSON A");
        var admin = user(data.principal(data.user(milikku, UserRole.CLIENT_ADMIN, "Admin JSON A")));
        ClientEntity lain = data.client("SD Pinjam JSON B");
        PaketEntity paketLain = data.paket(lain, "Kimia JSON Lain", "Paket JSON Milik Client Lain");

        // Bentuk galatnya sendiri (ADR-0019 pagar 3): status 404 yang benar, plus pesan yang bisa
        // ditampilkan apa adanya di klien — bukan payload kosong atau stack trace. Accept:
        // application/json disertakan sengaja: klien sungguhan (fetch() di pinjamPanel()) selalu
        // mengirimnya, dan ini satu-satunya tempat kepatuhan TC-31 diklaim terbukti — tanpa
        // header ini, request tidak merepresentasikan apa yang benar-benar dikirim klien.
        mvc.perform(get("/bank-soal/paket/{id}/pinjam", paketLain.getId())
                        .accept(MediaType.APPLICATION_JSON).with(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    /**
     * C1 (temuan review): {@code filterPaketId} datang langsung dari parameter permintaan dan
     * dulu tidak pernah melewati {@code pakets.require(...)} sebelum {@code topicsOf(...)} —
     * {@code TopicRepository.findByPaketIdOrderByPositionAsc} cuma join ke Paket untuk
     * menghormati {@code deleted_at}, tidak menyaring {@code clientId} sama sekali. Akibatnya
     * Client A yang mengirim {@code filterPaketId} milik Client B mendapat judul Topic Client B
     * begitu saja (200 berisi), sekaligus jadi oracle keberadaan (TC-09): id asing membalas
     * tidak kosong, id yang tidak ada membalas kosong, dua-duanya 200 — persis yang TC-09
     * larang bisa dibedakan.
     */
    @Test
    @DisplayName("TC-36 (TC-09): filterPaketId milik Client lain di GET .../pinjam dijawab 404, judul Topic-nya tidak pernah bocor")
    void filterPaketIdMilikClientLainDijawab404() throws Exception {
        ClientEntity milikku = data.client("SD Pinjam JSON Filter A");
        var admin = user(data.principal(data.user(milikku, UserRole.CLIENT_ADMIN, "Admin JSON Filter A")));
        PaketEntity target = data.paket(milikku, "Sejarah JSON Filter", "Paket Target JSON Filter");
        ClientEntity lain = data.client("SD Pinjam JSON Filter B");
        PaketEntity paketLain = data.paket(lain, "Kimia JSON Filter Lain", "Paket JSON Filter Milik Client Lain");
        // Topic bernama unik (bukan "Topik 1" bawaan): supaya assersi "tidak bocor" di bawah
        // sungguh membuktikan sesuatu, bukan kebetulan cocok dengan judul generik.
        TopicEntity topikLain = paketService.addTopic(paketLain.getId(), "Topik Rahasia Client Lain", lain.getId());

        mvc.perform(get("/bank-soal/paket/{id}/pinjam", target.getId())
                        .param("filterPaketId", paketLain.getId().toString())
                        .accept(MediaType.APPLICATION_JSON).with(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(topikLain.getTitle()))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("TC-36: soal milik Client lain tidak pernah muncul di JSON panel pinjam Client ini")
    void soalMilikClientLainTidakMunculSebagaiSumber() throws Exception {
        ClientEntity milikku = data.client("SD Pinjam JSON Isolasi A");
        var admin = user(data.principal(data.user(milikku, UserRole.CLIENT_ADMIN, "Admin JSON Isolasi")));
        PaketEntity target = data.paket(milikku, "Biologi JSON Isolasi", "Paket Target JSON Isolasi");
        ClientEntity lain = data.client("SD Pinjam JSON Isolasi B");
        PaketEntity paketLain = data.paket(lain, "Biologi JSON Isolasi", "Paket JSON Milik Client Lain Isolasi");
        TopicEntity topikLain = paketService.topicsOf(paketLain.getId()).get(0);
        data.mcq(lain, topikLain, "Soal json milik client lain isolasi", 4);

        PinjamPanelData hasil = ambil(target.getId(), admin);

        assertThat(hasil.pakets()).extracting(PinjamPanelData.Opsi::label)
                .doesNotContain("Paket JSON Milik Client Lain Isolasi");
        assertThat(hasil.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .doesNotContain("Soal json milik client lain isolasi");
    }

    @Test
    @DisplayName("AC-B21: penyaring Subject/Paket JSON panel pinjam benar-benar menyempit dropdown dan tabel soal, bukan hiasan")
    void penyaringBenarBenarMenyaring() throws Exception {
        ClientEntity client = data.client("SD Pinjam JSON Saring");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin JSON Saring")));
        PaketEntity target = data.paket(client, "Sejarah JSON Saring", "Paket Target JSON Saring");
        PaketEntity paketMtk = data.paket(client, "Matematika Kelas 4 JSON Saring", "Paket Matematika JSON Saring");
        PaketEntity paketIpa = data.paket(client, "IPA Kelas 5 JSON Saring", "Paket IPA JSON Saring");
        TopicEntity topikMtk = paketService.topicsOf(paketMtk.getId()).get(0);
        TopicEntity topikIpa = paketService.topicsOf(paketIpa.getId()).get(0);
        data.mcq(client, topikMtk, "Soal json saring matematika", 4);
        data.mcq(client, topikIpa, "Soal json saring ipa", 4);

        // Tanpa saringan (aturan 1): kedua Paket dan kedua soal sama-sama tampil.
        PinjamPanelData tanpaSaring = ambil(target.getId(), admin);
        assertThat(tanpaSaring.pakets()).extracting(PinjamPanelData.Opsi::label)
                .contains("Paket Matematika JSON Saring", "Paket IPA JSON Saring");
        assertThat(tanpaSaring.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .contains("Soal json saring matematika", "Soal json saring ipa");
        // AC-B21: dropdown Subject cuma menawarkan Subject yang benar-benar punya Paket sumber —
        // Subject Paket TARGET sendiri ("Sejarah JSON Saring") tidak punya Paket lain sama sekali
        // di Subject itu, jadi tidak boleh muncul di dropdown (menawarkannya berarti menyodorkan
        // pilihan yang pasti berujung "Semua Paket" kosong).
        assertThat(tanpaSaring.subjects()).extracting(PinjamPanelData.Opsi::id)
                .containsExactlyInAnyOrder(paketMtk.getSubjectId(), paketIpa.getSubjectId());

        // Saring Subject Matematika: daftar Paket menyempit (aturan 7), begitu juga soal.
        PinjamPanelData tersaringSubject = ambil(target.getId(), admin,
                "filterSubjectId", paketMtk.getSubjectId().toString());
        assertThat(tersaringSubject.pakets()).extracting(PinjamPanelData.Opsi::label)
                .containsExactly("Paket Matematika JSON Saring");
        assertThat(tersaringSubject.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .containsExactly("Soal json saring matematika");

        // Saring Paket langsung: Topic dropdown ikut terisi Topic Paket itu, soal menyempit sama.
        PinjamPanelData tersaringPaket = ambil(target.getId(), admin,
                "filterPaketId", paketIpa.getId().toString());
        // containsExactly, bukan contains: kalau penyempitan Topic-nya dicabut sepenuhnya
        // (kembali ke "seluruh Topic Client"), assersi contains tetap hijau — cuma
        // containsExactly yang benar-benar gagal saat itu terjadi.
        assertThat(tersaringPaket.topics()).extracting(PinjamPanelData.Opsi::id)
                .containsExactly(topikIpa.getId());
        assertThat(tersaringPaket.soal().content()).extracting(PinjamPanelData.SoalRow::isi)
                .containsExactly("Soal json saring ipa");
    }
}
