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
 *
 * <p>Soal tidak lagi membawa Paket/Topic/urutan sendiri (ADR-0021): setiap penyaring per Paket
 * atau Topic di sini menjoin {@link PaketItemEntity} dan {@link PaketVersionEntity}. Pola
 * subquery-nya sama di semua query supaya satu perbaikan berlaku ke semuanya. Pencarian ruang
 * kerja master menyembunyikan soal yang sudah digantikan revisi ({@code supersededById}):
 * baris lama masih dirujuk versi terbit dan sesi, tapi bukan lagi yang ditawarkan untuk dipakai.
 */
public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {

    Optional<QuestionEntity> findByIdAndClientId(UUID id, UUID clientId);

    /**
     * Padanan {@link #findByIdAndClientId} untuk konten master: pemiliknya harus Eduscreen.
     *
     * <p>Satu idiom untuk satu pertanyaan. Sebelumnya "master atau Client" ditanyakan tiga cara
     * berbeda di tiga tempat — termasuk {@code findById(...)} lalu disaring di Java, yang membaca
     * lintas-tenant lebih dulu dan baru menolak sesudahnya. Bentuk itu justru yang TC-36 hindari,
     * dan penamaannya di sini sejajar {@code PaketRepository.findByIdAndClientIdIsNull}.
     */
    Optional<QuestionEntity> findByIdAndClientIdIsNull(UUID id);

    List<QuestionEntity> findByClientIdAndIdIn(UUID clientId, Collection<UUID> ids);

    /**
     * Seluruh soal satu Topic di satu versi Paket, terurut posisi item — dipakai perakit Exercise
     * saat Guru menambahkan satu Topic sekaligus, pinjam per Topic, dan penomoran baris. Soal
     * yang sudah dihapus lunak tidak ikut ({@code @SQLRestriction}).
     *
     * <p>{@code clientId} null berarti konten master; disebut eksplisit di predikat, bukan
     * diserahkan ke derived query, supaya bentuknya sama dengan query lain di sini.
     */
    @Query("select q from QuestionEntity q, PaketItemEntity i "
            + "where i.questionId = q.id and i.paketVersionId = :versionId and i.topicId = :topicId "
            + "and ((:clientId is null and q.clientId is null) or q.clientId = :clientId) "
            + "order by i.position asc")
    List<QuestionEntity> findByVersionAndTopicOrdered(@Param("clientId") UUID clientId,
                                                      @Param("versionId") UUID versionId,
                                                      @Param("topicId") UUID topicId);

    /**
     * Pencarian panel perakit Exercise: menambah saringan Paket, tipe soal, dan pengecualian
     * soal yang sudah terpasang. Satu-satunya pencarian bank soal Client yang tersisa sejak
     * {@code GET /soal} lama dicabut (Task 14) — dipanggil dengan {@code paketId} null dari
     * panel yang menelusuri lintas Paket.
     *
     * <p>{@code paketId} disaring di dalam query utama ini, bukan di kode pemanggil (TC-36):
     * Paket milik Client lain otomatis menghasilkan nol baris karena klausa {@code clientId}
     * di atasnya sudah menutup jalan, tanpa perlu pemeriksaan kepemilikan Paket terpisah di sini.
     *
     * <p>{@code excludeIds} tidak pernah kosong — {@code not in ()} bukan SQL yang sah. Pemanggil
     * mengirim UUID nil sebagai isi pengganti; UUIDv7 tidak pernah bernilai nol, jadi ia tidak
     * bisa berbenturan dengan pengenal soal mana pun (ADR-0009).
     *
     * <p>{@code subjectId} ditambahkan untuk panel pinjam ({@code BankSoalController#panelPinjam},
     * AC-B19): satu-satunya penyaring yang belum ada di sini sebelumnya, dan sengaja ditambahkan
     * ke query yang sudah ada ini alih-alih melahirkan query kelima.
     */
    @Query("select q from QuestionEntity q where q.clientId = :clientId "
            + "and (:type is null or q.type = :type) "
            + "and q.id not in :excludeIds "
            + "and lower(q.bodyText) like :pattern "
            + "and exists (select i from PaketItemEntity i, PaketVersionEntity v, PaketEntity p "
            + "  where i.questionId = q.id and i.paketVersionId = v.id and v.paketId = p.id "
            + "  and (:subjectId is null or p.subjectId = :subjectId) "
            + "  and (:paketId is null or p.id = :paketId) "
            + "  and (:topicId is null or i.topicId = :topicId)) "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchForBuilder(
            @Param("clientId") UUID clientId,
            @Param("subjectId") UUID subjectId,
            @Param("paketId") UUID paketId,
            @Param("topicId") UUID topicId,
            @Param("type") QuestionType type,
            @Param("excludeIds") Collection<UUID> excludeIds,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja konten master Eduscreen: seluruh baris ber-{@code client_id} null, draf maupun
     * terbit — dipakai {@code QuestionService.searchMaster} saat penyaring status tidak dipilih.
     *
     * <p>{@code paketId} dan {@code excludeIds} ditambahkan untuk panel pinjam ruang kerja master
     * ({@code QuestionService.searchMasterBorrowable}) — baris "diklik" di tabel Paket atas panel
     * itu menyaring lewat parameter yang sama ini, bukan query keempat.
     * {@code excludeIds} idiomnya sama seperti {@link #searchForBuilder}: tidak pernah kosong.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.supersededById is null "
            + "and q.id not in :excludeIds "
            + "and lower(q.bodyText) like :pattern "
            + "and exists (select i from PaketItemEntity i, PaketVersionEntity v, PaketEntity p "
            + "  where i.questionId = q.id and i.paketVersionId = v.id and v.paketId = p.id "
            + "  and (:subjectId is null or p.subjectId = :subjectId) "
            + "  and (:paketId is null or p.id = :paketId) "
            + "  and (:topicId is null or i.topicId = :topicId)) "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchMaster(
            @Param("subjectId") UUID subjectId,
            @Param("paketId") UUID paketId,
            @Param("topicId") UUID topicId,
            @Param("excludeIds") Collection<UUID> excludeIds,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja konten master Eduscreen: penyaring status TERBIT
     * ({@code QuestionService.searchMaster}, kasus {@code StatusTerbit.TERBIT}).
     *
     * <p>Sengaja query terpisah dari {@link #searchMaster} dan
     * {@code searchUnpublishedMaster}, bukan satu query dengan parameter status: perbedaan siapa
     * yang boleh melihat draf tidak pantas disembunyikan di balik argumen yang mudah salah kirim.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.publishedAt is not null "
            + "and q.supersededById is null "
            + "and lower(q.bodyText) like :pattern "
            + "and exists (select i from PaketItemEntity i, PaketVersionEntity v, PaketEntity p "
            + "  where i.questionId = q.id and i.paketVersionId = v.id and v.paketId = p.id "
            + "  and (:subjectId is null or p.subjectId = :subjectId) "
            + "  and (:topicId is null or i.topicId = :topicId)) "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchPublishedMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Ruang kerja master, disaring pada yang masih digarap (BR-O05). Query terpisah, alasan yang
     * sama dengan {@link #searchPublishedMaster}.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.publishedAt is null "
            + "and q.supersededById is null "
            + "and lower(q.bodyText) like :pattern "
            + "and exists (select i from PaketItemEntity i, PaketVersionEntity v, PaketEntity p "
            + "  where i.questionId = q.id and i.paketVersionId = v.id and v.paketId = p.id "
            + "  and (:subjectId is null or p.subjectId = :subjectId) "
            + "  and (:topicId is null or i.topicId = :topicId)) "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchUnpublishedMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Pilihan penerbitan Paket (AC-B12, ADR-0020): soal di dalam satu versi yang masih digarap.
     */
    @Query("select q from QuestionEntity q, PaketItemEntity i "
            + "where i.questionId = q.id and i.paketVersionId = :versionId and q.publishedAt is null")
    List<QuestionEntity> findUnpublishedInVersion(@Param("versionId") UUID versionId);

    /** Gerbang penerbitan Paket (AC-B16): yang dihitung adalah soal TERBIT, bukan seluruh isi. */
    @Query("select count(q) from QuestionEntity q, PaketItemEntity i "
            + "where i.questionId = q.id and i.paketVersionId = :versionId and q.publishedAt is not null")
    long countPublishedInVersion(@Param("versionId") UUID versionId);

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
     * Jejak pinjam yang sudah mendarat di satu versi Paket: {@code sourceQuestionId} tiap
     * salinan hasil pinjam-antar-Paket yang belum disunting (AC-B04).
     */
    @Query("select q.sourceQuestionId from QuestionEntity q, PaketItemEntity i "
            + "where i.questionId = q.id and i.paketVersionId = :versionId and q.sourceQuestionId is not null")
    List<UUID> findSourceIdsInVersion(@Param("versionId") UUID versionId);

    /**
     * Jumlah soal per Paket untuk tabel tingkat kedua Bank Soal — versi kerja tiap Paket.
     *
     * <p>Satu query beragregasi untuk seluruh Paket satu Client, bukan satu {@code count} per
     * baris tabel — jumlah query tidak boleh tumbuh sebanding jumlah Paket.
     */
    @Query("select v.paketId as paketId, count(q) as jumlah "
            + "from QuestionEntity q, PaketItemEntity i, PaketVersionEntity v "
            + "where i.questionId = q.id and i.paketVersionId = v.id and v.publishedAt is null "
            + "and q.clientId = :clientId group by v.paketId")
    List<PaketCount> countByPaket(@Param("clientId") UUID clientId);

    /** Padanan {@link #countByPaket} untuk ruang kerja Eduscreen: seluruh Paket master. */
    @Query("select v.paketId as paketId, count(q) as jumlah "
            + "from QuestionEntity q, PaketItemEntity i, PaketVersionEntity v "
            + "where i.questionId = q.id and i.paketVersionId = v.id and v.publishedAt is null "
            + "and q.clientId is null group by v.paketId")
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
}
