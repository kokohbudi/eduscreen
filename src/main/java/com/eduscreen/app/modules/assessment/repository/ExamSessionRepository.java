package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan menyaring {@code clientId} secara eksplisit (TC-36). */
public interface ExamSessionRepository extends JpaRepository<ExamSessionEntity, UUID> {

    Optional<ExamSessionEntity> findByIdAndClientId(UUID id, UUID clientId);

    /** Jalur Siswa: kepemilikan sesi masuk klausa query, bukan pengecekan setelahnya (TC-08). */
    Optional<ExamSessionEntity> findByIdAndStudentIdAndClientId(UUID id, UUID studentId, UUID clientId);

    /** Kunci pesimistis mencegah dua request finalisasi balapan pada sesi yang sama (TC-18). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSessionEntity s where s.id = :id and s.clientId = :clientId")
    Optional<ExamSessionEntity> findByIdForUpdate(@Param("id") UUID id, @Param("clientId") UUID clientId);

    Optional<ExamSessionEntity> findFirstByAssignmentIdAndStudentIdAndStatusOrderByAttemptNumberDesc(
            UUID assignmentId, UUID studentId, SessionStatus status);

    List<ExamSessionEntity> findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(UUID assignmentId, UUID studentId);

    List<ExamSessionEntity> findByAssignmentId(UUID assignmentId);

    List<ExamSessionEntity> findByAssignmentIdAndStatus(UUID assignmentId, SessionStatus status);

    int countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    List<ExamSessionEntity> findByClientIdAndStudentIdOrderByStartedAtDesc(UUID clientId, UUID studentId);
}
