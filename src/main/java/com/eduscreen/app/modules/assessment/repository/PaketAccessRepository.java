package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Setiap pembacaan sisi sekolah menyebut {@code clientId} eksplisit (TC-36). */
public interface PaketAccessRepository extends JpaRepository<PaketAccessEntity, UUID> {

    Optional<PaketAccessEntity> findByClientIdAndPaketIdAndRevokedAtIsNull(UUID clientId, UUID paketId);

    Optional<PaketAccessEntity> findByIdAndClientIdAndRevokedAtIsNull(UUID id, UUID clientId);

    /** Akses aktif sebuah sekolah, termasuk yang sudah lewat batas (ditampilkan, ditandai). */
    List<PaketAccessEntity> findByClientIdAndRevokedAtIsNullOrderByGrantedAtDesc(UUID clientId);

    /** Akses yang boleh dipakai untuk pemakaian baru: belum dicabut, belum lewat batas. */
    @Query("select a from PaketAccessEntity a where a.clientId = :clientId and a.revokedAt is null "
            + "and (a.validUntil is null or a.validUntil > :now)")
    List<PaketAccessEntity> findUsable(@Param("clientId") UUID clientId, @Param("now") OffsetDateTime now);

    @Query("select a from PaketAccessEntity a where a.clientId = :clientId and a.paketId = :paketId "
            + "and a.revokedAt is null and (a.validUntil is null or a.validUntil > :now)")
    Optional<PaketAccessEntity> findUsable(@Param("clientId") UUID clientId, @Param("paketId") UUID paketId,
                                           @Param("now") OffsetDateTime now);
}
