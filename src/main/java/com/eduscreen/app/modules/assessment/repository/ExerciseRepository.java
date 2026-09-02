package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface ExerciseRepository extends JpaRepository<ExerciseEntity, UUID> {

    Optional<ExerciseEntity> findByIdAndClientId(UUID id, UUID clientId);

    Page<ExerciseEntity> findByClientIdOrderByUpdatedAtDesc(UUID clientId, Pageable pageable);

    List<ExerciseEntity> findByClientIdAndIdIn(UUID clientId, Collection<UUID> ids);

    /**
     * {@code pattern} sudah berupa pola {@code like} huruf kecil dan tidak pernah null — pemanggil
     * mengirim {@code "%"} bila tidak ada kata kunci.
     *
     * <p>Sengaja begitu: PostgreSQL tidak bisa menyimpulkan tipe parameter yang hanya muncul di
     * dalam {@code concat}, dan bentuk {@code (:q is null or ...)} gagal saat runtime dengan
     * {@code function lower(bytea) does not exist}. Menyiapkan pola di Java memindahkan
     * percabangan ke tempat yang tipenya jelas.
     */
    @Query("select e from ExerciseEntity e where e.clientId = :clientId "
            + "and lower(e.title) like :pattern "
            + "order by e.updatedAt desc")
    Page<ExerciseEntity> search(@Param("clientId") UUID clientId,
                                @Param("pattern") String pattern,
                                Pageable pageable);

    /** Katalog Client: hanya paket master yang sudah terbit (FR-067, FR-074). */
    @Query("select e from ExerciseEntity e where e.clientId is null and e.publishedAt is not null "
            + "and lower(e.title) like :pattern "
            + "order by e.updatedAt desc")
    Page<ExerciseEntity> searchPublishedMaster(@Param("pattern") String pattern, Pageable pageable);

    /** Gerbang adopsi paket: yang belum terbit tidak bisa diadopsi (FR-067, TC-09). */
    @Query("select e from ExerciseEntity e where e.id = :id "
            + "and e.clientId is null and e.publishedAt is not null")
    Optional<ExerciseEntity> findPublishedMasterById(@Param("id") UUID id);

    /** Kartu dashboard: paket master yang sudah terbit dan karena itu bisa diadopsi (FR-067). */
    @Query("select count(e) from ExerciseEntity e where e.clientId is null and e.publishedAt is not null")
    long countPublishedMaster();

    /**
     * Antrean dashboard: paket master yang macet di gerbang FR-069 — masih draf, tapi memuat
     * Question yang belum terbit sehingga penerbitannya pasti ditolak (BR-O05).
     *
     * <p>Satu query untuk seluruh paket, bukan perulangan
     * {@code QuestionRepository.findUnpublishedInExercise} per paket yang N+1.
     */
    @Query("select e from ExerciseEntity e where e.clientId is null and e.publishedAt is null "
            + "and exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id "
            + "and i.questionId in (select q.id from QuestionEntity q where q.publishedAt is null "
            + "and q.clientId is null)) "
            + "order by e.updatedAt desc")
    List<ExerciseEntity> findMasterBlocked();

    /**
     * Antrean dashboard: paket master yang tinggal diklik — draf, berisi (FR-072), dan seluruh
     * isinya sudah terbit (BR-O05).
     */
    @Query("select e from ExerciseEntity e where e.clientId is null and e.publishedAt is null "
            + "and exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id) "
            + "and not exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id "
            + "and i.questionId in (select q.id from QuestionEntity q where q.publishedAt is null "
            + "and q.clientId is null)) "
            + "order by e.updatedAt desc")
    List<ExerciseEntity> findMasterReadyToPublish();
}
