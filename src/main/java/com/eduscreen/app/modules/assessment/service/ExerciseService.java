package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exercise: kumpulan Question yang dirakit Guru, netral terhadap mode Practice/Assignment.
 */
@Service
public class ExerciseService {

    private final ExerciseRepository exercises;
    private final ExerciseItemRepository items;
    private final QuestionRepository questions;
    private final ClientClock clock;

    public ExerciseService(ExerciseRepository exercises,
                           ExerciseItemRepository items,
                           QuestionRepository questions,
                           ClientClock clock) {
        this.exercises = exercises;
        this.items = items;
        this.questions = questions;
        this.clock = clock;
    }

    /**
     * Daftar Exercise satu Client — perakit Guru (ADR-0018: Exercise master, baris ber-
     * {@code client_id} null, sudah dicabut; katalog dan adopsi sejak itu satuannya Paket, lihat
     * {@code PaketService}/{@code ContentAdoptionService}).
     */
    @Transactional(readOnly = true)
    public Page<ExerciseEntity> list(UUID clientId, String q, Pageable pageable) {
        return exercises.search(clientId, likePattern(q), pageable);
    }

    /**
     * Katalog Client dan pilihan paket saat onboarding: hanya paket master yang sudah terbit
     * (FR-067, FR-074).
     */
    @Transactional(readOnly = true)
    public Page<ExerciseEntity> listPublishedMaster(String q, Pageable pageable) {
        return exercises.searchPublishedMaster(likePattern(q), pageable);
    }

    /** Pola {@code like} huruf kecil; kata kunci kosong menjadi {@code "%"}, bukan null. */
    static String likePattern(String keyword) {
        return keyword == null || keyword.isBlank()
                ? "%"
                : "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }

    @Transactional(readOnly = true)
    public ExerciseEntity require(UUID id, UUID clientId) {
        return exercises.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise tidak ditemukan"));
    }

    @Transactional
    public ExerciseEntity create(UUID clientId, String title, UUID createdBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Judul Exercise wajib diisi");
        }
        return exercises.save(new ExerciseEntity(clientId, title.trim(), createdBy));
    }

    @Transactional(readOnly = true)
    public List<ExerciseItemEntity> itemsOf(UUID exerciseId) {
        return items.findByExerciseIdOrderByPositionAsc(exerciseId);
    }

