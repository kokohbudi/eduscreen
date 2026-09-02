package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.ContentOrigin;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Taksonomi Subject dan Topic.
 *
 * <p>Subject GLOBAL milik Eduscreen dibaca semua Client sekaligus digabung dengan Subject lokal
 * milik Client itu sendiri (FR-013). Jenjang tidak menjadi kolom tersendiri — ia melekat di
 * nama Subject, mis. "Matematika Kelas 4" (ADR-0004), sehingga taksonomi tetap satu hierarki
 * datar tanpa tabel jenjang terpisah.
 *
 * <p>Topic boleh dibuat Client di bawah Subject GLOBAL (FR-014): sebuah sekolah sering perlu
 * menambah satu bab lokal tanpa harus menduplikasi seluruh mata pelajaran milik Eduscreen demi
 * satu Topic tambahan.
 *
 * <p>Sejak ADR-0018 Topic tidak lagi menggantung langsung di Subject: ia hidup di dalam satu
 * Paket, dan kepemilikannya diwarisi dari Paket itu. Daur hidup Paket beserta Topic-nya karena
 * itu dirakit satu tempat di {@link PaketService}; kelas ini tinggal mengurus Subject dan
 * pembacaan Topic.
 */
@Service
public class TaxonomyService {

    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final PaketRepository pakets;

    public TaxonomyService(SubjectRepository subjects, TopicRepository topics, PaketRepository pakets) {
        this.subjects = subjects;
        this.topics = topics;
        this.pakets = pakets;
    }

    @Transactional(readOnly = true)
    public List<SubjectEntity> visibleSubjects(UUID clientId) {
        return subjects.findVisibleTo(clientId);
    }

    /**
     * Topic di bawah satu Subject milik satu pemilik konten; {@code ownerClientId} null berarti
     * Eduscreen.
     *
     * <p>Menggantikan "Topic yang terlihat Client" yang berlaku sebelum ADR-0018. Kepemilikan
     * Topic kini diwarisi dari Paket, dan Paket tidak pernah dipakai bersama: yang dipakai
     * bersama hanya Subject. Daftar campuran master-plus-milik-sendiri karena itu tidak lagi
     * mewakili apa pun yang bisa dikerjakan pemanggilnya.
     */
    @Transactional(readOnly = true)
    public List<TopicEntity> topicsOwnedBy(UUID subjectId, UUID ownerClientId) {
        return ownerClientId == null
                ? topics.findMasterOwnedIn(subjectId)
                : topics.findOwnedBy(subjectId, ownerClientId);
    }

