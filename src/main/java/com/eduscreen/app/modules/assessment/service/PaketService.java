package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Daur hidup Paket dan Topic di dalamnya.
 *
 * <p>{@code clientId} null berarti bekerja di ruang kerja master Eduscreen. Satu layanan untuk
 * dua sisi: bentuk datanya sama, yang berbeda hanya pemiliknya (ADR-0018).
 *
 * <p>Kelas ini satu-satunya tempat Paket <b>baru</b> dirakit beserta Topic bawaannya. Merakitnya
 * di tempat lain berarti aturan lahir Paket — Topic bawaan, pemilihan pemilik, pemeriksaan
 * Subject — tersebar dan lambat laun tidak lagi seragam. Salinan hasil adopsi katalog bukan
 * kelahiran: ia dirakit {@code ContentAdoptionService} dari Paket master beserta Topic aslinya.
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
                ? pakets.findByIdAndClientIdIsNull(id)
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
}
