package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestionEntity, UUID> {

    List<SessionQuestionEntity> findBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<SessionQuestionEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    Optional<SessionQuestionEntity> findBySessionIdAndPosition(UUID sessionId, int position);

    long countBySessionId(UUID sessionId);
}
