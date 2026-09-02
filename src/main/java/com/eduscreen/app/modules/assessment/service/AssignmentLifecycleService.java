package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Perubahan Assignment setelah ia terbit.
 *
 * <p>Setelah terbit, hanya {@code expiresAt} yang boleh berubah, dan hanya <b>diperpanjang</b>
 * (BR-A02). Memajukannya akan memotong ujian yang sedang berjalan tanpa peringatan; mengubah
 * Timer atau mode akan membuat dua Siswa mengerjakan aturan yang berbeda dalam satu Assignment
 * yang sama (BR-A03).
 */
@Service
public class AssignmentLifecycleService {

    private final AssignmentRepository assignments;
    private final ExamSessionRepository sessions;
    private final AssignmentPublishingService publishing;
    private final SessionFinalizer finalizer;
    private final ClientClock clock;

    public AssignmentLifecycleService(AssignmentRepository assignments,
                                      ExamSessionRepository sessions,
                                      AssignmentPublishingService publishing,
                                      SessionFinalizer finalizer,
                                      ClientClock clock) {
        this.assignments = assignments;
        this.sessions = sessions;
        this.publishing = publishing;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    /**
     * Memperpanjang batas akhir.
     *
     * <p>{@code effective_deadline} sesi yang masih {@code IN_PROGRESS} dihitung ulang
     * <b>sekali</b> di sini; sesi yang sudah terminal tidak disentuh dan tidak dihidupkan
     * kembali (BR-T06).
     */
    @Transactional
    public AssignmentEntity extend(UUID assignmentId, OffsetDateTime newExpiry, UserPrincipal guru) {
        AssignmentEntity assignment = publishing.require(assignmentId, guru);
        if (assignment.isDraft()) {
            throw new IllegalStateException("Draf diubah lewat penyuntingan biasa, bukan perpanjangan");
        }
        if (!newExpiry.isAfter(assignment.getExpiresAt())) {
            throw new UnprocessableException("Batas akhir hanya bisa diperpanjang, tidak dimajukan");
        }
        assignment.extendExpiry(newExpiry);
        assignments.save(assignment);

        for (ExamSessionEntity session : sessions.findByAssignmentIdAndStatus(
                assignmentId, SessionStatus.IN_PROGRESS)) {
            session.recomputeDeadline(
                    ExamSessionService.effectiveDeadline(assignment, session.getStartedAt()));
            sessions.save(session);
        }
        return assignment;
    }

    /**
     * Menutup lebih awal: seluruh sesi {@code IN_PROGRESS} ikut difinalisasi dengan
     * {@code EXPIRATION_REACHED} (BR-A05).
     *
     * <p>Finalisasi berjalan satu transaksi per sesi, bukan satu transaksi panjang yang mengunci
     * seluruh Ruangan sekaligus (TC-21).
     */
    @Transactional
    public AssignmentEntity closeEarly(UUID assignmentId, UserPrincipal guru) {
        AssignmentEntity assignment = publishing.require(assignmentId, guru);
        if (!assignment.isPublished()) {
            throw new IllegalStateException("Hanya Assignment terbit yang bisa ditutup");
        }
        List<ExamSessionEntity> running =
                sessions.findByAssignmentIdAndStatus(assignmentId, SessionStatus.IN_PROGRESS);

        assignment.close(clock.now());
        assignments.save(assignment);

        for (ExamSessionEntity session : running) {
            finalizer.closeEarly(session.getId(), session.getClientId());
        }
        return assignment;
    }

    /** Hanya draf yang bisa dihapus; yang sudah terbit ditutup, bukan dihilangkan (BR-A04). */
    @Transactional
    public void deleteDraft(UUID assignmentId, UserPrincipal guru) {
        AssignmentEntity assignment = publishing.require(assignmentId, guru);
        if (!assignment.isDraft()) {
            throw new IllegalStateException(
                    "Assignment yang sudah terbit tidak bisa dihapus; tutup lebih awal sebagai gantinya");
        }
        assignments.delete(assignment);
    }
}
