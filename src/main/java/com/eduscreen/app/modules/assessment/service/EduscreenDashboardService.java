package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ringkasan yang dibaca dashboard Eduscreen Admin.
 *
 * <p>Setiap query di sini menyaring {@code client_id is null} — konten milik Eduscreen — kecuali
 * {@link ClientRepository#count()} yang membaca tabel {@code client} itu sendiri, yaitu entitas
 * yang memang Eduscreen kelola. Tidak ada satu pun jalur yang menyentuh Question, Exercise,
 * Ruangan, atau Session milik sebuah sekolah (FR-080, BR-P04). Batas itu dikunci
 * {@code EduscreenDashboardIT}, bukan sekadar diperiksa mata.
 */
@Service
public class EduscreenDashboardService {

    private final ClientRepository clients;
    private final QuestionRepository questions;
    private final ExerciseRepository exercises;

    public EduscreenDashboardService(ClientRepository clients,
                                     QuestionRepository questions,
                                     ExerciseRepository exercises) {
        this.clients = clients;
        this.questions = questions;
        this.exercises = exercises;
    }

    /** Tiga angka pintasan di kaki dashboard. */
    @Transactional(readOnly = true)
    public KartuDashboard kartu() {
        return new KartuDashboard(
                clients.count(),
                questions.countMaster(),
                exercises.countPublishedMaster());
    }

    public record KartuDashboard(long client, long questionMaster, long paketTerbit) {
    }
}
