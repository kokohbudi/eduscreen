package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ClientClock clock;

    public MasterPublishingService(QuestionRepository questions,
                                   PaketRepository pakets,
                                   ClientClock clock) {
        this.questions = questions;
        this.pakets = pakets;
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
     * <p>Ditolak selama Paket induknya masih terbit. Gerbang {@link #publishPaket} hanya menutup
     * satu arah: ia memastikan Paket tidak bisa NAIK terbit dengan isi yang belum terbit, tapi
     * tidak menghalangi isinya TURUN setelah Paketnya terbit. Tanpa gerbang di sini, urutannya
     * adalah: terbitkan Paket, tarik satu soalnya, Paket tetap terbit di katalog, dan adopsi
     * menyalin soal draf itu ke sekolah — persis keadaan yang FR-067 larang, dicapai lewat pintu
     * belakang. {@code PaketRepository.findMasterBlocked} pun tidak memunculkannya di dasbor,
     * karena antrean itu hanya melihat Paket yang masih draf.
     *
     * <p>Ditolak, bukan menarik Paketnya otomatis: satu gerbang, satu arah. Penarikan otomatis
     * mengubah keadaan yang tidak diminta pengguna — Paket lenyap dari katalog seluruh Client
     * sebagai efek samping dari menyunting satu soal.
     */
    @Transactional
    public QuestionEntity unpublishQuestion(UUID id) {
        QuestionEntity question = requireMasterQuestion(id);
        requirePaketBelumTerbit(question, "menarik soal");
        question.unpublish();
        return questions.save(question);
    }

    /**
     * Menerbitkan Paket master, satuan katalog dan adopsi sejak ADR-0018 (FR-067, AC-B12).
     *
     * <p>Dua gerbang, keduanya wajib: Paket kosong ditolak (FR-072, AC-B16), dan Paket yang masih
     * memuat Question belum terbit ditolak dengan menyebut Question penyebabnya (FR-069 setara).
     * Yang kedua bukan kerewelan: Paket terbit yang isinya sebagian tersembunyi akan tampil di
     * katalog dengan jumlah soal yang berbeda dari yang benar-benar bisa diadopsi Client.
     *
     * <p>Ini satu-satunya tempat FR-067 ditegakkan untuk isi Paket —
     * {@code ContentAdoptionService.adoptPakets} sengaja menyalin seluruh Question Topic-nya apa
     * adanya tanpa menyaring status terbit, sehingga kalau gerbangnya dipasang di sana ia akan
     * menghasilkan salinan yang diam-diam tidak lengkap tanpa sekolah pernah tahu ada soal yang
     * hilang. Gerbang di penerbitan gagal keras, lebih awal, dan di depan orang (Eduscreen Admin)
     * yang bisa memperbaikinya.
     */
    @Transactional
    public PaketEntity publishPaket(UUID id) {
        PaketEntity paket = requireMasterPaket(id);

        if (!questions.existsByPaketId(id)) {
            throw new IllegalArgumentException("Paket master wajib memuat minimal 1 soal untuk bisa diterbitkan");
        }

        List<QuestionEntity> belumTerbit = questions.findUnpublishedInPaket(id);
        if (!belumTerbit.isEmpty()) {
            String penyebab = belumTerbit.stream()
                    .map(q -> "\"" + ringkas(q.getBodyText()) + "\"")
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Paket belum bisa diterbitkan karena masih memuat soal yang belum terbit: " + penyebab);
        }

        paket.publish(clock.now());
        return pakets.save(paket);
    }

    /** Menarik Paket master dari peredaran; salinan yang sudah diadopsi tidak tersentuh (FR-068). */
    @Transactional
    public PaketEntity withdrawPaket(UUID id) {
        PaketEntity paket = requireMasterPaket(id);
        paket.withdraw();
        return pakets.save(paket);
    }

    /**
     * Gerbang AC-B17: isi Paket master yang sedang terbit tidak boleh diturunkan atau dibuang.
     *
     * <p>Publik dan menerima {@code tindakan} sebagai kata kerja, karena aturannya milik dua
     * pemanggil di dua kelas: penarikan Question ada di sini, penghapusannya ada di
     * {@code QuestionService.softDelete}. Menyalinnya ke dua tempat berarti satu hari nanti
     * hanya satu yang diperbaiki.
     *
     * <p>Konten milik sebuah Client dilewati begitu saja: Paket Client tidak punya keadaan terbit
     * sama sekali — database menegakkannya lewat check constraint {@code paket_publish_master_only}
     * — sehingga tidak ada yang perlu dijaga, dan membaca Paket-nya di sini justru pembacaan
     * lintas-tenant tanpa guna (TC-36).
     */
    public void requirePaketBelumTerbit(QuestionEntity question, String tindakan) {
        if (question.getClientId() != null) {
            return;
        }
        pakets.findByIdAndClientIdIsNull(question.getPaketId())
                .filter(PaketEntity::isPublished)
                .ifPresent(paket -> {
                    throw new IllegalArgumentException("Paket \"" + paket.getTitle()
                            + "\" masih terbit di katalog. Tarik Paket itu dari katalog dulu "
                            + "sebelum " + tindakan + " di dalamnya.");
                });
    }

    /** Konten milik sebuah Client dan konten yang tidak ada sama-sama 404 (TC-09). */
    private QuestionEntity requireMasterQuestion(UUID id) {
        return questions.findByIdAndClientId(id, null)
                .orElseThrow(() -> new ResourceNotFoundException("Soal master tidak ditemukan"));
    }

    private PaketEntity requireMasterPaket(UUID id) {
        return pakets.findByIdAndClientIdIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));
    }

    private String ringkas(String bodyText) {
        return bodyText.length() <= 60 ? bodyText : bodyText.substring(0, 57) + "...";
    }
}
