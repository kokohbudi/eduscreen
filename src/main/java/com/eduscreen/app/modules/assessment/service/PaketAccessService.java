package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.assessment.repository.PaketAccessEntity;
import com.eduscreen.app.modules.assessment.repository.PaketAccessRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Akses sekolah ke Paket master (ADR-0021): diberi Eduscreen Admin, dibaca Guru lewat versi
 * yang ditunjuk, tanpa satu pun baris soal berpindah tangan.
 *
 * <p>Kelas ini juga satu-satunya sumber "versi apa saja yang terlihat Client C": versi kerja
 * Paket miliknya sendiri ditambah versi yang ditunjuk akses aktifnya. Setiap query bank soal
 * sisi sekolah menerima daftar itu sebagai parameter eksplisit (TC-36), bukan menghitungnya
 * sendiri lewat subquery yang tersebar.
 */
@Service
public class PaketAccessService {

    /** {@code in ()} bukan SQL yang sah; UUID nil tidak pernah menjadi id sungguhan (ADR-0009). */
    private static final UUID SENTINEL = new UUID(0L, 0L);

    private final PaketAccessRepository accesses;
    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final TopicRepository topics;
    private final ClientRepository clients;
    private final ClientClock clock;

    public PaketAccessService(PaketAccessRepository accesses, PaketRepository pakets,
                              PaketVersionRepository versions, TopicRepository topics,
                              ClientRepository clients, ClientClock clock) {
        this.accesses = accesses;
        this.pakets = pakets;
        this.versions = versions;
        this.topics = topics;
        this.clients = clients;
        this.clock = clock;
    }

    // ----------------------------------------------------------------- Eduscreen Admin

