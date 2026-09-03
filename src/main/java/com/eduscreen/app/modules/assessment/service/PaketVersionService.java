package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Versi Paket master (ADR-0021): membekukan versi kerja saat terbit, melahirkan versi kerja
 * baru dari versi terbit terakhir, dan melahirkan instance baru — Paket lain yang berbagi baris
 * soal yang sama.
 *
 * <p>Ketiganya cuma menyalin {@code paket_item}. Tidak ada satu pun baris {@code question} yang
 * lahir di sini; itulah yang membuat versi dan instance murah, dan yang membuat soal yang sama
 * benar-benar satu baris di mana pun ia dipakai.
 */
@Service
public class PaketVersionService {

    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final PaketItemRepository items;
    private final TopicRepository topics;
    private final ClientClock clock;

    public PaketVersionService(PaketRepository pakets, PaketVersionRepository versions,
                               PaketItemRepository items, TopicRepository topics, ClientClock clock) {
        this.pakets = pakets;
        this.versions = versions;
        this.items = items;
        this.topics = topics;
        this.clock = clock;
    }

    /**
     * Membekukan versi kerja: nomornya tetap, isinya tidak lagi bisa diubah, dan versi terbit
     * sebelumnya ditandai tergantikan. Dipanggil {@code MasterPublishingService.publishPaket};
     * gerbang isi (minimal satu soal terbit, AC-B16) ada di sana.
     */
    @Transactional
    public PaketVersionEntity freeze(PaketVersionEntity draft) {
        OffsetDateTime sekarang = clock.now();
        versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(draft.getPaketId())
                .ifPresent(sebelumnya -> {
                    sebelumnya.supersede(sekarang);
                    versions.save(sebelumnya);
                });
        draft.publish(sekarang);
        return versions.save(draft);
    }

    /**
     * Versi kerja baru dari versi terbit terakhir: seluruh penempatannya disalin, soalnya tidak.
     * Ditolak kalau Paket masih punya versi kerja (tidak ada dua versi kerja) atau belum pernah
     * terbit (tidak ada yang bisa dijadikan dasar).
     */
    @Transactional
    public PaketVersionEntity newVersion(UUID paketId, UUID actor) {
        PaketEntity paket = requireMaster(paketId);
        if (versions.findDraft(paketId).isPresent()) {
            throw new IllegalArgumentException("Paket ini masih punya versi kerja; ubah di sana");
        }
        PaketVersionEntity dasar = versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(paketId)
                .orElseThrow(() -> new IllegalArgumentException("Paket ini belum pernah terbit; versi kerjanya sudah ada"));
        PaketVersionEntity draft = versions.save(PaketVersionEntity.draft(paket, versions.nextNomor(paketId), actor));
        for (PaketItemEntity item : items.findByVersionOrdered(dasar.getId())) {
            items.save(item.copyTo(draft));
        }
        return draft;
    }

    /**
     * Instance baru: Paket master lain di Subject yang sama, dengan Topic disalin sebagai label
     * (satu baris per Topic, ADR-0018) dan penempatan yang menunjuk soal yang sama persis.
     * Dasarnya versi yang sedang dibaca — versi kerja bila ada, kalau tidak versi terbit
     * terakhir. Instance lahir belum terbit dan tidak membawa jejak ke asalnya: sejak lahir ia
     * Paket yang berdiri sendiri.
     */
    @Transactional
    public PaketEntity newInstance(UUID paketId, String title, UUID actor) {
        PaketEntity sumber = requireMaster(paketId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Judul Paket tidak boleh kosong");
        }
        PaketVersionEntity dasar = versions.findDraft(paketId)
                .or(() -> versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(paketId))
                .orElseThrow(() -> new IllegalStateException("Paket " + paketId + " tidak punya versi"));

        PaketEntity baru = pakets.save(PaketEntity.master(sumber.getSubjectId(), title.trim(), actor));
        PaketVersionEntity draft = versions.save(PaketVersionEntity.draft(baru, 1, actor));
        Map<UUID, TopicEntity> topicBaru = new HashMap<>();
        for (TopicEntity t : topics.findByPaketIdOrderByPositionAsc(sumber.getId())) {
            topicBaru.put(t.getId(), topics.save(TopicEntity.of(baru.getId(), t.getTitle(), t.getPosition())));
        }
        for (PaketItemEntity item : items.findByVersionOrdered(dasar.getId())) {
            items.save(item.relocatedTo(draft, topicBaru.get(item.getTopicId())));
        }
        return baru;
    }

    /** Paket milik Client dan Paket yang tidak ada sama-sama 404 (TC-09); versi hanya milik master. */
    private PaketEntity requireMaster(UUID paketId) {
        return pakets.findByIdAndClientIdIsNull(paketId)
                .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));
    }
}
