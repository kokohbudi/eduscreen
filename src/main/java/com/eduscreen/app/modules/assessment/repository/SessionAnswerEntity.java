package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Satu baris per SessionQuestion — kunci alami upsert auto-save (TC-20). Kiriman ulang berisi
 * jawaban identik adalah no-op: antrean coba-ulang di klien menjamin server akan menerima
 * kiriman ganda, dan server yang menolaknya mengubah mekanisme pemulihan menjadi sumber
 * kerusakan.
 */
@Entity
@Table(name = "session_answer")
public class SessionAnswerEntity {

    @Id
    private UUID id;

    @Column(name = "session_question_id", nullable = false)
    private UUID sessionQuestionId;

    @Column(name = "selected_option_id")
    private UUID selectedOptionId;

    @Column(name = "essay_text")
    private String essayText;

    /** Dihitung saat disimpan untuk MCQ; null untuk essay sampai Guru menilai. */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "essay_score")
    private Integer essayScore;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt;

    protected SessionAnswerEntity() {
    }

    public SessionAnswerEntity(UUID sessionQuestionId) {
        this.id = UuidV7.randomUuid();
        this.sessionQuestionId = sessionQuestionId;
    }

    public void recordChoice(UUID selectedOptionId, boolean correct, OffsetDateTime now) {
        this.selectedOptionId = selectedOptionId;
        this.essayText = null;
        this.isCorrect = correct;
        this.answeredAt = now;
    }

    public void recordEssay(String essayText, OffsetDateTime now) {
        this.essayText = essayText;
        this.selectedOptionId = null;
        this.isCorrect = null;
        this.answeredAt = now;
    }

    public void grade(Integer essayScore) {
        this.essayScore = essayScore;
    }

    /** Terjawab bila ada pilihan atau esai yang tidak kosong; aturan tunggal untuk peta soal dan rekap. */
    public boolean isAnswered() {
        return selectedOptionId != null || (essayText != null && !essayText.isBlank());
    }

    /** Dipakai untuk memutuskan apakah kiriman ulang adalah no-op (TC-20). */
    public boolean sameAs(UUID selectedOptionId, String essayText) {
        return Objects.equals(this.selectedOptionId, selectedOptionId) && Objects.equals(this.essayText, essayText);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionQuestionId() {
        return sessionQuestionId;
    }

    public UUID getSelectedOptionId() {
        return selectedOptionId;
    }

    public String getEssayText() {
        return essayText;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public Integer getEssayScore() {
        return essayScore;
    }

    public OffsetDateTime getAnsweredAt() {
        return answeredAt;
    }
}
