package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ContentOrigin;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Topic di bawah satu Subject. Boleh milik Client meski Subject induknya GLOBAL (FR-014),
 * sehingga origin ditegakkan di lapis Topic sendiri, bukan diwarisi dari induknya.
 */
@Entity
@Table(name = "topic")
@SQLRestriction("deleted_at is null")
public class TopicEntity {

    @Id
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentOrigin origin;

    /** Null hanya untuk origin GLOBAL. */
    @Column(name = "client_id")
    private UUID clientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * Topic master yang menurunkan Topic ini, atau null bila Guru menulisnya sendiri.
     *
     * <p>Jejak asal saja (ADR-0001), sejajar {@code sourceQuestionId}: tidak dipakai untuk
     * sinkronisasi apa pun, dan tidak menghalangi adopsi kedua melahirkan Topic baru (FR-077).
     */
    @Column(name = "source_topic_id")
    private UUID sourceTopicId;

    protected TopicEntity() {
    }

    private TopicEntity(UUID subjectId, UUID clientId, String name, ContentOrigin origin) {
        if (origin == ContentOrigin.GLOBAL ? clientId != null : clientId == null) {
            throw new IllegalArgumentException(
                    "Origin GLOBAL wajib clientId null; origin CLIENT wajib clientId terisi");
        }
        this.id = UuidV7.randomUuid();
        this.subjectId = subjectId;
        this.clientId = clientId;
        this.name = name;
        this.origin = origin;
    }

    /** Topic master milik Eduscreen, dibaca semua Client. */
    public static TopicEntity global(UUID subjectId, String name) {
        return new TopicEntity(subjectId, null, name, ContentOrigin.GLOBAL);
    }

    /** Topic buatan satu Client, hanya dibaca Client itu sendiri. */
    public static TopicEntity forClient(UUID subjectId, UUID clientId, String name) {
        return new TopicEntity(subjectId, clientId, name, ContentOrigin.CLIENT);
    }

    /** Topic Client hasil adopsi, membawa pengenal Topic master yang menurunkannya. */
    public static TopicEntity adoptedFrom(UUID subjectId, UUID clientId, String name, UUID sourceTopicId) {
        TopicEntity topic = forClient(subjectId, clientId, name);
        topic.sourceTopicId = sourceTopicId;
        return topic;
    }

    /** Penghapusan bersifat soft delete (TC-35). */
    public void softDelete(OffsetDateTime now) {
        this.deletedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getName() {
        return name;
    }

    public ContentOrigin getOrigin() {
        return origin;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSourceTopicId() {
        return sourceTopicId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
