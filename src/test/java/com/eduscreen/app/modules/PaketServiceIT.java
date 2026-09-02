package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaketServiceIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    PaketService pakets;
    @Autowired
    TaxonomyService taxonomy;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-B01: Paket baru lahir dengan satu Topic bernama Topik 1")
    void newPaketHasDefaultTopic() {
        ClientEntity client = data.client("SD Topik Bawaan");
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Pecahan", null, "Matematika Kelas 4 Bawaan"),
                client.getId(), null);

        List<TopicEntity> topics = pakets.topicsOf(paket.getId());

        assertThat(topics).extracting(TopicEntity::getTitle).containsExactly("Topik 1");
        assertThat(topics.get(0).getPosition()).isZero();
    }

    @Test
    @DisplayName("AC-B06: nama Subject yang sudah ada dipakai ulang, bukan diduplikasi")
    void existingSubjectIsReused() {
        ClientEntity client = data.client("SD Subject Pakai Ulang");
        SubjectEntity subject = taxonomy.createClientSubject(client.getId(), "IPA Kelas 5 Pakai Ulang");

        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan A", null, "IPA Kelas 5 Pakai Ulang"),
                client.getId(), null);

        assertThat(paket.getSubjectId()).isEqualTo(subject.getId());
    }

    @Test
    @DisplayName("AC-B06: nama yang cocok Subject GLOBAL menempel ke GLOBAL, tanpa peduli kapital dan spasi tepi")
    void globalSubjectWinsOverLocalTwin() {
        ClientEntity client = data.client("SD Kembar Global");
        // Kembaran lokal sengaja lahir lebih dulu: kalau layanan cuma mengambil yang pertama
        // ketemu, kembaran inilah yang menang dan salinan lokal terus dipakai selamanya.
        taxonomy.createClientSubject(client.getId(), "Fisika Kelas 7 Kembar");
        SubjectEntity global = taxonomy.createGlobalSubject("Fisika Kelas 7 Kembar");

        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Gaya", null, "  fisika KELAS 7 kembar "),
                client.getId(), null);

        assertThat(paket.getSubjectId()).isEqualTo(global.getId());
    }

    @Test
    @DisplayName("TC-36: Paket milik Client lain menghasilkan 404, bukan 403")
    void requireHidesOtherClientsPaket() {
        ClientEntity pemilik = data.client("SD Pemilik Paket");
        ClientEntity penyusup = data.client("SD Penyusup Paket");
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Rahasia", null, "IPS Kelas 6 Rahasia"),
                pemilik.getId(), null);

        assertThatThrownBy(() -> pakets.require(paket.getId(), penyusup.getId()))
                .isInstanceOf(com.eduscreen.app.shared.web.ResourceNotFoundException.class);
        assertThat(pakets.require(paket.getId(), pemilik.getId()).getId()).isEqualTo(paket.getId());
    }

    @Test
    @DisplayName("TC-36: ruang kerja master menolak id Paket milik Client, dan sebaliknya")
    void requireSeparatesMasterFromClient() {
        ClientEntity client = data.client("SD Batas Master");
        PaketEntity milikClient = pakets.create(
                new PaketService.PaketDraft("Latihan Client", null, "PKN Kelas 5 Batas"),
                client.getId(), null);
        PaketEntity master = pakets.create(
                new PaketService.PaketDraft("Latihan Master", null, "PKN Kelas 5 Master Batas"),
                null, null);

        assertThatThrownBy(() -> pakets.require(milikClient.getId(), null))
                .isInstanceOf(com.eduscreen.app.shared.web.ResourceNotFoundException.class);
        assertThatThrownBy(() -> pakets.require(master.getId(), client.getId()))
                .isInstanceOf(com.eduscreen.app.shared.web.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC-B01: Topic tambahan menyambung posisi setelah Topik 1, bukan mengulang dari nol")
    void addTopicContinuesPositions() {
        ClientEntity client = data.client("SD Posisi Topic");
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Posisi", null, "Seni Kelas 3 Posisi"),
                client.getId(), null);

        TopicEntity kedua = pakets.addTopic(paket.getId(), "Bab Warna", client.getId());

        assertThat(kedua.getPosition()).isEqualTo(1);
        assertThat(pakets.topicsOf(paket.getId()))
                .extracting(TopicEntity::getTitle).containsExactly("Topik 1", "Bab Warna");
    }

    @Test
    @DisplayName("TC-35: softDelete mengisi deleted_at, Paket lenyap dari require dan topicsOf, barisnya tetap ada")
    void softDeleteHidesWithoutErasing() {
        ClientEntity client = data.client("SD Hapus Halus");
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Terhapus", null, "Olahraga Kelas 2 Hapus"),
                client.getId(), null);

        pakets.softDelete(paket.getId(), client.getId());

        assertThatThrownBy(() -> pakets.require(paket.getId(), client.getId()))
                .isInstanceOf(com.eduscreen.app.shared.web.ResourceNotFoundException.class);
        assertThat(pakets.topicsOf(paket.getId())).isEmpty();
        // Bukti tidak ada DELETE fisik: barisnya masih di tabel, hanya bertanda waktu hapus.
        assertThat(jdbc.queryForObject(
                "select count(*) from paket where id = ?", Long.class, paket.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select deleted_at from paket where id = ?", OffsetDateTime.class, paket.getId()))
                .isNotNull();
    }
}
