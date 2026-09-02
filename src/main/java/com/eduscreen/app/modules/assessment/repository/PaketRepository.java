package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan milik Client menyaring {@code clientId} di dalam query (TC-36). */
public interface PaketRepository extends JpaRepository<PaketEntity, UUID> {

    Optional<PaketEntity> findByIdAndClientId(UUID id, UUID clientId);

    /** Padanan {@link #findByIdAndClientId} untuk ruang kerja master: pemiliknya harus Eduscreen. */
    Optional<PaketEntity> findByIdAndClientIdIsNull(UUID id);

    List<PaketEntity> findByClientIdAndSubjectIdOrderByTitleAsc(UUID clientId, UUID subjectId);

    /** Paket master, dipakai ruang kerja Eduscreen. */
    @Query("select p from PaketEntity p where p.clientId is null and p.subjectId = :subjectId "
            + "order by p.title asc")
    List<PaketEntity> findMaster(@Param("subjectId") UUID subjectId);

    /** Paket master yang sudah terbit; satu-satunya yang boleh terlihat Client (FR-067). */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is not null "
            + "and p.subjectId = :subjectId order by p.title asc")
    List<PaketEntity> findMasterPublished(@Param("subjectId") UUID subjectId);

    @Query("select p from PaketEntity p where p.id = :id and p.clientId is null "
            + "and p.publishedAt is not null")
    Optional<PaketEntity> findPublishedMasterById(@Param("id") UUID id);

    /**
     * Seluruh Paket master terbit, lintas Subject — daftar pilihan onboarding Client baru
     * (FR-020, FR-067), yang tidak punya penyaring Subject seperti katalog Client.
     */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is not null "
            + "order by p.title asc")
    List<PaketEntity> findAllMasterPublished();

    /** Kartu dashboard: Paket master yang sudah terbit dan karena itu bisa diadopsi (FR-067). */
    @Query("select count(p) from PaketEntity p where p.clientId is null and p.publishedAt is not null")
    long countPublishedMaster();

    /**
     * Antrean dashboard: Paket master yang macet di gerbang AC-B12 — masih draf, tapi memuat
     * Question yang belum terbit sehingga penerbitannya pasti ditolak (BR-O05).
     *
     * <p>Predikat {@code exists}-nya sengaja disalin dari gerbang
     * {@code MasterPublishingService.publishPaket} ({@code QuestionRepository.findUnpublishedInPaket}),
     * bukan memanggilnya per Paket dalam sebuah perulangan: satu query untuk seluruh Paket master,
     * bukan N+1, mengikuti pola {@code ExerciseRepository.findMasterBlocked} sebelum Paket
     * menggantikan Exercise sebagai satuan konten master (ADR-0018).
     */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is null "
            + "and exists (select q.id from QuestionEntity q where q.paketId = p.id and q.publishedAt is null) "
            + "order by p.updatedAt desc")
    List<PaketEntity> findMasterBlocked();

    /**
     * Antrean dashboard: Paket master yang tinggal diklik — berisi (AC-B16) dan seluruh isinya
     * sudah terbit (BR-O05).
     */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is null "
            + "and exists (select q.id from QuestionEntity q where q.paketId = p.id) "
            + "and not exists (select q.id from QuestionEntity q where q.paketId = p.id "
            + "and q.publishedAt is null) "
            + "order by p.updatedAt desc")
    List<PaketEntity> findMasterReadyToPublish();

    /** Jumlah Paket per Subject untuk tabel tingkat pertama Bank Soal. */
    @Query("select p.subjectId as subjectId, count(p) as jumlah from PaketEntity p "
            + "where p.clientId = :clientId group by p.subjectId")
    List<SubjectCount> countBySubject(@Param("clientId") UUID clientId);

    @Query("select p.subjectId as subjectId, count(p) as jumlah from PaketEntity p "
            + "where p.clientId is null group by p.subjectId")
    List<SubjectCount> countMasterBySubject();

    /** Paket yang sudah pernah diadopsi Client ini, untuk menandai katalog (FR-076). */
    @Query("select distinct p.sourcePaketId from PaketEntity p "
            + "where p.clientId = :clientId and p.sourcePaketId in :ids")
    List<UUID> findAdoptedSourceIds(@Param("clientId") UUID clientId,
                                    @Param("ids") Collection<UUID> ids);

    /**
     * Apakah Client ini sudah pernah mengadopsi Paket master itu (FR-076, FR-077).
     *
     * <p>Dibaca dari {@code sourcePaketId} yang ditulis sejak adopsi pertama, bukan dari
     * pencocokan judul: master yang di-rename Eduscreen dan salinan yang dirapikan Client Admin
     * sama-sama membuat tebakan berdasarkan judul meleset.
     */
    boolean existsByClientIdAndSourcePaketId(UUID clientId, UUID sourcePaketId);

    interface SubjectCount {
        UUID getSubjectId();

        long getJumlah();
    }
}
