package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Satu pilihan jawaban. Tepat satu benar per soal pilihan ganda, ditegakkan lewat unique index
 * parsial di database, bukan di sini (FR-016, TC-19). Tanpa soft delete: opsi mengikuti siklus
 * hidup soal induknya.
 */
@Entity
@Table(name = "question_option")
public class QuestionOptionEntity {

    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int position;

    protected QuestionOptionEntity() {
    }

    public QuestionOptionEntity(UUID questionId, String bodyHtml, String bodyText, boolean correct, int position) {
        this.id = UuidV7.randomUuid();
        this.questionId = questionId;
        this.bodyHtml = bodyHtml;
        this.bodyText = bodyText;
        this.correct = correct;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
