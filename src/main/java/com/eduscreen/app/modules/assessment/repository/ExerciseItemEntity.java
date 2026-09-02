package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Urutan soal yang disusun Guru di satu Exercise; boleh lintas Subject dan Topic (FR-024). */
@Entity
@Table(name = "exercise_item")
public class ExerciseItemEntity {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int position;

    protected ExerciseItemEntity() {
    }

    public ExerciseItemEntity(UUID exerciseId, UUID questionId, int position) {
        this.id = UuidV7.randomUuid();
        this.exerciseId = exerciseId;
        this.questionId = questionId;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
