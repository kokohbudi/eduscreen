package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    Optional<AssignmentEntity> findByIdAndClientId(UUID id, UUID clientId);

    List<AssignmentEntity> findByClientIdAndRuanganIdOrderByCreatedAtDesc(UUID clientId, UUID ruanganId);

    List<AssignmentEntity> findByClientIdAndStatusOrderByExpiresAtAsc(UUID clientId, AssignmentStatus status);

    /** Portal Siswa merangkum Assignment PUBLISHED lintas seluruh Ruangan miliknya (FR-058). */
    List<AssignmentEntity> findByClientIdAndRuanganIdInAndStatusOrderByExpiresAtAsc(
            UUID clientId, Collection<UUID> ruanganIds, AssignmentStatus status);

    long countByExerciseId(UUID exerciseId);
}
