package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kepemilikan Topic diwarisi dari Paket, jadi penyaringan Client dilakukan lewat join ke Paket
 * di dalam query, bukan di kode pemanggil (TC-36).
 */
public interface TopicRepository extends JpaRepository<TopicEntity, UUID> {

    List<TopicEntity> findByPaketIdOrderByPositionAsc(UUID paketId);

    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and t.id = :id and p.clientId = :clientId")
    Optional<TopicEntity> findWritable(@Param("id") UUID id, @Param("clientId") UUID clientId);

    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and t.id = :id and p.clientId is null")
    Optional<TopicEntity> findWritableMaster(@Param("id") UUID id);

    /**
     * Topic di bawah satu Subject yang Paket induknya dimiliki Client ini.
     *
     * <p>Sengaja tanpa Paket master. Daftar Topic dipakai untuk MENGISI formulir tulis dan
     * menyaring bank soal milik sendiri; sejak ADR-0018 Client tidak bisa menulis ke Paket
     * master, dan soalnya juga tidak pernah berada di sana — menawarkannya hanya menyodorkan
     * pilihan yang dijamin 404 saat disimpan.
     */
    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and p.subjectId = :subjectId and p.clientId = :clientId "
            + "order by t.title asc")
    List<TopicEntity> findOwnedBy(@Param("subjectId") UUID subjectId, @Param("clientId") UUID clientId);

    /** Padanan {@link #findOwnedBy} untuk pemilik Eduscreen: ruang kerja master dan katalog. */
    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and p.subjectId = :subjectId and p.clientId is null "
            + "order by t.title asc")
    List<TopicEntity> findMasterOwnedIn(@Param("subjectId") UUID subjectId);

    /**
     * Pencocokan judul untuk impor massal: berkas impor hanya membawa nama Topic, tanpa Subject
     * maupun Paket. Batas tenant ikut ditegakkan di dalam query, bukan di kode pemanggil (TC-36).
     *
     * <p>Sengaja hanya Paket milik Client ini, bukan juga Paket master: impor adalah jalur
     * TULIS, dan Client tidak boleh menaruh soal ke dalam Paket milik Eduscreen (ADR-0018).
     * Judul yang kebetulan sama dengan Topic master karena itu tidak cocok.
     */
    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and lower(t.title) = lower(:title) "
            + "and p.clientId = :clientId "
            + "order by t.createdAt asc")
    List<TopicEntity> findWritableByTitle(@Param("title") String title, @Param("clientId") UUID clientId);

    @Query("select coalesce(max(t.position), -1) + 1 from TopicEntity t where t.paketId = :paketId")
    int nextPosition(@Param("paketId") UUID paketId);
}
