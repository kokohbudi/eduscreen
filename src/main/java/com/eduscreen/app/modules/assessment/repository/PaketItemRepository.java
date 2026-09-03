package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Penempatan soal per versi Paket (ADR-0021).
 *
 * <p>Tidak menyaring {@code clientId}: kepemilikan versi sudah dijaga saat pemanggil mendapat
 * {@code versionId} lewat {@code PaketService.require} → {@code versionOf} (TC-36). Setiap
 * query yang mengembalikan soal atau menghitung soal menjoin {@link QuestionEntity}, supaya
 * {@code @SQLRestriction} soft delete-nya ikut berlaku: item milik soal yang sudah dihapus lunak
 * tidak pernah terhitung (TC-35).
 */
public interface PaketItemRepository extends JpaRepository<PaketItemEntity, UUID> {

    /** Isi satu versi, urut Topic lalu urutan di dalam Topic — halaman isi Paket. */
    @Query("select i from PaketItemEntity i, TopicEntity t, QuestionEntity q "
            + "where i.topicId = t.id and i.questionId = q.id and i.paketVersionId = :versionId "
            + "order by t.position asc, i.position asc")
    List<PaketItemEntity> findByVersionOrdered(@Param("versionId") UUID versionId);

    @Query("select i from PaketItemEntity i, QuestionEntity q "
            + "where i.questionId = q.id and i.paketVersionId = :versionId and i.topicId = :topicId "
            + "order by i.position asc")
    List<PaketItemEntity> findByVersionAndTopicOrdered(@Param("versionId") UUID versionId,
                                                       @Param("topicId") UUID topicId);

    /** Isi Topic yang boleh keluar dari ruang kerja master: hanya soal terbit (ADR-0020). */
    @Query("select i from PaketItemEntity i, QuestionEntity q "
            + "where i.questionId = q.id and q.publishedAt is not null "
            + "and i.paketVersionId = :versionId and i.topicId = :topicId order by i.position asc")
    List<PaketItemEntity> findPublishedByVersionAndTopicOrdered(@Param("versionId") UUID versionId,
                                                                @Param("topicId") UUID topicId);

    Optional<PaketItemEntity> findByPaketVersionIdAndQuestionId(UUID versionId, UUID questionId);

    /**
     * Posisi berikutnya untuk soal baru di dalam satu Topic sebuah versi.
     *
     * <p>Dihitung di database, bukan dari ukuran daftar di memori: dua penulis yang menyimpan
     * bersamaan akan membaca angka yang sama kalau dihitung di aplikasi. Item soal yang sudah
     * dihapus lunak ikut dibuang saat penghapusan ({@code QuestionService.softDelete}), jadi
     * posisinya bisa dipakai ulang.
     */
    @Query("select coalesce(max(i.position), -1) + 1 from PaketItemEntity i "
            + "where i.paketVersionId = :versionId and i.topicId = :topicId")
    int nextPosition(@Param("versionId") UUID versionId, @Param("topicId") UUID topicId);

    @Query("select i.questionId from PaketItemEntity i, QuestionEntity q "
            + "where i.questionId = q.id and i.paketVersionId = :versionId")
    List<UUID> questionIdsOf(@Param("versionId") UUID versionId);

    @Query("select count(i) from PaketItemEntity i, QuestionEntity q "
            + "where i.questionId = q.id and i.paketVersionId = :versionId")
    long countByVersion(@Param("versionId") UUID versionId);

    /** Paket yang memuat soal ini, lewat versi mana pun — gerbang AC-B17 dan tautan editor. */
    @Query("select v.paketId from PaketItemEntity i, PaketVersionEntity v "
            + "where i.paketVersionId = v.id and i.questionId = :questionId")
    List<UUID> paketIdsContaining(@Param("questionId") UUID questionId);

    /** Penempatan satu soal beserta Paketnya. Satu baris per versi yang memuatnya. */
    @Query("select i.questionId as questionId, v.paketId as paketId, i.paketVersionId as versionId, "
            + "i.topicId as topicId, i.position as position "
            + "from PaketItemEntity i, PaketVersionEntity v "
            + "where i.paketVersionId = v.id and i.questionId in :questionIds")
    List<Placement> findPlacements(@Param("questionIds") Collection<UUID> questionIds);

    interface Placement {
        UUID getQuestionId();

        UUID getPaketId();

        UUID getVersionId();

        UUID getTopicId();

        int getPosition();
    }

    @Modifying
    @Query("delete from PaketItemEntity i where i.questionId = :questionId "
            + "and i.paketVersionId in (select v.id from PaketVersionEntity v where v.publishedAt is null)")
    void deleteDraftItemsOf(@Param("questionId") UUID questionId);
}
