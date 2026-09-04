package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
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

    /** Judul Exercise yang baru lahir dari perakit, sebelum Guru mengetik judulnya sendiri (BR-E06). */
    public static final String JUDUL_BAWAAN = "Latihan tanpa judul";

    private final ExerciseRepository exercises;
    private final ExerciseItemRepository items;
    private final QuestionRepository questions;
    private final PaketItemRepository paketItems;
    private final PaketAccessService access;
    private final ClientClock clock;

    public ExerciseService(ExerciseRepository exercises,
                           ExerciseItemRepository items,
                           QuestionRepository questions,
                           PaketItemRepository paketItems,
                           PaketAccessService access,
                           ClientClock clock) {
        this.exercises = exercises;
        this.items = items;
        this.questions = questions;
        this.paketItems = paketItems;
        this.access = access;
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

    /**
     * Judul kosong diterima dan diganti {@link #JUDUL_BAWAAN} (BR-E06): perakit adalah satu layar
     * tempat Guru memberi judul, memilih soal referensi, dan menulis soal sendiri, jadi Exercise
     * harus sudah ada sebelum ia sempat mengetik apa pun.
     */
    @Transactional
    public ExerciseEntity create(UUID clientId, String title, UUID createdBy) {
        String judul = title == null || title.isBlank() ? JUDUL_BAWAAN : title.trim();
        return exercises.save(new ExerciseEntity(clientId, judul, createdBy));
    }

    /** Mengganti judul dari dalam perakit (BR-E06); Exercise terkunci menolaknya seperti perubahan lain (BR-E04). */
    @Transactional
    public ExerciseEntity rename(UUID exerciseId, String title, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Judul Exercise tidak boleh kosong");
        }
        exercise.setTitle(title.trim());
        return exercises.save(exercise);
    }

    @Transactional(readOnly = true)
    public List<ExerciseItemEntity> itemsOf(UUID exerciseId) {
        return items.findByExerciseIdOrderByPositionAsc(exerciseId);
    }

    @Transactional
    public void addQuestion(UUID exerciseId, UUID questionId, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        // Soal harus terlihat Client ini: miliknya sendiri, atau soal master lewat akses Paket
        // (ADR-0021); di luar itu, termasuk yang tidak ada, sama-sama 404 (TC-09, TC-36). Sengaja
        // TIDAK ada validasi Subject/Topic apa pun di sini: Exercise boleh memuat soal lintas
        // Subject dan Topic mana pun (BR-E01, AC-E02, FR-024).
        questions.findAccessibleById(questionId, clientId, access.visibleVersionIds(clientId))
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
        for (QuestionEntity soal : questions.findAccessibleByIdIn(clientId, access.visibleVersionIds(clientId), questionIds)) {
            sah.add(soal.getId());
        }
        return append(exercise, questionIds.stream().filter(sah::contains).toList());
    }

    /**
     * Menambahkan seluruh Question satu Topic sekaligus (BR-E01).
     *
     * <p>Topic yang tidak terlihat Client — milik Client lain, atau Paket master tanpa akses —
     * tidak perlu ditolak eksplisit: {@code visibleVersionOfTopic} kosong dan pemanggilan berakhir
     * 0 tanpa membocorkan apa pun (TC-36). Topic Paket master dibaca lewat versi yang ditunjuk
     * akses sekolah (ADR-0021), hanya soal terbitnya.
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
        List<UUID> ids = access.visibleVersionOfTopic(topicId, clientId)
                .map(v -> questions.findAccessibleInTopic(clientId, v.getId(), topicId))
                .orElse(List.of())
                .stream().map(QuestionEntity::getId).toList();
        return append(exercise, ids);
    }

    /**
     * Menambahkan seluruh isi satu Paket sekaligus, urut Topic dan posisi (skenario "Guru ambil
     * Paket bulat-bulat", ADR-0021): Paket miliknya sendiri lewat versi kerjanya, Paket master
     * lewat versi yang ditunjuk aksesnya — hanya soal terbit. Tidak ada satu pun baris soal
     * yang lahir: Exercise menunjuk soal master langsung. Paket yang tidak terlihat → 404.
     *
     * @return jumlah soal yang benar-benar baru ditambahkan
     */
    @Transactional
    public int addPaket(UUID exerciseId, UUID paketId, UUID clientId) {
        ExerciseEntity exercise = requireUnlocked(exerciseId, clientId);
        PaketVersionEntity versi = access.visibleVersionOf(paketId, clientId);
        return append(exercise, questions.findAccessibleInVersion(clientId, versi.getId())
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
        // Soal lepas (BR-E05) tidak punya rumah lain: begitu Exercise terakhir yang memuatnya
        // melepasnya, ia tidak akan pernah bisa ditemukan lagi lewat layar mana pun. Dihapus di
        // sini supaya tidak menumpuk sebagai baris yatim yang tak terlihat siapa pun.
        hapusSoalLepasYatim(questionId, clientId);
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

    /**
     * Menghapus lunak soal yang tidak punya penempatan Paket dan tidak lagi dipakai Exercise mana
     * pun (BR-E05). Soal berpenempatan — miliknya sendiri maupun master — tidak tersentuh: ia
     * masih hidup di Paketnya. Soal master juga lolos dengan sendirinya karena
     * {@code findByIdAndClientId} tidak akan mencocokkan {@code client_id} null.
     */
    private void hapusSoalLepasYatim(UUID questionId, UUID clientId) {
        if (!paketItems.findPlacements(List.of(questionId)).isEmpty()
                || items.existsByQuestionId(questionId)) {
            return;
        }
        questions.findByIdAndClientId(questionId, clientId).ifPresent(soal -> {
            soal.softDelete(clock.now());
            questions.save(soal);
        });
    }

    private void renumber(UUID exerciseId) {
        int position = 0;
        for (ExerciseItemEntity item : items.findByExerciseIdOrderByPositionAsc(exerciseId)) {
            item.setPosition(position++);
        }
    }
}
