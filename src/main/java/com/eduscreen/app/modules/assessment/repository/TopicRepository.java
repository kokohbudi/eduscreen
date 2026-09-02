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

    /** Yang terlihat satu Client: Topic di Paket master maupun di Paket miliknya sendiri (FR-014). */
    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and t.id = :id "
            + "and (p.clientId is null or p.clientId = :clientId)")
    Optional<TopicEntity> findVisible(@Param("id") UUID id, @Param("clientId") UUID clientId);

    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and p.subjectId = :subjectId "
            + "and (p.clientId is null or p.clientId = :clientId) "
            + "order by t.title asc")
    List<TopicEntity> findVisibleTo(@Param("subjectId") UUID subjectId, @Param("clientId") UUID clientId);

    /**
     * Pencocokan judul untuk impor massal: berkas impor hanya membawa nama Topic, tanpa Subject
     * maupun Paket. Batas tenant ikut ditegakkan di dalam query, bukan di kode pemanggil (TC-36).
     */
    @Query("select t from TopicEntity t, PaketEntity p "
            + "where t.paketId = p.id and lower(t.title) = lower(:title) "
            + "and (p.clientId is null or p.clientId = :clientId) "
            + "order by t.createdAt asc")
    List<TopicEntity> findVisibleByTitle(@Param("title") String title, @Param("clientId") UUID clientId);

    @Query("select coalesce(max(t.position), -1) + 1 from TopicEntity t where t.paketId = :paketId")
    int nextPosition(@Param("paketId") UUID paketId);
}
