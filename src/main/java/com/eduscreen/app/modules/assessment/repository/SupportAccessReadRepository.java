package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Tabel hanya-sisip (TC-46): tidak ada jalur update atau delete di repository ini. */
public interface SupportAccessReadRepository extends JpaRepository<SupportAccessReadEntity, UUID> {

    List<SupportAccessReadEntity> findByClientIdOrderByReadAtDesc(UUID clientId);
}
