package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Keadaan terbit konten master Eduscreen (FR-066 sampai FR-070, FR-072).
 *
 * <p>Dua keadaan saja, disimpan sebagai satu kolom waktu: {@code publishedAt} terisi berarti
 * terlihat di katalog seluruh Client, kosong berarti masih digarap dan hanya terlihat Eduscreen
 * Admin. Bukan enum status — kolom waktu menyimpan "apakah" dan "sejak kapan" sekaligus, dan
 * mengikuti pola {@code lockedAt}/{@code deletedAt}/{@code closedAt} yang sudah dipakai di
 * seluruh skema ini.
 *
 * <p><b>Penarikan aman by construction.</b> Menarik konten master dari peredaran tidak menyentuh
 * satu pun salinan yang sudah diadopsi Client, karena adopsi menghasilkan baris tersendiri tanpa
 * tautan hidup ke master (ADR-0001, FR-068). Tidak ada kode di sini yang perlu menjaga hal itu —
 * yang menjaganya adalah bentuk datanya.
 *
 * <p>Seluruh method menyaring {@code clientId} null secara eksplisit lewat
 * {@code findByIdAndClientId}: konten milik sebuah Client tidak punya keadaan terbit, dan
 * database menolaknya lewat check constraint {@code *_publish_master_only}.
 */
@Service
public class MasterPublishingService {

    private final QuestionRepository questions;
    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final PaketItemRepository items;
    private final PaketVersionService versionService;
    private final ClientClock clock;

    public MasterPublishingService(QuestionRepository questions,
                                   PaketRepository pakets,
                                   PaketVersionRepository versions,
                                   PaketItemRepository items,
                                   PaketVersionService versionService,
                                   ClientClock clock) {
        this.questions = questions;
        this.pakets = pakets;
        this.versions = versions;
        this.items = items;
        this.versionService = versionService;
        this.clock = clock;
    }

    @Transactional
    public QuestionEntity publishQuestion(UUID id) {
        QuestionEntity question = requireMasterQuestion(id);
        // Waktu terbit selalu jam server, tidak pernah nilai yang dikirim klien (TC-12).
        question.publish(clock.now());
        return questions.save(question);
    }

    /**
     * Menarik satu Question master dari peredaran (AC-B17).
     *
     * <p>Ditolak selama soal itu ada di sebuah versi terbit: sekolah membaca versi terbit lewat
     * saringan {@code publishedAt}, jadi menariknya berarti soal lenyap dari versi yang
     * seharusnya beku (ADR-0021). Ditolak, bukan menarik versinya otomatis: satu gerbang, satu
     * arah. Yang bisa ditarik hanya soal yang baru ada di versi kerja.
     */
    @Transactional
    public QuestionEntity unpublishQuestion(UUID id) {
        QuestionEntity question = requireMasterQuestion(id);
        requireTidakDiVersiTerbit(question, "menarik soal");
        question.unpublish();
        return questions.save(question);
    }

    /**
     * Menerbitkan Paket master, satuan katalog dan akses sejak ADR-0018 (FR-067, AC-B12).
     *
     * <p>Satu gerbang saja: versi yang terbit wajib punya minimal satu Question terbit (AC-B16).
     * Question draf yang tersisa di dalamnya tidak menghalangi — ia hanya tidak ikut terlihat
     * sekolah sampai diterbitkan (ADR-0020). Sebelum ADR-0020 gerbangnya menolak Paket yang
     * masih memuat draf; itu menyandera Paket berisi 200 soal pada satu soal yang belum sempat
     * ditinjau, tanpa memberi jalan keluar selain menerbitkan sisanya satu per satu.
     *
     * <p>Kalau Paket punya versi kerja, versi itulah yang dibekukan menjadi versi terbit
     * berikutnya (ADR-0021). Kalau tidak — Paket yang pernah ditarik lalu diterbitkan lagi —
     * cukup keadaan Paketnya yang naik; versi terbit terakhir tetap yang dibaca.
     *
     * <p>{@code sertakanDraf} adalah pilihan yang diambil Eduscreen Admin di layar, bukan
     * kebijakan tersembunyi: true menerbitkan seluruh Question draf di versi ini lebih dulu,
     * false membekukan versinya saja dan meninggalkan draf tetap draf.
     */
    @Transactional
    public PaketEntity publishPaket(UUID id, boolean sertakanDraf) {
        PaketEntity paket = requireMasterPaket(id);

        if (sertakanDraf) {
            terbitkanSemuaDraf(id);
        }

        PaketVersionEntity version = versionOf(id);
        if (questions.countPublishedInVersion(version.getId()) == 0) {
            throw new IllegalArgumentException(items.countByVersion(version.getId()) > 0
                    ? "Paket ini belum punya satu pun soal terbit. Terbitkan minimal satu soalnya dulu."
                    : "Paket master wajib memuat minimal 1 soal untuk bisa diterbitkan");
        }
        if (version.isDraft()) {
            versionService.freeze(version);
        }

        paket.publish(clock.now());
        return pakets.save(paket);
    }

