package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Kepemilikan tenant ditegakkan lewat {@code QuestionEntity} induknya, bukan di sini. */
public interface QuestionOptionRepository extends JpaRepository<QuestionOptionEntity, UUID> {

    List<QuestionOptionEntity> findByQuestionIdOrderByPositionAsc(UUID questionId);

    List<QuestionOptionEntity> findByQuestionIdIn(Collection<UUID> questionIds);

    Optional<QuestionOptionEntity> findByIdAndQuestionId(UUID id, UUID questionId);

    void deleteByQuestionId(UUID questionId);
}