    /**
     * Memberi sekolah akses ke versi terbit terakhir sebuah Paket master (FR-067). Paket draf,
     * ditarik, atau tidak ada → 404 identik (TC-09). Akses yang sudah aktif tidak digandakan:
     * batas waktunya yang diperbarui.
     */
    @Transactional
    public PaketAccessEntity grant(UUID clientId, UUID paketId, OffsetDateTime validUntil, UUID actor) {
        clients.findById(clientId).orElseThrow(() -> new ResourceNotFoundException("Client tidak ditemukan"));
        PaketEntity paket = pakets.findPublishedMasterById(paketId)
                .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));
        PaketVersionEntity terbaru = versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(paket.getId())
                .orElseThrow(() -> new IllegalStateException("Paket terbit tanpa versi terbit"));

        Optional<PaketAccessEntity> aktif = accesses.findByClientIdAndPaketIdAndRevokedAtIsNull(clientId, paketId);
        if (aktif.isPresent()) {
            aktif.get().extend(validUntil);
            return accesses.save(aktif.get());
        }
        return accesses.save(PaketAccessEntity.grant(clientId, terbaru, validUntil, actor));
    }

    @Transactional
    public PaketAccessEntity revoke(UUID accessId) {
        PaketAccessEntity akses = accesses.findById(accessId)
                .orElseThrow(() -> new ResourceNotFoundException("Akses tidak ditemukan"));
        akses.revoke(clock.now());
        return accesses.save(akses);
    }

    /**
     * Memindahkan akses ke versi terbit lain dari Paket yang sama. {@code clientId} null berarti
     * Eduscreen Admin (boleh untuk sekolah mana pun); selain itu akses harus milik pemanggil.
     */
    @Transactional
    public PaketAccessEntity switchVersion(UUID accessId, UUID versionId, UUID clientId) {
        PaketAccessEntity akses = (clientId == null
                ? accesses.findById(accessId)
                : accesses.findByIdAndClientIdAndRevokedAtIsNull(accessId, clientId))
                .orElseThrow(() -> new ResourceNotFoundException("Akses tidak ditemukan"));
        PaketVersionEntity versi = versions.findByIdAndPaketIdAndPublishedAtIsNotNull(versionId, akses.getPaketId())
                .orElseThrow(() -> new ResourceNotFoundException("Versi tidak ditemukan"));
        akses.switchTo(versi);
        return accesses.save(akses);
    }

    /** Seluruh akses aktif sebuah sekolah, termasuk yang sudah lewat batas — untuk layar admin. */
    public List<PaketAccessEntity> activeFor(UUID clientId) {
        return accesses.findByClientIdAndRevokedAtIsNullOrderByGrantedAtDesc(clientId);
    }

    // ----------------------------------------------------------------- sisi sekolah

    /** Akses yang boleh dipakai sekarang: belum dicabut, belum lewat batas. */
    public List<PaketAccessEntity> usable(UUID clientId) {
        return accesses.findUsable(clientId, clock.now());
    }

    /**
     * Versi yang terlihat Client C: versi kerja Paket miliknya ∪ versi akses yang bisa dipakai.
     * Tidak pernah kosong — {@code in ()} bukan SQL yang sah — jadi diisi sentinel bila perlu.
     */
    public List<UUID> visibleVersionIds(UUID clientId) {
        List<UUID> ids = new ArrayList<>(versions.findDraftIdsByClient(clientId));
        usable(clientId).forEach(a -> ids.add(a.getVersionId()));
        if (ids.isEmpty()) {
            ids.add(SENTINEL);
        }
        return ids;
    }

    /** Akses yang bisa dipakai Client untuk satu Paket master, bila ada. */
    public Optional<PaketAccessEntity> usableAccess(UUID paketId, UUID clientId) {
        return accesses.findUsable(clientId, paketId, clock.now());
    }

    /** Versi terbit terakhir sebuah Paket — untuk menawarkan "pindah ke versi N" ke sekolah. */
    public Optional<PaketVersionEntity> latestPublished(UUID paketId) {
        return versions.findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(paketId);
    }

    /** Paket yang bisa dibaca Client: miliknya sendiri ∪ Paket master yang aksesnya bisa dipakai. */
    public List<PaketEntity> readablePakets(UUID clientId) {
        List<PaketEntity> semua = new ArrayList<>(pakets.findByClientIdOrderByTitleAsc(clientId));
        List<UUID> masterIds = usable(clientId).stream().map(PaketAccessEntity::getPaketId).toList();
        if (!masterIds.isEmpty()) {
            semua.addAll(pakets.findAllById(masterIds));
        }
        return semua;
    }

    /** Paket master yang aksesnya bisa dipakai Client ini, terurut judul. */
    public List<PaketEntity> masterPaketsFor(UUID clientId) {
        List<UUID> ids = usable(clientId).stream().map(PaketAccessEntity::getPaketId).toList();
        return ids.isEmpty() ? List.of() : pakets.findAllById(ids).stream()
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle())).toList();
    }

    /**
     * Paket yang boleh DIBACA pemanggil: miliknya, atau master yang aksesnya bisa dipakai.
     * Selain itu 404 identik (TC-09).
     */
    public PaketEntity requireReadable(UUID paketId, UUID clientId) {
        return pakets.findByIdAndClientId(paketId, clientId)
                .or(() -> accesses.findUsable(clientId, paketId, clock.now())
                        .flatMap(a -> pakets.findByIdAndClientIdIsNull(paketId)))
                .orElseThrow(() -> new ResourceNotFoundException("Paket tidak ditemukan"));
    }

    /**
     * Versi yang dibaca Client untuk sebuah Paket: versi kerja Paket miliknya, atau versi yang
     * ditunjuk aksesnya. Tanpa keduanya → 404 identik (TC-09).
     */
    public PaketVersionEntity visibleVersionOf(UUID paketId, UUID clientId) {
        if (pakets.findByIdAndClientId(paketId, clientId).isPresent()) {
            return versions.findDraft(paketId)
                    .orElseThrow(() -> new IllegalStateException("Paket " + paketId + " tidak punya versi kerja"));
        }
        return accesses.findUsable(clientId, paketId, clock.now())
                .flatMap(a -> versions.findById(a.getVersionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Paket tidak ditemukan"));
    }

    /**
     * Padanan {@link #visibleVersionOf} untuk Topic: Topic asing menghasilkan kosong, bukan galat.
     * Disaring di dalam query lewat daftar Paket yang terlihat (TC-36), bukan dibaca dulu lalu
     * ditolak di Java.
     */
    public Optional<PaketVersionEntity> visibleVersionOfTopic(UUID topicId, UUID clientId) {
        List<UUID> paketIds = readablePakets(clientId).stream().map(PaketEntity::getId).toList();
        if (paketIds.isEmpty()) {
            return Optional.empty();
        }
        return topics.findByIdAndPaketIdIn(topicId, paketIds)
                .map(t -> visibleVersionOf(t.getPaketId(), clientId));
    }

    /** Topic seluruh Paket yang terlihat Client di satu Subject — dropdown penyaring bank soal. */
    public List<TopicEntity> readableTopicsIn(UUID subjectId, UUID clientId) {
        List<UUID> paketIds = readablePakets(clientId).stream()
                .filter(p -> p.getSubjectId().equals(subjectId))
                .map(PaketEntity::getId).toList();
        return paketIds.isEmpty() ? List.of() : topics.findInPakets(paketIds);
    }
}
