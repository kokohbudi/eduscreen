package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketAccessEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.PaketVersionService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Akses sekolah ke Paket master: satu baris penanda, nol baris soal (ADR-0021). */
class PaketAccessIT extends PostgresTestBase {

    @Autowired TestData data;
    @Autowired PaketService pakets;
    @Autowired PaketAccessService access;
    @Autowired PaketVersionService versionService;
    @Autowired MasterPublishingService publishing;
    @Autowired QuestionService questionService;
    @Autowired QuestionRepository questions;

    private record Master(PaketEntity paket, TopicEntity topic, QuestionEntity soal) {
    }

    private Master masterTerbit(String nama) {
        PaketEntity paket = data.masterPaket("Matematika Kelas 4 " + nama, "Paket " + nama);
        TopicEntity topic = pakets.topicsOf(paket.getId()).get(0);
        QuestionEntity soal = data.publishedMasterMcq(topic, "Soal " + nama);
        publishing.publishPaket(paket.getId());
        return new Master(paket, topic, soal);
    }

    @Test
    @DisplayName("AC-B05 (ADR-0021): memberi akses = satu baris paket_access ke versi terbit terakhir, nol baris question/paket/topic baru")
    void grantTanpaSalinan() {
        Master m = masterTerbit("Akses");
        ClientEntity sekolah = data.client("SD Akses");
        long soalSebelum = questions.count();

        PaketAccessEntity akses = access.grant(sekolah.getId(), m.paket().getId(), null, null);

        assertThat(akses.getClientId()).isEqualTo(sekolah.getId());
        assertThat(akses.getPaketId()).isEqualTo(m.paket().getId());
        assertThat(akses.getVersionId()).isEqualTo(pakets.versionOf(m.paket().getId()).getId());
        assertThat(questions.count()).isEqualTo(soalSebelum);
        assertThat(access.masterPaketsFor(sekolah.getId())).extracting(PaketEntity::getId)
                .containsExactly(m.paket().getId());
        // Soal master kini terlihat di pencarian bank soal sekolah, sebagai baris yang sama.
        assertThat(questionService.searchForBuilder(sekolah.getId(), null, null, null, null, List.of(),
                "Soal Akses", PageRequest.of(0, 10)).getContent())
                .extracting(QuestionEntity::getId).containsExactly(m.soal().getId());
        assertThat(questionService.requireReadable(m.soal().getId(), sekolah.getId()).getId())
                .isEqualTo(m.soal().getId());

        // Memberi ulang tidak menggandakan, hanya memperbarui batas.
        OffsetDateTime batas = OffsetDateTime.now().plusMonths(6);
        PaketAccessEntity ulang = access.grant(sekolah.getId(), m.paket().getId(), batas, null);
        assertThat(ulang.getId()).isEqualTo(akses.getId());
        assertThat(ulang.getValidUntil()).isEqualTo(batas);
        assertThat(access.activeFor(sekolah.getId())).hasSize(1);
    }

