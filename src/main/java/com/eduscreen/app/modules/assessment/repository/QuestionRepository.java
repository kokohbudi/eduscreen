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
     * Varian {@link #search} untuk perakit Exercise: menambah saringan tipe soal dan pengecualian
     * soal yang sudah terpasang.
     *
     * <p>{@code excludeIds} tidak pernah kosong — {@code not in ()} bukan SQL yang sah. Pemanggil
     * mengirim UUID nil sebagai isi pengganti; UUIDv7 tidak pernah bernilai nol, jadi ia tidak
     * bisa berbenturan dengan pengenal soal mana pun (ADR-0009).
     */
    @Query("select q from QuestionEntity q where q.clientId = :clientId "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and (:type is null or q.type = :type) "
            + "and q.id not in :excludeIds "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchForBuilder(
            @Param("clientId") UUID clientId,
            @Param("topicId") UUID topicId,
            @Param("type") QuestionType type,
            @Param("excludeIds") Collection<UUID> excludeIds,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja konten master Eduscreen: seluruh baris ber-{@code client_id} null, draf maupun
     * terbit. Katalog Client TIDAK memakai ini — lihat {@link #searchPublishedMaster}.
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
     * Katalog Client: hanya konten master yang <b>sudah terbit</b> (FR-067, FR-074).
     *
     * <p>Sengaja query terpisah dari {@link #searchMaster}, bukan satu query dengan parameter
     * boolean: yang membedakan keduanya adalah siapa yang boleh melihat draf, dan perbedaan
     * sepenting itu tidak pantas disembunyikan di balik argumen yang mudah salah kirim.
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
