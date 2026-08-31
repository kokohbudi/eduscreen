package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.RuanganStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface RuanganRepository extends JpaRepository<RuanganEntity, UUID> {

    Optional<RuanganEntity> findByIdAndClientId(UUID id, UUID clientId);

    List<RuanganEntity> findByClientIdOrderByNameAsc(UUID clientId);

    List<RuanganEntity> findByClientIdAndStatusOrderByNameAsc(UUID clientId, RuanganStatus status);
}
