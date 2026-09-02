package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Daur hidup Paket dan Topic di dalamnya.
 *
 * <p>{@code clientId} null berarti bekerja di ruang kerja master Eduscreen. Satu layanan untuk
 * dua sisi: bentuk datanya sama, yang berbeda hanya pemiliknya (ADR-0018).
 *
 * <p>Kelas ini satu-satunya tempat {@link PaketEntity} dirakit bersama Topic-nya. Merakitnya di
 * tempat lain berarti aturan lahir Paket — Topic bawaan, pemilihan pemilik, pemeriksaan Subject
 * — tersebar dan lambat laun tidak lagi seragam.
 */
@Service
public class PaketService {

    /** Topic bawaan supaya soal pertama bisa langsung ditulis (AC-B01). */
    static final String TOPIC_BAWAAN = "Topik 1";

    private final PaketRepository pakets;
    private final TopicRepository topics;
    private final TaxonomyService taxonomy;

    public PaketService(PaketRepository pakets, TopicRepository topics, TaxonomyService taxonomy) {
        this.pakets = pakets;
        this.topics = topics;
        this.taxonomy = taxonomy;
    }

    /**
     * @param subjectId   Subject yang dipilih dari daftar, atau null
     * @param subjectName nama yang diketik; dipakai bila {@code subjectId} null
     */
    public record PaketDraft(String title, UUID subjectId, String subjectName) {
    }

    @Transactional
    public PaketEntity create(PaketDraft draft, UUID clientId, UUID actor) {
        if (draft.title() == null || draft.title().isBlank()) {
            throw new IllegalArgumentException("Judul Paket tidak boleh kosong");
        }
        // Ruang kerja master hanya menerima Subject GLOBAL (FR-061); Client menerima GLOBAL
        // ditambah miliknya sendiri. Dua gerbang berbeda, sama-sama berujung 404 bila lolos batas.
        UUID subjectId = draft.subjectId() != null
                ? requireSubject(draft.subjectId(), clientId).getId()
                : subjectByName(draft.subjectName(), clientId);

        PaketEntity paket = clientId == null
                ? PaketEntity.master(subjectId, draft.title().trim(), actor)
                : PaketEntity.forClient(clientId, subjectId, draft.title().trim(), actor);
        pakets.save(paket);
        topics.save(TopicEntity.of(paket.getId(), TOPIC_BAWAAN, 0));
        return paket;
    }

    private SubjectEntity requireSubject(UUID subjectId, UUID clientId) {
        return clientId == null
                ? taxonomy.requireGlobalSubject(subjectId)
                : taxonomy.requireVisibleSubject(subjectId, clientId);
    }

    private UUID subjectByName(String name, UUID clientId) {
        SubjectEntity subject = taxonomy.findOrCreateSubject(name, clientId);
        return subject.getId();
    }

    /** Paket yang boleh ditulis pemanggil. Milik Client lain menghasilkan 404, bukan 403 (TC-36). */
    public PaketEntity require(UUID id, UUID clientId) {
        return (clientId == null
                ? pakets.findById(id).filter(p -> p.getClientId() == null)
                : pakets.findByIdAndClientId(id, clientId))
                .orElseThrow(() -> new ResourceNotFoundException("Paket tidak ditemukan"));
    }

    public List<TopicEntity> topicsOf(UUID paketId) {
        return topics.findByPaketIdOrderByPositionAsc(paketId);
    }

    @Transactional
    public TopicEntity addTopic(UUID paketId, String title, UUID clientId) {
        require(paketId, clientId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Judul Topic tidak boleh kosong");
        }
        return topics.save(TopicEntity.of(paketId, title.trim(), topics.nextPosition(paketId)));
    }

    @Transactional
    public void softDelete(UUID id, UUID clientId) {
        PaketEntity paket = require(id, clientId);
        paket.softDelete(OffsetDateTime.now());
        pakets.save(paket);
    }

    // ------------------------------------------------- jembatan alur lama

    /**
     * Topic milik Client di bawah Subject GLOBAL atau miliknya sendiri (FR-014, TC-09).
     *
     * <p>Sementara sampai Task 6: alur lama masih menyebut "buat Topic" sebagai langkah pertama,
     * jadi satu Topic lahir bersama satu Paket sewadah bernama sama supaya punya induk yang sah.
     * Ruang kerja Bank Soal yang memisahkan pembuatan Paket sebagai langkah tersendiri ditulis
     * menyusul, dan jembatan ini ikut dibongkar bersamanya.
     */
    @Transactional
    public TopicEntity createClientTopic(UUID subjectId, UUID clientId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Topic wajib diisi");
        }
        taxonomy.requireVisibleSubject(subjectId, clientId);
        String bersih = name.trim();
        PaketEntity paket = pakets.save(PaketEntity.forClient(clientId, subjectId, bersih, null));
        return topics.save(TopicEntity.of(paket.getId(), bersih, 0));
    }

    /**
     * Topic master Eduscreen, di bawah Subject yang juga GLOBAL (FR-061).
     *
     * <p>Berbeda dengan Subject GLOBAL yang dibaca langsung dan tidak pernah disalin, Paket
     * master beserta Topic-nya <b>disalin</b> ke Client saat adopsi (BR-O02, AC-O02). Asimetri
     * itu disengaja dan ditegakkan {@code ContentAdoptionService}, bukan di sini.
     *
     * <p>Sementara sampai Task 6: lihat catatan yang sama di {@link #createClientTopic}.
     */
    @Transactional
    public TopicEntity createGlobalTopic(UUID subjectId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Topic wajib diisi");
        }
        taxonomy.requireGlobalSubject(subjectId);
        String bersih = name.trim();
        PaketEntity paket = pakets.save(PaketEntity.master(subjectId, bersih, null));
        return topics.save(TopicEntity.of(paket.getId(), bersih, 0));
    }
}
