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

    /**
     * Seluruh Paket milik Client, lintas Subject — daftar pilihan Paket di panel perakit Exercise
     * (BR-E01, FR-024): perakit boleh menelusuri Paket mana pun di dalam Client, bukan hanya
     * Paket satu Subject seperti panel pinjam antar-Paket.
     */
    List<PaketEntity> findByClientIdOrderByTitleAsc(UUID clientId);

    /** Paket master, dipakai ruang kerja Eduscreen. */
    @Query("select p from PaketEntity p where p.clientId is null and p.subjectId = :subjectId "
            + "order by p.title asc")
    List<PaketEntity> findMaster(@Param("subjectId") UUID subjectId);

    /**
     * Seluruh Paket master, lintas Subject — padanan {@link #findByClientIdOrderByTitleAsc} untuk
     * tingkat pertama Bank Soal master (ADR-0018 revisi tingkat pertama): tabel Paket menyambut
     * langsung, tanpa memilih Subject lebih dulu.
     */
    @Query("select p from PaketEntity p where p.clientId is null order by p.title asc")
    List<PaketEntity> findAllMaster();

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
     * Antrean dashboard: Paket master yang benar-benar macet — masih draf dan belum punya satu pun
     * Question terbit, sehingga penerbitannya pasti ditolak (BR-O05, AC-B16). Paket kosong ikut di
     * sini: hasil adopsinya sama-sama nol soal.
     *
     * <p>Predikatnya sengaja disalin dari gerbang {@code MasterPublishingService.publishPaket}
     * ({@code QuestionRepository.countPublishedInPaket}), bukan memanggilnya per Paket dalam
     * sebuah perulangan: satu query untuk seluruh Paket master, bukan N+1.
     *
     * <p>Sejak ADR-0020 Paket yang memuat draf TIDAK lagi macet — ia bisa terbit dengan soal yang
     * sudah siap saja. Yang menyandera penerbitan tinggal satu: tidak ada isi yang siap.
     */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is null "
            + "and not exists (select q.id from QuestionEntity q where q.paketId = p.id "
            + "and q.publishedAt is not null) "
            + "order by p.updatedAt desc")
    List<PaketEntity> findMasterBlocked();

    /**
     * Antrean dashboard: Paket master yang tinggal diklik — masih draf, tapi sudah punya isi yang
     * siap terbit (AC-B16, BR-O05). Komplemen persis {@link #findMasterBlocked}.
     */
    @Query("select p from PaketEntity p where p.clientId is null and p.publishedAt is null "
            + "and exists (select q.id from QuestionEntity q where q.paketId = p.id "
            + "and q.publishedAt is not null) "
            + "order by p.updatedAt desc")
    List<PaketEntity> findMasterReadyToPublish();

    /** Paket yang sudah pernah diadopsi Client ini, untuk menandai katalog (FR-076). */
    @Query("select distinct p.sourcePaketId from PaketEntity p "
            + "where p.clientId = :clientId and p.sourcePaketId in :ids")
    List<UUID> findAdoptedSourceIds(@Param("clientId") UUID clientId,
                                    @Param("ids") Collection<UUID> ids);
}
