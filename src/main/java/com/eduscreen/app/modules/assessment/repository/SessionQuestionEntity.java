package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snapshot beku soal di dalam satu sesi (BR-S02). Tidak pernah berubah setelah dibuat, termasuk
 * ketika Siswa kembali setelah terputus dan ketika soal aslinya di-soft-delete di tengah ujian
 * (BR-Q04).
 */
@Entity
@Table(name = "session_question")
public class SessionQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int position;

    /** Urutan Option hasil pengacakan untuk sesi ini; isinya urutan murni, tidak di-query per elemen. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "option_order", columnDefinition = "uuid[]")
    private UUID[] optionOrder;

    /** Terisi pada Practice saat jawaban pertama dikirim (BR-S07). */
    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    protected SessionQuestionEntity() {
    }

    public SessionQuestionEntity(UUID sessionId, UUID questionId, int position, UUID[] optionOrder) {
        this.id = UuidV7.randomUuid();
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.position = position;
        this.optionOrder = optionOrder;
    }

    /** Idempoten: hanya mengisi bila belum terkunci, sehingga penguncian ganda tetap aman. */
    public void lock(OffsetDateTime now) {
        if (this.lockedAt == null) {
            this.lockedAt = now;
        }
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public int getPosition() {
        return position;
    }

    public UUID[] getOptionOrder() {
        return optionOrder;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }
}
