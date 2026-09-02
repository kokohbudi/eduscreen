package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Kepemilikan tenant ditegakkan lewat {@code ExerciseEntity} induknya, bukan di sini. */
public interface ExerciseItemRepository extends JpaRepository<ExerciseItemEntity, UUID> {

    List<ExerciseItemEntity> findByExerciseIdOrderByPositionAsc(UUID exerciseId);

    Optional<ExerciseItemEntity> findByExerciseIdAndQuestionId(UUID exerciseId, UUID questionId);

    long countByExerciseId(UUID exerciseId);

    void deleteByExerciseId(UUID exerciseId);
}
