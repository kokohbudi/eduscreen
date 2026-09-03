package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.AssignmentStatus;
import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.shared.web.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Membuat draf Assignment dan menerbitkannya.
 *
 * <p>Gerbang validasi berjalan <b>saat penerbitan</b>, bukan saat perakitan Exercise (ADR-0003).
 * Exercise netral terhadap mode: satu kumpulan soal yang sama boleh terbit sebagai Quiz hari ini
 * dan sebagai Practice minggu depan, dan aturan yang berbeda antar-mode baru berlaku pada saat
 * ia benar-benar dipakai.
 *
 * <p>Satu Assignment menyasar tepat satu Ruangan (BR-M02). {@link #publishBulk} hanya kemudahan
 * antarmuka yang menghasilkan N Assignment terpisah — waktu, penutupan, dan rekap tiap Ruangan
 * tetap berdiri sendiri.
 */
@Service
public class AssignmentPublishingService {

    /** Muatan penerbitan; validitasnya diperiksa di {@link #publish}. */
    public record PublishRequest(UUID exerciseId,
                                 UUID ruanganId,
                                 String title,
                                 AssignmentMode mode,
                                 Integer timerDurationMinutes,
                                 OffsetDateTime expiresAt,
                                 int maxAttempts,
                                 boolean shuffleQuestions,
                                 boolean shuffleOptions,
                                 RevealAnswersAt revealAnswersAt) {
    }

    private final AssignmentRepository assignments;
    private final ExerciseRepository exercises;
    private final ExerciseItemRepository exerciseItems;
    private final QuestionRepository questions;
    private final RuanganService ruangan;
    private final ClientClock clock;

    public AssignmentPublishingService(AssignmentRepository assignments,
                                       ExerciseRepository exercises,
                                       ExerciseItemRepository exerciseItems,
                                       QuestionRepository questions,
                                       RuanganService ruangan,
                                       ClientClock clock) {
        this.assignments = assignments;
        this.exercises = exercises;
        this.exerciseItems = exerciseItems;
        this.questions = questions;
        this.ruangan = ruangan;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssignmentEntity require(UUID id, UserPrincipal guru) {
        AssignmentEntity assignment = assignments.findByIdAndClientId(id, guru.requireClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment tidak ditemukan"));
        requireAssignedGuru(assignment.getRuanganId(), guru);
        return assignment;
    }

    @Transactional
    public AssignmentEntity createDraft(PublishRequest request, UserPrincipal guru) {
        UUID clientId = guru.requireClientId();
        requireOwnRuangan(request.ruanganId(), guru);
        exercises.findByIdAndClientId(request.exerciseId(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise tidak ditemukan"));

        return assignments.save(new AssignmentEntity(
                clientId, request.exerciseId(), request.ruanganId(), guru.userId(),
                request.mode(), request.title(), request.timerDurationMinutes(),
                request.expiresAt(), Math.max(request.maxAttempts(), 1),
                request.shuffleQuestions(), request.shuffleOptions(),
                request.revealAnswersAt() == null ? RevealAnswersAt.AFTER_SUBMIT : request.revealAnswersAt()));
    }

    /**
     * Menerbitkan satu Assignment setelah seluruh gerbang validasi lolos.
     *
     * <p>Efek sampingnya mengunci Exercise: begitu Assignment pertamanya lahir, isi Exercise
     * tidak boleh berubah lagi, karena mengubahnya akan mengubah ujian yang sedang berjalan
     * (BR-E04, BR-M07).
     */
    @Transactional
    public AssignmentEntity publish(UUID assignmentId, UserPrincipal guru) {
        AssignmentEntity assignment = require(assignmentId, guru);
        if (!assignment.isDraft()) {
            throw new IllegalStateException("Hanya draf yang bisa diterbitkan");
        }
        requireOwnRuangan(assignment.getRuanganId(), guru);
        validate(assignment, guru.requireClientId());

        ExerciseEntity exercise = exercises
                .findByIdAndClientId(assignment.getExerciseId(), guru.requireClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise tidak ditemukan"));

        OffsetDateTime now = clock.now();
        exercise.lock(now);
        exercises.save(exercise);
        assignment.publish(now);
        return assignments.save(assignment);
    }

    /** Menerbitkan ke N Ruangan sekaligus; hasilnya tetap N Assignment terpisah (BR-M02). */
    @Transactional
    public List<AssignmentEntity> publishBulk(PublishRequest template,
                                              List<UUID> ruanganIds,
                                              UserPrincipal guru) {
        List<AssignmentEntity> published = new ArrayList<>();
        for (UUID ruanganId : ruanganIds) {
            PublishRequest request = new PublishRequest(
                    template.exerciseId(), ruanganId, template.title(), template.mode(),
                    template.timerDurationMinutes(), template.expiresAt(), template.maxAttempts(),
                    template.shuffleQuestions(), template.shuffleOptions(), template.revealAnswersAt());
            published.add(publish(createDraft(request, guru).getId(), guru));
        }
        return published;
    }

    /**
     * Gerbang validasi penerbitan (kontrak {@code assignment-publishing.md}).
     *
     * <p>Pesan kegagalan Practice menyebut <b>soal mana</b> penyebabnya. Guru yang hanya diberi
     * tahu "ada soal essay" harus membuka 40 soal satu per satu untuk menemukannya (BR-M04).
     */
    private void validate(AssignmentEntity assignment, UUID clientId) {
        List<UUID> questionIds = exerciseItems
                .findByExerciseIdOrderByPositionAsc(assignment.getExerciseId())
                .stream().map(ExerciseItemEntity::getQuestionId).toList();

        if (questionIds.isEmpty()) {
            throw new UnprocessableException("Exercise belum berisi satu soal pun");
        }
        if (assignment.getMode() == AssignmentMode.QUIZ && assignment.getTimerDurationMinutes() == null) {
            throw new UnprocessableException("Quiz wajib mengisi durasi Timer");
        }
        if (!assignment.getExpiresAt().isAfter(clock.now())) {
            throw new UnprocessableException("Batas akhir harus berada di masa depan");
        }

        if (assignment.getMode() == AssignmentMode.PRACTICE) {
            // Id datang dari item Exercise milik Client ini sendiri, jadi sudah tenant-aman; tidak
            // disaring akses Paket lagi — akses yang kedaluwarsa tidak boleh membuat soal esai
            // lolos validasi diam-diam (ADR-0021).
            List<QuestionEntity> content = questions.findAllById(questionIds);
            List<String> essays = new ArrayList<>();
            List<String> withoutExplanation = new ArrayList<>();
            for (QuestionEntity question : content) {
                int number = questionIds.indexOf(question.getId()) + 1;
                if (question.getType() == QuestionType.ESSAY) {
                    essays.add("soal " + number);
                } else if (question.getExplanationHtml() == null || question.getExplanationHtml().isBlank()) {
                    withoutExplanation.add("soal " + number);
                }
            }
            if (!essays.isEmpty()) {
                throw new UnprocessableException(
                        "Practice tidak boleh memuat soal essay: " + String.join(", ", essays));
            }
            if (!withoutExplanation.isEmpty()) {
                throw new UnprocessableException(
                        "Practice mewajibkan pembahasan pada setiap soal; belum ada di "
                                + String.join(", ", withoutExplanation));
            }
        }
    }

    /** Guru hanya boleh menerbitkan ke Ruangan tempat ia ditugaskan dan yang ACTIVE (BR-M01). */
    private void requireOwnRuangan(UUID ruanganId, UserPrincipal guru) {
        RuanganEntity target = ruangan.require(ruanganId, guru.requireClientId());
        if (target.isArchived() || !ruangan.isAssignedGuru(ruanganId, guru.userId())) {
            // 404, bukan 403: Ruangan yang bukan miliknya tidak boleh bisa dibedakan dari
            // Ruangan yang tidak ada (TC-09).
            throw new ResourceNotFoundException("Ruangan tidak ditemukan");
        }
    }

    private void requireAssignedGuru(UUID ruanganId, UserPrincipal guru) {
        if (!ruangan.isAssignedGuru(ruanganId, guru.userId())) {
            throw new ResourceNotFoundException("Assignment tidak ditemukan");
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentEntity> listForRuangan(UUID ruanganId, UserPrincipal guru) {
        requireAssignedGuru(ruanganId, guru);
        return assignments.findByClientIdAndRuanganIdOrderByCreatedAtDesc(guru.requireClientId(), ruanganId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentEntity> listPublished(UUID clientId) {
        return assignments.findByClientIdAndStatusOrderByExpiresAtAsc(clientId, AssignmentStatus.PUBLISHED);
    }
}
