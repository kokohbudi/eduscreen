package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Satu soal. {@code clientId} null berarti konten master milik Eduscreen; adopsi Client menjadi
 * SALINAN baru yang menandai asalnya lewat {@code sourceQuestionId}, tanpa sinkronisasi lanjutan
 * (ADR-0001).
 */
@Entity
@Table(name = "question")
@SQLRestriction("deleted_at is null")
public class QuestionEntity {

    @Id
    private UUID id;

    /** Null berarti konten master milik Eduscreen. */
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "paket_id", nullable = false)
    private UUID paketId;

    /** Urutan soal di dalam Topic-nya. Menggantikan peran ExerciseItem.position untuk bank soal. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    /** Turunan teks polos dari body_html, dipakai pencarian; kolom HTML tidak dicari (TC-25). */
    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "explanation_html")
    private String explanationHtml;

    @Column(name = "explanation_text")
    private String explanationText;

    /** Jejak adopsi saja; tidak dipakai untuk sinkronisasi apa pun (ADR-0001). */
    @Column(name = "source_question_id")
    private UUID sourceQuestionId;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * Terisi berarti terbit dan terlihat di katalog seluruh Client; kosong berarti masih digarap
     * dan hanya terlihat Eduscreen Admin (FR-066).
     *
     * <p>Hanya bermakna bagi konten master. Check constraint {@code question_publish_master_only} menolaknya
     * terisi pada baris milik sebuah Client.
     */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected QuestionEntity() {
    }

    public QuestionEntity(UUID clientId, UUID paketId, UUID topicId, QuestionType type,
                          String bodyHtml, String bodyText) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
        this.paketId = paketId;
        this.topicId = topicId;
        this.type = type;
        this.bodyHtml = bodyHtml;
        this.bodyText = bodyText;
    }

    /** Penghapusan bersifat soft delete (TC-35). */
    public void softDelete(OffsetDateTime now) {
        this.deletedAt = now;
    }

    /** Menerbitkan; idempoten, penerbitan ulang tidak menggeser waktu terbit pertama (FR-066). */
    public void publish(OffsetDateTime now) {
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    /**
     * Menarik dari peredaran (FR-068). Tidak menyentuh satu pun salinan yang sudah diadopsi
     * Client: adopsi adalah salinan penuh, bukan tautan hidup (ADR-0001).
     */
    public void unpublish() {
        this.publishedAt = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public void setTopicId(UUID topicId) {
        this.topicId = topicId;
    }

    public UUID getPaketId() {
        return paketId;
    }

    public int getPosition() {
        return position;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    /** Memindahkan soal ke Topic lain; Paket induknya ikut supaya keduanya tidak pernah berbeda. */
    public void reparent(UUID paketId, UUID topicId) {
        this.paketId = paketId;
        this.topicId = topicId;
    }

    public QuestionType getType() {
        return type;
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

    public String getExplanationHtml() {
        return explanationHtml;
    }

    public void setExplanationHtml(String explanationHtml) {
        this.explanationHtml = explanationHtml;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }

    public UUID getSourceQuestionId() {
        return sourceQuestionId;
    }

    /** Dipakai saat menyalin soal master (adopsi Client, ADR-0001): jejak asal, bukan tautan sinkronisasi. */
    public void setSourceQuestionId(UUID sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    /** Dipakai saat soal lahir lewat jalur lain dari konstruktor: adopsi (aktor) dan impor massal (pengunggah). */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
