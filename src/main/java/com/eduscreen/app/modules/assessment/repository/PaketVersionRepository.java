package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kepemilikan versi diwarisi dari Paket; pemanggil wajib sudah lolos {@code PaketService.require}
 * sebelum membaca versi lewat {@code paketId} (TC-36).
 */
public interface PaketVersionRepository extends JpaRepository<PaketVersionEntity, UUID> {

    /** Versi kerja sebuah Paket — paling banyak satu (index parsial {@code paket_version_single_draft}). */
    @Query("select v from PaketVersionEntity v where v.paketId = :paketId and v.publishedAt is null")
    Optional<PaketVersionEntity> findDraft(@Param("paketId") UUID paketId);

    List<PaketVersionEntity> findByPaketIdOrderByNomorDesc(UUID paketId);

    Optional<PaketVersionEntity> findFirstByPaketIdAndPublishedAtIsNotNullOrderByNomorDesc(UUID paketId);

    /** Versi terbit tertentu dari Paket tertentu — sasaran pindah akses; selain itu kosong (TC-36). */
    Optional<PaketVersionEntity> findByIdAndPaketIdAndPublishedAtIsNotNull(UUID id, UUID paketId);

    @Query("select coalesce(max(v.nomor), 0) + 1 from PaketVersionEntity v where v.paketId = :paketId")
    int nextNomor(@Param("paketId") UUID paketId);

    /** Versi kerja seluruh Paket milik satu Client — bagian "milik sendiri" dari versi yang terlihat (TC-36). */
    @Query("select v.id from PaketVersionEntity v, PaketEntity p where v.paketId = p.id "
            + "and v.clientId = :clientId and v.publishedAt is null")
    List<UUID> findDraftIdsByClient(@Param("clientId") UUID clientId);
}
