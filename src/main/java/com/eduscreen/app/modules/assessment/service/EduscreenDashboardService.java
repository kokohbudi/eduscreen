package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final SubjectRepository subjects;

    public EduscreenDashboardService(ClientRepository clients,
                                     QuestionRepository questions,
                                     ExerciseRepository exercises,
                                     SubjectRepository subjects) {
        this.clients = clients;
        this.questions = questions;
        this.exercises = exercises;
        this.subjects = subjects;
    }

    /** Tiga angka pintasan di kaki dashboard. */
    @Transactional(readOnly = true)
    public KartuDashboard kartu() {
        return new KartuDashboard(
                clients.count(),
                questions.countMaster(),
                exercises.countPublishedMaster());
    }

    /** ponytail: batas nama yang ditampilkan per baris antrean; hitungannya tetap utuh. */
    private static final int BATAS_NAMA = 5;

    /**
     * Pekerjaan konten master yang macet (BR-O05).
     *
     * <p>Daftarnya dipotong di Java, bukan di query: katalog master berukuran ratusan baris, dan
     * tiga {@code Pageable} demi memotong daftar sependek ini lebih mahal dibaca daripada
     * dampaknya. Kalau katalog master tumbuh sampai puluhan ribu, pindahkan pemotongan ke query.
     */
    @Transactional(readOnly = true)
    public Antrean antrean() {
        return new Antrean(
                questions.countUnpublishedMaster(),
                Baris.dari(exercises.findMasterBlocked()),
                Baris.dari(exercises.findMasterReadyToPublish()),
                Baris.dari(subjects.findGlobalWithoutTopic()));
    }

    public record KartuDashboard(long client, long questionMaster, long paketTerbit) {
    }

    /** Satu baris antrean: sampai lima nama untuk ditampilkan, plus jumlah seluruhnya. */
    public record Baris<T>(List<T> tampil, long total) {

        public static <T> Baris<T> dari(List<T> semua) {
            return new Baris<>(semua.stream().limit(BATAS_NAMA).toList(), semua.size());
        }

        /** Yang tidak muat ditampilkan; dirender sebagai "…dan N lainnya". */
        public long sisa() {
            return total - tampil.size();
        }

        public boolean ada() {
            return total > 0;
        }
    }

    /**
     * Seluruh antrean. {@link #kosong()} menentukan apakah blok "Butuh perhatian" dirender sama
     * sekali: antrean kosong berarti bloknya hilang dan kartu naik jadi isi utama.
     */
    public record Antrean(long questionDraf,
                          Baris<ExerciseEntity> paketMacet,
                          Baris<ExerciseEntity> paketSiapTerbit,
                          Baris<SubjectEntity> subjectBuntu) {

        public boolean kosong() {
            return questionDraf == 0
                    && !paketMacet.ada()
                    && !paketSiapTerbit.ada()
                    && !subjectBuntu.ada();
        }
    }
}
