package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.ResultKind;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rekap nilai satu Assignment dan aktivitas latihan satu Ruangan.
 *
 * <p>Rekap dibangun dari <b>daftar anggota Ruangan</b>, bukan dari daftar sesi (BR-L01). Siswa
 * yang tidak pernah menekan Mulai tampil {@code NOT_STARTED} dengan skor 0 — tanpa satu baris
 * sesi pun dibuat untuknya, sehingga BR-S01 tetap utuh.
 *
 * <p>Membuka rekap juga memicu finalisasi seluruh sesi yang sudah lewat batas waktunya (BR-L02,
 * ADR-0002): browser Siswa yang tertutup dan tidak pernah kembali tetap menghasilkan Result,
 * dan Result itu lahir di sini.
 */
@Service
public class ReportService {

    /** Satu baris rekap; {@code sessions} kosong berarti {@code NOT_STARTED}. */
    public record Row(AppUserEntity student,
                      String status,
                      int attempts,
                      BigDecimal officialScore,
                      ResultStatus gradingStatus) {
    }

    private final ExamSessionRepository sessions;
    private final ResultRepository results;
    private final RuanganService ruangan;
    private final AssignmentPublishingService publishing;
    private final SessionFinalizer finalizer;

    public ReportService(ExamSessionRepository sessions,
                         ResultRepository results,
                         RuanganService ruangan,
                         AssignmentPublishingService publishing,
                         SessionFinalizer finalizer) {
        this.sessions = sessions;
        this.results = results;
        this.ruangan = ruangan;
        this.publishing = publishing;
        this.finalizer = finalizer;
    }

    @Transactional
    public List<Row> recap(UUID assignmentId, UserPrincipal guru) {
        AssignmentEntity assignment = publishing.require(assignmentId, guru);
        finalizeStaleSessions(assignmentId, assignment.getClientId());

        List<AppUserEntity> students = ruangan.membersOf(
                assignment.getRuanganId(), assignment.getClientId(), MemberRole.SISWA);

        Map<UUID, List<ExamSessionEntity>> byStudent = sessions.findByAssignmentId(assignmentId)
                .stream().collect(Collectors.groupingBy(ExamSessionEntity::getStudentId));

        Map<UUID, ResultEntity> resultBySession = resultsFor(byStudent.values().stream()
                .flatMap(List::stream).map(ExamSessionEntity::getId).toList());

        List<Row> rows = new ArrayList<>();
        for (AppUserEntity student : students) {
            List<ExamSessionEntity> own = byStudent.getOrDefault(student.getId(), List.of());
            if (own.isEmpty()) {
                rows.add(new Row(student, "NOT_STARTED", 0, BigDecimal.ZERO, ResultStatus.FINAL));
                continue;
            }
            List<ResultEntity> graded = own.stream()
                    .map(session -> resultBySession.get(session.getId()))
                    .filter(java.util.Objects::nonNull)
                    // Result Practice tidak masuk rekap nilai (BR-L04).
                    .filter(result -> result.getKind() == ResultKind.GRADED)
                    .toList();

            // Skor resmi adalah yang TERTINGGI di antara seluruh pengerjaan (BR-L03, BR-C07);
            // seluruh Attempt tetap bisa dibuka lewat halaman riwayat.
            BigDecimal best = graded.stream()
                    .map(ResultEntity::getScore)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            ResultStatus grading = graded.stream()
                    .anyMatch(result -> result.getStatus() == ResultStatus.PENDING_REVIEW)
                    ? ResultStatus.PENDING_REVIEW
                    : ResultStatus.FINAL;

            ExamSessionEntity latest = own.stream()
                    .max(Comparator.comparingInt(ExamSessionEntity::getAttemptNumber))
                    .orElseThrow();
            rows.add(new Row(student, latest.getStatus().name(), own.size(), best, grading));
        }
        return rows;
    }

    /** Seluruh pengerjaan seorang Siswa pada satu Assignment, terurut Attempt (FR-053). */
    @Transactional
    public List<ExamSessionEntity> attemptsOf(UUID assignmentId, UUID studentId, UserPrincipal guru) {
        AssignmentEntity assignment = publishing.require(assignmentId, guru);
        finalizeStaleSessions(assignmentId, assignment.getClientId());
        return sessions.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(assignmentId, studentId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ResultEntity> resultsFor(List<UUID> sessionIds) {
        return sessionIds.isEmpty()
                ? Map.of()
                : results.findBySessionIdIn(sessionIds).stream()
                        .collect(Collectors.toMap(ResultEntity::getSessionId, Function.identity()));
    }

    /**
     * Aktivitas latihan satu Ruangan, terpisah dari rekap nilai (BR-L04, AC-C03).
     *
     * <p>Practice adalah latihan, bukan penilaian. Mencampurnya ke rekap nilai akan membuat
     * Siswa yang rajin berlatih terlihat seperti Siswa yang nilainya jelek.
     */
    @Transactional(readOnly = true)
    public List<ResultEntity> practiceActivity(UUID ruanganId, UserPrincipal guru) {
        if (!ruangan.isAssignedGuru(ruanganId, guru.userId())) {
            throw new com.eduscreen.app.shared.web.ResourceNotFoundException("Ruangan tidak ditemukan");
        }
        List<UUID> assignmentIds = publishing.listForRuangan(ruanganId, guru).stream()
                .filter(AssignmentEntity::isPractice)
                .map(AssignmentEntity::getId)
                .toList();
        List<UUID> sessionIds = assignmentIds.stream()
                .flatMap(id -> sessions.findByAssignmentId(id).stream())
                .map(ExamSessionEntity::getId)
                .toList();
        return sessionIds.isEmpty() ? List.of() : results.findBySessionIdIn(sessionIds);
    }

    /**
     * Menutup sesi yang batas waktunya sudah lewat, <b>satu transaksi per sesi</b> (TC-21).
     *
     * <p>Satu transaksi panjang akan mengunci seluruh Ruangan sekaligus, dan Guru yang membuka
     * rekap kelas berisi 30 Siswa akan memblokir setiap Siswa yang sedang menyimpan jawaban.
     */
    private void finalizeStaleSessions(UUID assignmentId, UUID clientId) {
        for (ExamSessionEntity session :
                sessions.findByAssignmentIdAndStatus(assignmentId, SessionStatus.IN_PROGRESS)) {
            finalizer.finalizeIfExpired(session.getId(), clientId);
        }
    }
}
