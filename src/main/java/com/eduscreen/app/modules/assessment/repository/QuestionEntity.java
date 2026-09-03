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
 * Satu soal: isi murni, tanpa penempatan (ADR-0021).
 *
 * <p>Di Paket mana, Topic mana, dan urutan berapa soal ini berada dicatat {@link PaketItemEntity},
 * satu baris per versi Paket yang memuatnya — sehingga satu soal boleh hidup di banyak versi dan
 * Paket tanpa disalin. {@code clientId} null berarti konten master milik Eduscreen.
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

    /**
     * Soal asal yang salinan ini masih kembarannya (ADR-0001, ADR-0018). Dikosongkan begitu
     * salinan disunting lewat editor; tidak dipakai untuk sinkronisasi apa pun.
     */
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

    /**
     * Terisi berarti soal ini sudah digantikan revisi yang lebih baru (Fase 2, ADR-0021). Baris
     * lama tetap ada: versi Paket terbit, Exercise, dan sesi yang menunjuknya tidak berubah.
     */
    @Column(name = "superseded_by_id")
    private UUID supersededById;

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

    public QuestionEntity(UUID clientId, QuestionType type, String bodyHtml, String bodyText) {
        this.id = UuidV7.randomUuid();
        this.clientId = clientId;
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

    public UUID getSupersededById() {
        return supersededById;
    }

    public boolean isSuperseded() {
        return supersededById != null;
    }

    /** Menandai soal ini sudah digantikan revisi baru; sekali jalan. */
    public void supersede(UUID newerId) {
        this.supersededById = newerId;
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
