package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAnswerRepository extends JpaRepository<SessionAnswerEntity, UUID> {

    Optional<SessionAnswerEntity> findBySessionQuestionId(UUID sessionQuestionId);

    List<SessionAnswerEntity> findBySessionQuestionIdIn(Collection<UUID> sessionQuestionIds);
}
