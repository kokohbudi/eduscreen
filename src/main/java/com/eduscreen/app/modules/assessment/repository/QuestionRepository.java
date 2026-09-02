package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Setiap method yang membaca bank soal milik Client menyebut {@code clientId} secara eksplisit
 * (TC-36) — kebocoran itu bisa berjalan berbulan-bulan tanpa terdeteksi kalau disamarkan lewat
 * filter otomatis.
 */
public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {

    Optional<QuestionEntity> findByIdAndClientId(UUID id, UUID clientId);

    List<QuestionEntity> findByClientIdAndIdIn(UUID clientId, Collection<UUID> ids);

    /**
     * Seluruh Question sebuah Paket, terurut posisi — halaman isi Paket (bukan pencarian).
     *
     * <p>Tidak menyaring Paket yang sudah ter-soft-delete — baris di bawah Paket yang sudah
     * dihapus ikut terbawa. Pemanggil wajib memastikan Paket-nya masih hidup lebih dulu (mis.
     * lewat {@code PaketService.require}) sebelum memakai hasil query ini.
     */
    List<QuestionEntity> findByPaketIdOrderByPositionAsc(UUID paketId);

    /**
     * Seluruh Question sebuah Topic, terurut posisi — dipakai adopsi per Paket
     * ({@code ContentAdoptionService}) untuk menyalin isi tiap Topic master apa adanya, terlepas
     * dari keadaan terbit masing-masing Question (gerbang adopsi ada di tingkat Paket, FR-067).
     *
     * <p>Sama seperti {@link #findByPaketIdOrderByPositionAsc}, tidak menyaring Topic yang sudah
     * ter-soft-delete — pemanggil wajib memastikan Topic-nya masih hidup lebih dulu.
     */
    List<QuestionEntity> findByTopicIdOrderByPositionAsc(UUID topicId);

    List<QuestionEntity> findByClientIdAndTopicIdOrderByCreatedAtDesc(UUID clientId, UUID topicId);

    /**
     * Urut naik, dipakai perakit Exercise saat Guru menambahkan satu Topic sekaligus: urutan
     * tulis adalah urutan ajar, sedangkan varian {@code Desc} di atas melayani daftar bank soal
     * yang memang ingin menampilkan yang terbaru lebih dulu.
     */
    List<QuestionEntity> findByClientIdAndTopicIdOrderByCreatedAtAsc(UUID clientId, UUID topicId);

    /**
     * Pencarian menyentuh {@code body_text} yang teks polos, bukan HTML (TC-25).
     *
     * <p>{@code pattern} sudah berupa pola {@code like} huruf kecil dan tidak pernah null;
     * pemanggil mengirim {@code "%"} bila tidak ada kata kunci. PostgreSQL tidak bisa
     * menyimpulkan tipe parameter yang hanya muncul di dalam {@code concat}, sehingga bentuk
     * {@code (:q is null or ...)} gagal saat runtime.
     */
    @Query("select q from QuestionEntity q where q.clientId = :clientId "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> search(
            @Param("clientId") UUID clientId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Varian {@link #search} untuk perakit Exercise: menambah saringan Paket, tipe soal, dan
     * pengecualian soal yang sudah terpasang.
     *
     * <p>{@code paketId} disaring di dalam query utama ini, bukan di kode pemanggil (TC-36):
     * Paket milik Client lain otomatis menghasilkan nol baris karena klausa {@code clientId}
     * di atasnya sudah menutup jalan, tanpa perlu pemeriksaan kepemilikan Paket terpisah di sini.
     *
     * <p>{@code excludeIds} tidak pernah kosong — {@code not in ()} bukan SQL yang sah. Pemanggil
     * mengirim UUID nil sebagai isi pengganti; UUIDv7 tidak pernah bernilai nol, jadi ia tidak
     * bisa berbenturan dengan pengenal soal mana pun (ADR-0009).
     */
    @Query("select q from QuestionEntity q where q.clientId = :clientId "
            + "and (:paketId is null or q.paketId = :paketId) "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and (:type is null or q.type = :type) "
            + "and q.id not in :excludeIds "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchForBuilder(
            @Param("clientId") UUID clientId,
            @Param("paketId") UUID paketId,
            @Param("topicId") UUID topicId,
            @Param("type") QuestionType type,
            @Param("excludeIds") Collection<UUID> excludeIds,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja konten master Eduscreen: seluruh baris ber-{@code client_id} null, draf maupun
     * terbit — dipakai {@code QuestionService.searchMaster} saat penyaring status tidak dipilih.
     * Katalog Client tidak lagi menyaring Question satu per satu sejak ADR-0018; satuan
     * katalognya sekarang Paket ({@code PaketRepository.findMasterPublished}).
     *
     * <p>{@code question} tidak membawa {@code subject_id} sendiri, jadi penyaringan Subject
     * lewat subquery ke Paket induknya (ADR-0018).
     */
    @Query("select q from QuestionEntity q where q.clientId is null "
            + "and (:subjectId is null or q.paketId in (select p.id from PaketEntity p where p.subjectId = :subjectId)) "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja konten master Eduscreen: penyaring status TERBIT
     * ({@code QuestionService.searchMaster}, kasus {@code StatusTerbit.TERBIT}). Bukan jalur
     * katalog Client — katalog menyaring per Paket, bukan per Question, sejak ADR-0018.
     *
     * <p>Sengaja query terpisah dari {@link #searchMaster} dan
     * {@code searchUnpublishedMaster}, bukan satu query dengan parameter status: perbedaan siapa
     * yang boleh melihat draf tidak pantas disembunyikan di balik argumen yang mudah salah kirim.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.publishedAt is not null "
            + "and (:subjectId is null or q.paketId in (select p.id from PaketEntity p where p.subjectId = :subjectId)) "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchPublishedMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Gerbang adopsi: konten yang belum terbit tidak bisa diadopsi meski pengenalnya ditebak
     * (FR-067). Ketiadaan dan "belum terbit" sengaja menghasilkan hasil kosong yang sama,
     * sehingga pemanggil membalas 404 tanpa membocorkan bahwa pengenalnya sah (TC-09).
     */
    @Query("select q from QuestionEntity q where q.id = :id "
            + "and q.clientId is null and q.publishedAt is not null")
    Optional<QuestionEntity> findPublishedMasterById(@Param("id") UUID id);

    /** Gerbang penerbitan paket: Question di dalamnya yang masih digarap (FR-069). */
    @Query("select q from QuestionEntity q where q.publishedAt is null "
            + "and q.id in (select i.questionId from ExerciseItemEntity i where i.exerciseId = :exerciseId)")
    List<QuestionEntity> findUnpublishedInExercise(@Param("exerciseId") UUID exerciseId);

    /**
     * Gerbang penerbitan Paket (AC-B12, FR-067, FR-069 setara): Question di dalamnya yang masih
     * digarap. {@code question.paketId} sudah langsung menunjuk Paket-nya (ADR-0018), jadi tidak
     * perlu subquery lewat tabel perantara seperti {@link #findUnpublishedInExercise}.
     */
    @Query("select q from QuestionEntity q where q.publishedAt is null and q.paketId = :paketId")
    List<QuestionEntity> findUnpublishedInPaket(@Param("paketId") UUID paketId);

    /**
     * Penanda "sudah pernah diadopsi" untuk katalog (FR-076, FR-077).
     *
     * <p>{@code sourceQuestionId} sudah ditulis sejak adopsi pertama sebagai jejak asal
     * (ADR-0001); menelusurinya adalah pemakaian yang memang dirancang untuknya, bukan tabel
     * jejak baru. Dibatasi pada pengenal yang tampil di satu halaman katalog supaya biayanya
     * tetap datar berapa pun besar katalognya.
     */
    @Query("select distinct q.sourceQuestionId from QuestionEntity q "
            + "where q.clientId = :clientId and q.sourceQuestionId in :ids")
    List<UUID> findAdoptedSourceIds(@Param("clientId") UUID clientId, @Param("ids") Collection<UUID> ids);

    /**
     * Dipakai HANYA untuk memuat soal yang sudah tercatat di {@code session_question} milik
     * Siswa. Batas tenant di sini tidak perlu disaring ulang karena sudah ditegakkan lewat sesi
     * itu sendiri — jangan pakai method ini di luar pembacaan snapshot sesi.
     *
     * <p>Sengaja query native: ia harus <b>menembus</b> {@code @SQLRestriction} soft delete.
     * Menghapus sebuah soal saat ujian berjalan tidak boleh mengubah apa pun yang dilihat Siswa
     * yang sedang mengerjakannya, dan tidak boleh membuat Result-nya kehilangan soal saat
     * dihitung (BR-Q04, AC-Q02). Soal yang dihapus hilang dari pencarian bank soal — bukan dari
     * sesi yang sudah memakainya.
     */
    @Query(value = "select * from question where id in (:ids)", nativeQuery = true)
    List<QuestionEntity> findAllForSnapshot(@Param("ids") Collection<UUID> ids);

    /**
     * Posisi berikutnya untuk soal baru di dalam satu Topic.
     *
     * <p>Dihitung di database, bukan dari ukuran daftar di memori: dua penulis yang menyimpan
     * bersamaan akan membaca angka yang sama kalau dihitung di aplikasi. Soal yang sudah
     * dihapus lunak tidak ikut dihitung — {@code @SQLRestriction} pada {@code QuestionEntity}
     * sudah menyaringnya (TC-35) — sehingga posisi bisa dipakai ulang setelah penghapusan.
     */
    @Query("select coalesce(max(q.position), -1) + 1 from QuestionEntity q where q.topicId = :topicId")
    int nextPosition(@Param("topicId") UUID topicId);

    /**
     * Jejak pinjam yang sudah mendarat di Paket ini: {@code sourceQuestionId} tiap salinan yang
     * hasil pinjam-antar-Paket (AC-B04). Sama seperti {@link #findByPaketIdOrderByPositionAsc},
     * tidak menyaring Paket yang sudah ter-soft-delete — pemanggil wajib memastikan Paket
     * tujuannya masih hidup lebih dulu (mis. lewat {@code PaketService.require}).
     */
    @Query("select q.sourceQuestionId from QuestionEntity q "
            + "where q.paketId = :paketId and q.sourceQuestionId is not null")
    List<UUID> findSourceIdsInPaket(@Param("paketId") UUID paketId);

    /**
     * Gerbang penerbitan Paket (AC-B16, FR-072): Paket tanpa satu pun Question tidak boleh
     * terbit. Derived query, bukan {@code @Query}: cukup satu {@code exists}, tidak ada alasan
     * menulis JPQL tangan untuk itu.
     */
    boolean existsByPaketId(UUID paketId);

    /**
     * Jumlah soal per Paket untuk tabel tingkat kedua Bank Soal.
     *
     * <p>Satu query beragregasi untuk seluruh Paket satu Client, bukan satu {@code count} per
     * baris tabel — jumlah query tidak boleh tumbuh sebanding jumlah Paket.
     */
    @Query("select q.paketId as paketId, count(q) as jumlah from QuestionEntity q "
            + "where q.clientId = :clientId group by q.paketId")
    List<PaketCount> countByPaket(@Param("clientId") UUID clientId);

    /** Padanan {@link #countByPaket} untuk ruang kerja Eduscreen: seluruh Paket master. */
    @Query("select q.paketId as paketId, count(q) as jumlah from QuestionEntity q "
            + "where q.clientId is null group by q.paketId")
    List<PaketCount> countMasterByPaket();

    interface PaketCount {
        UUID getPaketId();

        long getJumlah();
    }

    /** Kartu dashboard: seluruh Question master, draf maupun terbit (FR-060). */
    @Query("select count(q) from QuestionEntity q where q.clientId is null")
    long countMaster();

    /** Antrean dashboard: Question master yang masih digarap (BR-O05, FR-066). */
    @Query("select count(q) from QuestionEntity q where q.clientId is null and q.publishedAt is null")
    long countUnpublishedMaster();

    /**
     * Ruang kerja master, disaring pada yang masih digarap (BR-O05).
     *
     * <p>Query terpisah, bukan parameter boolean pada {@link #searchMaster} — pola yang sama
     * dipakai {@link #searchPublishedMaster}: yang membedakan ketiganya adalah siapa yang boleh
     * melihat draf, dan perbedaan sepenting itu tidak pantas disembunyikan di balik argumen.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.publishedAt is null "
            + "and (:subjectId is null or q.paketId in (select p.id from PaketEntity p where p.subjectId = :subjectId)) "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchUnpublishedMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);
}