    @Transactional
    public SubjectEntity createClientSubject(UUID clientId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Subject wajib diisi");
        }
        return subjects.save(SubjectEntity.forClient(clientId, name.trim()));
    }

    /** Dipakai Eduscreen Admin untuk konten master yang dibaca semua Client. */
    @Transactional
    public SubjectEntity createGlobalSubject(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Subject wajib diisi");
        }
        String bersih = name.trim();
        requireNamaGlobalBelumDipakai(bersih);
        return subjects.save(SubjectEntity.global(bersih));
    }

    /**
     * Subject untuk sebuah Paket: dipakai ulang bila namanya sudah ada, dibuat bila belum.
     *
     * <p>Pencocokan mengabaikan besar-kecil huruf dan spasi tepi, supaya "Matematika Kelas 4" dan
     * "matematika kelas 4 " tidak melahirkan dua Subject yang bagi manusia sama. Bila nama itu
     * ada sebagai Subject GLOBAL sekaligus kembaran lokal — nama Subject Client tidak dipagari
     * indeks unik, jadi kembaran mungkin — GLOBAL yang menang: Client tidak perlu memelihara
     * salinan lokal dari mapel yang sudah disediakan Eduscreen, dan pemenangnya tidak boleh
     * bergantung pada urutan baris yang kebetulan.
     */
    public SubjectEntity findOrCreateSubject(String name, UUID clientId) {
        String bersih = name == null ? "" : name.trim();
        if (bersih.isEmpty()) {
            throw new IllegalArgumentException("Nama Subject tidak boleh kosong");
        }
        List<SubjectEntity> senama = visibleSubjects(clientId).stream()
                .filter(s -> s.getName().equalsIgnoreCase(bersih))
                .toList();
        return senama.stream()
                .filter(s -> s.getOrigin() == ContentOrigin.GLOBAL)
                .findFirst()
                .or(() -> senama.stream().findFirst())
                .orElseGet(() -> clientId == null
                        ? createGlobalSubject(bersih)
                        : createClientSubject(clientId, bersih));
    }

    /**
     * Memperbaiki nama Subject GLOBAL yang salah ketik.
     *
     * <p>Subject GLOBAL tidak pernah disalin ke Client (BR-O02): setiap sekolah menunjuk baris
     * yang sama, jadi perbaikan di sini langsung terlihat semua Client tanpa satu pun salinan
     * ikut disentuh.
     */
    @Transactional
    public SubjectEntity renameGlobalSubject(UUID id, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama Subject wajib diisi");
        }
        SubjectEntity subject = requireGlobalSubject(id);
        String bersih = name.trim();
        // Rename yang hanya membetulkan kapital atau spasi tepi tidak boleh menabrak dirinya sendiri.
        if (!bersih.equalsIgnoreCase(subject.getName())) {
            requireNamaGlobalBelumDipakai(bersih);
        }
        subject.rename(bersih);
        return subjects.save(subject);
    }

    /**
     * Taksonomi bersama kehilangan gunanya kalau Eduscreen sendiri yang menggandakan namanya
     * (ADR-0004). Pemeriksaan di sini yang memberi pesan terbaca; indeks unik V7 jaring terakhirnya.
     */
    private void requireNamaGlobalBelumDipakai(String name) {
        if (subjects.existsByOriginAndNameIgnoreCase(ContentOrigin.GLOBAL, name)) {
            throw new IllegalArgumentException("Subject global bernama \"" + name + "\" sudah ada");
        }
    }

    /** Subject milik sebuah Client tidak boleh menampung Topic master; ia diperlakukan seolah tidak ada. */
    @Transactional(readOnly = true)
    public SubjectEntity requireGlobalSubject(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Subject wajib dipilih");
        }
        SubjectEntity subject = subjects.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject tidak ditemukan"));
        if (subject.getOrigin() != ContentOrigin.GLOBAL) {
            throw new ResourceNotFoundException("Subject tidak ditemukan");
        }
        return subject;
    }

    /**
     * Topic yang boleh ditulisi pemilik konten yang sedang bekerja.
     *
     * <p>{@code clientId} null berarti Eduscreen Admin sedang menulis konten master, dan Paket
     * induk Topic-nya wajib master. Paket milik sebuah Client tidak boleh menampung Question
     * master: Question itu akan terbaca seluruh Client lewat katalog sementara Paket induknya
     * tidak, dan adopsi akan menyalin konten milik satu sekolah ke sekolah lain (FR-061, FR-082).
     *
     * <p>Sebaliknya juga, dan ini yang membedakannya dari {@link #requireVisibleTopic}: Client
     * hanya boleh menulis ke Paket miliknya sendiri. Paket master boleh ia BACA lewat katalog,
     * tidak boleh ia tulisi (ADR-0018). Aturan lama FR-014 — Topic milik Client di bawah Subject
     * GLOBAL — kini terwujud sebagai Paket milik Client yang menunjuk Subject global yang sama:
     * Subject tetap dipakai bersama, Paket tidak pernah. Database menegakkan hal yang sama lewat
     * {@code question_paket_same_owner} (V9), sehingga jalur tulis mana pun yang lupa memeriksa
     * tetap tertahan.
     */
    @Transactional(readOnly = true)
    public TopicEntity requireWritableTopic(UUID id, UUID clientId) {
        if (id == null) {
            throw new IllegalArgumentException("Soal wajib melekat pada satu Topic");
        }
        if (clientId != null) {
            return topics.findWritable(id, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic tidak ditemukan"));
        }
        return topics.findWritableMaster(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic tidak ditemukan"));
    }

    /** Batas tenant menyamakan "tidak ada" dan "milik Client lain" menjadi 404 (TC-09). */
    @Transactional(readOnly = true)
    public SubjectEntity requireVisibleSubject(UUID id, UUID clientId) {
        if (id == null) {
            throw new IllegalArgumentException("Subject wajib dipilih");
        }
        SubjectEntity subject = subjects.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject tidak ditemukan"));
        if (subject.getOrigin() != ContentOrigin.GLOBAL && !clientId.equals(subject.getClientId())) {
            throw new ResourceNotFoundException("Subject tidak ditemukan");
        }
        return subject;
    }

    /**
     * Subject yang menaungi sebuah Topic. Sejak ADR-0018 Topic tidak lagi membawa Subject
     * sendiri: ia mewarisinya dari Paket, jadi pertanyaannya dijawab satu tempat saja.
     */
    @Transactional(readOnly = true)
    public UUID subjectIdOf(TopicEntity topic) {
        return pakets.findById(topic.getPaketId())
                .orElseThrow(() -> new ResourceNotFoundException("Paket tidak ditemukan"))
                .getSubjectId();
    }
}
