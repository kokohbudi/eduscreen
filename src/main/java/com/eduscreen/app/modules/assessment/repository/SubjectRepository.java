package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ContentOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Seluruh pembacaan milik Client menyaring {@code clientId} secara eksplisit (TC-36). */
public interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {

    List<SubjectEntity> findByClientIdOrderByNameAsc(UUID clientId);

    /**
     * Penjaga duplikat nama Subject GLOBAL; jaring terakhirnya indeks unik
     * {@code subject_global_name_unique} (V7). Yang sudah dihapus tidak ikut terhitung — filter
     * {@code deleted_at is null} datang dari {@code @SQLRestriction} di entity.
     */
    boolean existsByOriginAndNameIgnoreCase(ContentOrigin origin, String name);

    /** Subject yang boleh dilihat satu Client: GLOBAL milik Eduscreen plus miliknya (FR-013). */
    @Query("select s from SubjectEntity s where s.origin = com.eduscreen.app.modules.assessment.domain.ContentOrigin.GLOBAL or s.clientId = :clientId order by s.name asc")
    List<SubjectEntity> findVisibleTo(@Param("clientId") UUID clientId);

    /**
     * Antrean dashboard: Subject GLOBAL yang belum punya satu pun Paket master (BR-O05). Di ruang
     * kerja master, tombol "+ Soal baru" hanya muncul setelah Topic dipilih, dan Topic hidup di
     * dalam Paket (ADR-0018) — Subject tanpa Paket adalah jalan buntu yang tidak menjelaskan
     * dirinya sendiri.
     *
     * <p>Subquery menyaring {@code p.clientId is null} dengan sengaja: Client boleh membuat Paket
     * di bawah Subject GLOBAL (FR-014), tapi Paket itu tidak terlihat di ruang kerja master, jadi
     * ia tidak pernah membuat Subject berhenti buntu bagi Eduscreen Admin (TC-36).
     */
    @Query("select s from SubjectEntity s "
            + "where s.origin = com.eduscreen.app.modules.assessment.domain.ContentOrigin.GLOBAL "
            + "and not exists (select p.id from PaketEntity p where p.subjectId = s.id "
            + "and p.clientId is null) "
            + "order by s.name asc")
    List<SubjectEntity> findGlobalWithoutTopic();
}