    /** Menerbitkan Paket tanpa menyentuh Question drafnya. */
    @Transactional
    public PaketEntity publishPaket(UUID id) {
        return publishPaket(id, false);
    }

    /**
     * Menerbitkan seluruh Question draf di satu Paket master sekaligus (AC-B19).
     *
     * <p>Ada karena Paket berisi ratusan soal tidak bisa diterbitkan lewat tombol per baris tanpa
     * membuang waktu yang tidak masuk akal. Bukan gerbang, cuma jalan pintas: hasilnya persis sama
     * dengan menekan Terbitkan pada tiap soal satu per satu.
     */
    @Transactional
    public int publishDraftQuestions(UUID paketId) {
        requireMasterPaket(paketId);
        return terbitkanSemuaDraf(paketId);
    }

    /** Question master yang masih draf di satu Paket, untuk ditawarkan pilihannya di layar. */
    public List<QuestionEntity> draftQuestionsOf(UUID paketId) {
        return questions.findUnpublishedInVersion(versionOf(paketId).getId());
    }

    /** Jumlah soal terbit di versi kerja satu Paket master — angka di dialog terbit (AC-B12). */
    public long publishedCountOf(UUID paketId) {
        return questions.countPublishedInVersion(versionOf(paketId).getId());
    }

    /**
     * Versi yang dibaca: versi kerja bila ada, kalau tidak versi terbit terakhir — soal draf di
     * dalam versi beku tetap boleh diterbitkan satu per satu (ADR-0020), item-nya saja yang beku.
     * Pemanggil sudah lolos {@code requireMasterPaket} atau padanannya.
     */
    private PaketVersionEntity versionOf(UUID paketId) {
        return versions.findDraft(paketId)
                .or(() -> versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(paketId))
                .orElseThrow(() -> new IllegalStateException("Paket " + paketId + " tidak punya versi"));
    }

    private int terbitkanSemuaDraf(UUID paketId) {
        List<QuestionEntity> draf = questions.findUnpublishedInVersion(versionOf(paketId).getId());
        // Waktu terbit selalu jam server, tidak pernah nilai yang dikirim klien (TC-12).
        OffsetDateTime sekarang = clock.now();
        draf.forEach(q -> {
            q.publish(sekarang);
            questions.save(q);
        });
        return draf.size();
    }

    /** Menarik Paket master dari peredaran: tidak ada akses baru; yang sudah membaca tidak tersentuh (FR-068). */
    @Transactional
    public PaketEntity withdrawPaket(UUID id) {
        PaketEntity paket = requireMasterPaket(id);
        paket.withdraw();
        return pakets.save(paket);
    }

    /**
     * Gerbang AC-B17: soal yang ada di versi terbit mana pun tidak boleh diturunkan dari
     * keadaan terbit — versi terbit beku (ADR-0021).
     *
     * <p>Konten milik sebuah Client dilewati begitu saja: Paket Client tidak pernah terbit
     * (check constraint {@code paket_publish_master_only}), sehingga tidak ada yang perlu dijaga.
     */
    public void requireTidakDiVersiTerbit(QuestionEntity question, String tindakan) {
        if (question.getClientId() != null) {
            return;
        }
        if (items.countPublishedPlacements(question.getId()) > 0) {
            throw new IllegalArgumentException("Soal ini ada di versi Paket yang sudah terbit dan beku. "
                    + "Buat versi baru Paket itu, lalu ganti soalnya di sana, alih-alih " + tindakan + ".");
        }
    }

    /** Konten milik sebuah Client dan konten yang tidak ada sama-sama 404 (TC-09). */
    private QuestionEntity requireMasterQuestion(UUID id) {
        return questions.findByIdAndClientIdIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soal master tidak ditemukan"));
    }

    private PaketEntity requireMasterPaket(UUID id) {
        return pakets.findByIdAndClientIdIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));
    }

}
