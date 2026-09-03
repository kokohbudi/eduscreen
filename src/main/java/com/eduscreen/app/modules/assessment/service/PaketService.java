package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Daur hidup Paket, versi kerjanya, dan Topic di dalamnya.
 *
 * <p>{@code clientId} null berarti bekerja di ruang kerja master Eduscreen. Satu layanan untuk
 * dua sisi: bentuk datanya sama, yang berbeda hanya pemiliknya (ADR-0018).
 *
 * <p>Kelas ini satu-satunya tempat Paket <b>baru</b> dirakit beserta versi kerja dan Topic
 * bawaannya. Merakitnya di tempat lain berarti aturan lahir Paket — versi 1, Topic bawaan,
 * pemilihan pemilik, pemeriksaan Subject — tersebar dan lambat laun tidak lagi seragam. Salinan
 * hasil adopsi katalog bukan kelahiran: ia dirakit {@code ContentAdoptionService} dari Paket
 * master beserta Topic aslinya.
 */
@Service
public class PaketService {

    /** Topic bawaan supaya soal pertama bisa langsung ditulis (AC-B01). */
    static final String TOPIC_BAWAAN = "Topik 1";

    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final TopicRepository topics;
    private final TaxonomyService taxonomy;

    public PaketService(PaketRepository pakets, PaketVersionRepository versions,
                        TopicRepository topics, TaxonomyService taxonomy) {
        this.pakets = pakets;
        this.versions = versions;
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
        // Versi kerja pertama lahir bersama Paketnya: tanpa versi tidak ada tempat menaruh soal
        // (ADR-0021), sama seperti tanpa Topic bawaan tidak ada tempat menulis.
        versions.save(PaketVersionEntity.draft(paket, 1, actor));
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

    /**
     * Versi kerja sebuah Paket: tempat soal ditulis, dipindah, dan dihapus.
     *
     * <p>Tidak menyaring kepemilikan — pemanggil wajib sudah lolos {@link #require} untuk
     * {@code paketId} ini (TC-36). Paket tanpa versi kerja adalah keadaan yang tidak pernah
     * dibuat kode ini (setiap Paket lahir bersama versi 1, dan V11 memberi satu ke tiap Paket
     * lama), jadi ketiadaannya adalah cacat data, bukan 404.
     */
    public PaketVersionEntity versionOf(UUID paketId) {
        return versions.findDraft(paketId)
                .orElseThrow(() -> new IllegalStateException("Paket " + paketId + " tidak punya versi kerja"));
    }

    public List<TopicEntity> topicsOf(UUID paketId) {
        return topics.findByPaketIdOrderByPositionAsc(paketId);
    }

    /**
     * Topic tersebar lintas banyak Paket sekaligus, dipetakan sekali untuk label kolom Topic di
     * tabel hasil panel pinjam ({@code BankSoalController}/{@code MasterContentController}) —
     * bukan satu query per baris. Tidak menyaring kepemilikan: {@code ids} datang dari
     * {@code Question} yang sudah lolos {@code searchForBuilder}/{@code searchMasterBorrowable},
     * jadi sudah tenant-aman sebelum sampai sini; method ini murni pembacaan label tampilan.
     */
    public List<TopicEntity> topicsByIds(Collection<UUID> ids) {
        return topics.findAllById(ids);
    }

    /**
     * Menerjemahkan judul Topic yang diketik di editor soal menjadi Topic sungguhan: dipakai ulang
     * kalau namanya sudah ada di Paket ini, dibuat baru kalau belum (perbandingan tanpa peduli
     * besar-kecil huruf, sehingga "Aljabar" dan "aljabar" tidak jadi dua Topic kembar).
     *
     * <p>Ini padanan cara Subject ditangani saat membuat Paket: satu kolom ber-datalist, bukan
     * dua jalur terpisah "pilih dari daftar" dan "tambah baru".
     */
    @Transactional
    public TopicEntity resolveTopic(UUID paketId, String title, UUID clientId) {
        require(paketId, clientId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Judul Topic tidak boleh kosong");
        }
        String bersih = title.trim();
        return topics.findByPaketIdOrderByPositionAsc(paketId).stream()
                .filter(t -> t.getTitle().equalsIgnoreCase(bersih))
                .findFirst()
                .orElseGet(() -> topics.save(
                        TopicEntity.of(paketId, bersih, topics.nextPosition(paketId))));
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
