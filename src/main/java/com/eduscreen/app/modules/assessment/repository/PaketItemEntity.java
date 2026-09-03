package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Penempatan satu soal di dalam satu versi Paket: Topic mana, urutan berapa (ADR-0021).
 *
 * <p>Soal yang sama boleh punya banyak item — satu per versi atau Paket yang memuatnya — tanpa
 * satu pun baris {@code question} baru. Itu yang membuat versi dan instance Paket murah.
 *
 * <p>{@code clientId} adalah pemilik SOAL, disalin saat item dibuat dan dikunci dua FK komposit
 * di {@code paket_item}: soal milik sebuah Client tidak pernah bisa ditempatkan di versi master
 * maupun versi milik Client lain, apa pun yang ditulis kode pemanggil (TC-36).
 */
@Entity
@Table(name = "paket_item")
public class PaketItemEntity {

    @Id
    private UUID id;

    @Column(name = "paket_version_id", nullable = false)
    private UUID paketVersionId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int position;

    protected PaketItemEntity() {
    }

    public PaketItemEntity(PaketVersionEntity version, TopicEntity topic, QuestionEntity question, int position) {
        // Arah yang tidak diperiksa database (FK MATCH SIMPLE, lihat V11): soal master tidak
        // pernah ditempatkan langsung di versi milik Client — sekolah membacanya lewat akses.
        if (question.getClientId() == null && version.getClientId() != null) {
            throw new IllegalArgumentException("Soal master tidak bisa ditempatkan di Paket milik Client");
        }
        this.id = UuidV7.randomUuid();
        this.paketVersionId = version.getId();
        this.clientId = question.getClientId();
        this.topicId = topic.getId();
        this.questionId = question.getId();
        this.position = position;
    }

    /** Salinan penempatan ke versi lain: soal dan Topic yang sama, urutan yang sama. */
    public PaketItemEntity copyTo(PaketVersionEntity version) {
        PaketItemEntity copy = new PaketItemEntity();
        copy.id = UuidV7.randomUuid();
        copy.paketVersionId = version.getId();
        copy.clientId = clientId;
        copy.topicId = topicId;
        copy.questionId = questionId;
        copy.position = position;
        return copy;
    }

    /**
     * Penempatan yang sama di versi/Paket lain: soal yang sama, Topic tujuan yang diberikan
     * (instance baru menyalin Topic sebagai label baru), urutan yang sama.
     */
    public PaketItemEntity relocatedTo(PaketVersionEntity version, TopicEntity topic) {
        PaketItemEntity copy = copyTo(version);
        copy.topicId = topic.getId();
        return copy;
    }

    /** Menukar soal di penempatan ini dengan revisinya (ADR-0021); Topic dan urutan tetap. */
    public void replaceQuestion(QuestionEntity revision) {
        this.questionId = revision.getId();
        this.clientId = revision.getClientId();
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public void moveToTopic(TopicEntity topic) {
        this.topicId = topic.getId();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaketVersionId() {
        return paketVersionId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public int getPosition() {
        return position;
    }
}
