package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {

    Optional<ResultEntity> findBySessionId(UUID sessionId);

    Optional<ResultEntity> findByIdAndClientId(UUID id, UUID clientId);

    List<ResultEntity> findBySessionIdIn(Collection<UUID> sessionIds);

    List<ResultEntity> findByClientIdAndStatus(UUID clientId, ResultStatus status);
}