    @Test
    @DisplayName("TC-09 (FR-067): Paket master draf, ditarik, atau milik Client sama-sama tidak bisa diberikan — 404 identik")
    void grantHanyaPaketTerbit() {
        ClientEntity sekolah = data.client("SD Akses Draf");
        PaketEntity draf = data.masterPaket("Matematika Kelas 4 Akses Draf", "Paket draf");
        Master ditarik = masterTerbit("Ditarik");
        publishing.withdrawPaket(ditarik.paket().getId());
        PaketEntity milikSekolah = data.paket(sekolah, "Matematika Kelas 4 Akses Draf", "Paket sekolah");

        for (PaketEntity p : List.of(draf, ditarik.paket(), milikSekolah)) {
            assertThatThrownBy(() -> access.grant(sekolah.getId(), p.getId(), null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("TC-41 (TC-36): akses sekolah A tidak terlihat sekolah B — soal master tidak bisa dibaca, dipasang, atau disalin B")
    void aksesTidakBocorLintasSekolah() {
        Master m = masterTerbit("Bocor");
        ClientEntity a = data.client("SD Akses A");
        ClientEntity b = data.client("SD Akses B");
        access.grant(a.getId(), m.paket().getId(), null, null);

        assertThat(access.masterPaketsFor(b.getId())).isEmpty();
        assertThat(questionService.searchForBuilder(b.getId(), null, null, null, null, List.of(),
                "Soal Bocor", PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThatThrownBy(() -> questionService.requireReadable(m.soal().getId(), b.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> access.visibleVersionOf(m.paket().getId(), b.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> access.requireReadable(m.paket().getId(), b.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("TC-36 (FR-068, ADR-0021): akses kedaluwarsa atau dicabut menghentikan pemakaian baru; soal master tetap tidak bisa ditulisi sekolah")
    void kedaluwarsaDanCabut() {
        Master m = masterTerbit("Habis");
        ClientEntity sekolah = data.client("SD Akses Habis");
        PaketAccessEntity akses = access.grant(sekolah.getId(), m.paket().getId(),
                OffsetDateTime.now().minusDays(1), null);

        assertThat(access.usable(sekolah.getId())).isEmpty();
        assertThat(access.activeFor(sekolah.getId())).hasSize(1);
        assertThatThrownBy(() -> questionService.requireReadable(m.soal().getId(), sekolah.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        access.grant(sekolah.getId(), m.paket().getId(), null, null);
        assertThat(access.usable(sekolah.getId())).hasSize(1);
        access.revoke(akses.getId());
        assertThat(access.usable(sekolah.getId())).isEmpty();
        assertThat(access.activeFor(sekolah.getId())).isEmpty();

        // Soal master tidak pernah bisa ditulisi sekolah, akses atau tidak (TC-36).
        assertThatThrownBy(() -> questionService.require(m.soal().getId(), sekolah.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC-B12 (ADR-0021): sekolah tetap di versinya sampai pindah sendiri; pindah versi hanya ke versi terbit Paket yang sama")
    void pindahVersi() {
        Master m = masterTerbit("Upgrade");
        ClientEntity sekolah = data.client("SD Akses Upgrade");
        PaketAccessEntity akses = access.grant(sekolah.getId(), m.paket().getId(), null, null);
        PaketVersionEntity v1 = pakets.versionOf(m.paket().getId());

        PaketVersionEntity v2 = versionService.newVersion(m.paket().getId(), null);
        QuestionEntity revisi = questionService.revise(m.soal().getId(), new QuestionService.QuestionDraft(
                m.topic().getId(), m.soal().getType(), "<p>Soal Upgrade v2</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false))), m.paket().getId());

        // Versi 2 masih kerja: sekolah tidak bisa pindah ke sana, dan tetap membaca v1.
        assertThatThrownBy(() -> access.switchVersion(akses.getId(), v2.getId(), sekolah.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(access.visibleVersionOf(m.paket().getId(), sekolah.getId()).getId()).isEqualTo(v1.getId());
        assertThat(questionService.requireReadable(m.soal().getId(), sekolah.getId())).isNotNull();

        publishing.publishPaket(m.paket().getId(), true);
        assertThat(access.visibleVersionOf(m.paket().getId(), sekolah.getId()).getId())
                .as("terbit versi 2 tidak memindahkan sekolah diam-diam").isEqualTo(v1.getId());

        access.switchVersion(akses.getId(), v2.getId(), sekolah.getId());
        assertThat(access.visibleVersionOf(m.paket().getId(), sekolah.getId()).getId()).isEqualTo(v2.getId());
        assertThat(questionService.requireReadable(revisi.getId(), sekolah.getId())).isNotNull();
        assertThatThrownBy(() -> questionService.requireReadable(m.soal().getId(), sekolah.getId()))
                .as("baris lama sudah digantikan revisi; bukan lagi yang ditawarkan")
                .isInstanceOf(ResourceNotFoundException.class);

        // Sekolah lain tidak bisa memindahkan akses milik sekolah ini.
        ClientEntity lain = data.client("SD Akses Upgrade Lain");
        assertThatThrownBy(() -> access.switchVersion(akses.getId(), v1.getId(), lain.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
