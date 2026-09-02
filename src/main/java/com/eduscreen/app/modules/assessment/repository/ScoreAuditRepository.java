package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Tabel hanya-sisip (TC-37): tidak ada jalur update atau delete di repository ini. */
public interface ScoreAuditRepository extends JpaRepository<ScoreAuditEntity, UUID> {

    List<ScoreAuditEntity> findByResultIdOrderByChangedAtAsc(UUID resultId);

    List<ScoreAuditEntity> findByClientIdOrderByChangedAtDesc(UUID clientId);
}