    @Transactional
    public void addQuestion(UUID exerciseId, UUID questionId, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        // Soal harus milik Client yang sama; soal milik Client lain atau tidak ada sama-sama
        // menghasilkan 404 (TC-09, TC-36). Sengaja TIDAK ada validasi Subject/Topic apa pun di
        // sini: Exercise boleh memuat soal lintas Subject dan Topic mana pun di dalam Client,
        // Guru berpindah bebas antar Topic dalam satu sesi perakitan (BR-E01, AC-E02, FR-024).
        questions.findByIdAndClientId(questionId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        if (items.findByExerciseIdAndQuestionId(exerciseId, questionId).isPresent()) {
            return;
        }
        int position = (int) items.countByExerciseId(exerciseId);
        items.save(new ExerciseItemEntity(exercise.getId(), questionId, position));
    }

    /**
     * Menambahkan beberapa Question terpilih sekaligus (BR-E01).
     *
     * <p>Urutannya mengikuti urutan {@code questionIds} yang dikirim, bukan urutan baris yang
     * dikembalikan database: yang Guru lihat saat mencentang adalah urutan hasil pencarian, dan
     * itulah urutan yang ia harapkan muncul di paket.
     *
     * <p>Pengenal yang bukan milik Client pemanggil dilewati, tidak melempar galat. Sengaja
     * sejajar {@link #addTopic}: pemanggilan borongan yang gagal separuh jalan meninggalkan
     * Exercise dalam keadaan yang tidak diminta siapa pun, dan membedakan "tidak ada" dari
     * "milik sekolah lain" justru yang TC-36 larang.
     *
     * @return jumlah soal yang benar-benar baru ditambahkan
     */
    @Transactional
    public int addQuestions(UUID exerciseId, List<UUID> questionIds, UUID clientId) {
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        Set<UUID> sah = new HashSet<>();
        for (QuestionEntity soal : questions.findByClientIdAndIdIn(clientId, questionIds)) {
            sah.add(soal.getId());
        }
        return append(exercise, questionIds.stream().filter(sah::contains).toList());
    }

    /**
     * Menambahkan seluruh Question satu Topic sekaligus (BR-E01).
     *
     * <p>Topic GLOBAL milik Eduscreen tidak perlu ditolak eksplisit: soal master ber-{@code
     * client_id} null, sehingga query yang menyaring {@code clientId} Guru mengembalikan kosong
     * dan pemanggilan itu berakhir 0 tanpa membocorkan apa pun (TC-36). Jalur satu-satunya ke
     * konten master tetap adopsi oleh Client Admin (FR-081).
     *
     * <p>Soal yang sudah terpasang dilewati, jadi menekan tombol dua kali tidak menggandakan
     * apa pun. Sengaja tidak memanggil {@link #addQuestion} per soal: itu akan menembakkan satu
     * {@code select} dan satu {@code count} untuk tiap soal, dan satu Topic bisa berisi ratusan.
     *
     * @return jumlah soal yang benar-benar baru ditambahkan
     */
    @Transactional
    public int addTopic(UUID exerciseId, UUID topicId, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        return append(exercise, questions.findByClientIdAndTopicIdOrderByCreatedAtAsc(clientId, topicId)
                .stream().map(QuestionEntity::getId).toList());
    }

    /**
     * Menempelkan soal ke ekor Exercise, melewati yang sudah terpasang. Posisi dihitung sekali
     * dari jumlah item sekarang lalu dinaikkan sendiri — bukan dibaca ulang per soal, karena
     * satu Topic bisa berisi ratusan.
     */
    private int append(ExerciseEntity exercise, List<UUID> questionIds) {
        Set<UUID> terpasang = new HashSet<>();
        for (ExerciseItemEntity item : items.findByExerciseIdOrderByPositionAsc(exercise.getId())) {
            terpasang.add(item.getQuestionId());
        }
        int position = terpasang.size();
        int ditambah = 0;
        for (UUID questionId : questionIds) {
            if (terpasang.add(questionId)) {
                items.save(new ExerciseItemEntity(exercise.getId(), questionId, position++));
                ditambah++;
            }
        }
        return ditambah;
    }

    @Transactional
    public void removeQuestion(UUID exerciseId, UUID questionId, UUID clientId) {
        requireUnlocked(exerciseId, clientId);
        items.findByExerciseIdAndQuestionId(exerciseId, questionId).ifPresent(items::delete);
        // Posisi dirapatkan ulang supaya tidak bolong; addQuestion menghitung posisi berikutnya
        // dari jumlah item sekarang, dan posisi berlubang akan membuatnya bentrok.
        renumber(exerciseId);
    }

    @Transactional
    public void reorder(UUID exerciseId, List<UUID> questionIds, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        List<ExerciseItemEntity> current = items.findByExerciseIdOrderByPositionAsc(exerciseId);
        Map<UUID, ExerciseItemEntity> byQuestionId = new HashMap<>();
        for (ExerciseItemEntity item : current) {
            byQuestionId.put(item.getQuestionId(), item);
        }
        int position = 0;
        for (UUID questionId : questionIds) {
            ExerciseItemEntity item = byQuestionId.get(questionId);
            if (item != null) {
                item.setPosition(position++);
            }
        }
    }

    /**
     * Duplikat menjadi satu-satunya cara mengubah Exercise yang sudah terkunci (BR-E04): title
     * ditandai " (salinan)" dan {@code lockedAt} kosong sehingga Exercise baru itu kembali bisa
     * diedit bebas.
     */
    @Transactional
    public ExerciseEntity duplicate(UUID exerciseId, UUID clientId, UUID createdBy) {
        ExerciseEntity source = require(exerciseId, clientId);
        ExerciseEntity copy = exercises.save(
                new ExerciseEntity(clientId, source.getTitle() + " (salinan)", createdBy));
        int position = 0;
        for (ExerciseItemEntity item : items.findByExerciseIdOrderByPositionAsc(exerciseId)) {
            items.save(new ExerciseItemEntity(copy.getId(), item.getQuestionId(), position++));
        }
        return copy;
    }

    /**
     * Exercise ber-{@code lockedAt} terisi sudah dipakai Assignment yang berjalan; mengubah
     * isinya diam-diam akan mengubah ujian yang sedang dikerjakan Siswa. Karena itu setiap
     * perubahan isi ditolak dengan 409 yang menawarkan duplikasi (BR-E04, FR-026).
     */
    private ExerciseEntity requireUnlocked(UUID exerciseId, UUID clientId) {
        ExerciseEntity exercise = require(exerciseId, clientId);
        if (exercise.isLocked()) {
            throw new IllegalStateException(
                    "Exercise sudah terkunci karena sudah diterbitkan; duplikasikan untuk mengubahnya");
        }
        return exercise;
    }

    private void renumber(UUID exerciseId) {
        int position = 0;
        for (ExerciseItemEntity item : items.findByExerciseIdOrderByPositionAsc(exerciseId)) {
            item.setPosition(position++);
        }
    }
}
