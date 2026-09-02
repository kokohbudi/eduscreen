package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sub-bahasan di dalam satu Paket (ADR-0018).
 *
 * <p>Topic tidak lagi punya origin maupun clientId: kepemilikannya diwarisi dari Paket. Itu
 * membuat satu-satunya sumber kebenaran soal kepemilikan ada di satu tempat.
 */
@Entity
@Table(name = "topic")
@SQLRestriction("deleted_at is null")
public class TopicEntity {

    @Id
    private UUID id;

    @Column(name = "paket_id", nullable = false)
    private UUID paketId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Terisi berarti Topic ini ikut lenyap bersama Paket yang menaunginya.
     *
     * <p>Tidak ada mutator yang mengisinya: menyusun ulang, mengganti nama, dan menghapus Topic
     * adalah kemampuan BARU yang belum dibangun, bukan kemampuan yang hilang — model sebelum
     * ADR-0018 hanya bisa MEMBUAT Topic global. Kolomnya tetap ada karena V9 memang menuliskannya
     * saat memindahkan data lama, dan {@code @SQLRestriction} di atas yang menyembunyikan
     * barisnya (TC-35).
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected TopicEntity() {
    }

    private TopicEntity(UUID paketId, String title, int position) {
        this.id = UuidV7.randomUuid();
        this.paketId = paketId;
        this.title = title;
        this.position = position;
    }

    public static TopicEntity of(UUID paketId, String title, int position) {
        return new TopicEntity(paketId, title, position);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaketId() {
        return paketId;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
