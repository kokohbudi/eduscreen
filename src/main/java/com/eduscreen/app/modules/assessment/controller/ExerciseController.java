package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daftar dan perakit Exercise: kumpulan Question yang Guru susun, netral terhadap mode
 * Practice/Assignment nantinya. Jalur dan bentuk keluaran mengikuti
 * {@code contracts/content-authoring.md} persis.
 *
 * <p>{@link ExerciseService} yang menegakkan kunci (BR-E04, FR-026): setiap perubahan pada
 * Exercise ber-{@code lockedAt} dijawab {@code 409} lewat {@link IllegalStateException} yang
 * sudah ditangani {@code GlobalExceptionAdvice} (TC-31), sehingga controller ini tidak perlu
 * memeriksa status kunci sendiri.
 */
@Controller
public class ExerciseController {

    private static final int UKURAN_HALAMAN = 20;
    private static final int UKURAN_HALAMAN_BANK_SOAL = 10;

    private final ExerciseService exercises;
    private final QuestionService questions;
    private final PaketAccessService access;

    public ExerciseController(ExerciseService exercises, QuestionService questions, PaketAccessService access) {
        this.exercises = exercises;
        this.questions = questions;
        this.access = access;
    }

    @GetMapping("/exercise")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserPrincipal user,
                       Model model) {
        model.addAttribute("daftar", exercises.list(user.requireClientId(), q, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("q", q);
        return "exercise/daftar";
    }

    @PostMapping("/exercise")
    public String create(@RequestParam String title, @AuthenticationPrincipal UserPrincipal user) {
        ExerciseEntity exercise = exercises.create(user.requireClientId(), title, user.userId());
        return "redirect:/exercise/" + exercise.getId();
    }

    @GetMapping("/exercise/{id}")
    public String builder(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user, Model model) {
        UUID clientId = user.requireClientId();
        model.addAttribute("exercise", exercises.require(id, clientId));
        muatItem(id, clientId, model);

        // Penelusuran bank soal awal saat perakit dibuka: belum difilter Paket/Topic mana pun.
        // Pencarian berikutnya lewat HTMX memakai GET /exercise/{id}/cari (lihat cari() di bawah
        // dan exercise/builder.html) — panel ini menyaring per Paket, bukan Subject (ADR-0018).
        model.addAttribute("hasil", questions.searchForBuilder(
                clientId, null, null, null, null, List.of(), null,
                PageRequest.of(0, UKURAN_HALAMAN_BANK_SOAL)));
        model.addAttribute("paketId", null);
        model.addAttribute("topicId", null);
        model.addAttribute("q", null);
        model.addAttribute("type", null);
        model.addAttribute("sembunyikanTerpasang", false);
        model.addAttribute("exerciseId", id);
        // Paket milik sekolah ∪ Paket master yang aksesnya dimiliki (ADR-0021): perakit menelusuri
        // keduanya lewat panel yang sama.
        model.addAttribute("pakets", access.readablePakets(clientId));
        return "exercise/builder";
    }

    /**
     * Panel penelusuran bank soal di dalam perakit: menyaring per Paket dan Topic (ADR-0018),
     * bukan lagi Subject/Topic. Rute sendiri di bawah {@code /exercise/{id}}, bukan berbagi rute
     * dengan {@link com.eduscreen.app.modules.assessment.controller.BankSoalController} —
     * {@code exerciseId} datang dari path, bukan parameter opsional yang gampang lupa dikirim,
     * dan bentuk fragmen hasilnya bebas berbeda dari daftar bank soal biasa.
     */
    @GetMapping("/exercise/{id}/cari")
    public String cari(@PathVariable UUID id,
                       @RequestParam(required = false) UUID paketId,
                       @RequestParam(required = false) UUID topicId,
                       @RequestParam(required = false) QuestionType type,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean sembunyikanTerpasang,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserPrincipal user,
                       Model model) {
        UUID clientId = user.requireClientId();
        // require dulu (TC-36, TC-09): Exercise milik Client lain dijawab 404 sebelum panelnya
        // sempat memuat satu baris pun, bukan diam-diam menampilkan bank soal Client ini di
        // dalam perakit orang lain.
        exercises.require(id, clientId);
        List<UUID> terpasang = sembunyikanTerpasang
                ? exercises.itemsOf(id).stream().map(ExerciseItemEntity::getQuestionId).toList()
                : List.of();
        model.addAttribute("hasil", questions.searchForBuilder(
                clientId, null, paketId, topicId, type, terpasang, q,
                PageRequest.of(page, UKURAN_HALAMAN_BANK_SOAL)));
        model.addAttribute("paketId", paketId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("sembunyikanTerpasang", sembunyikanTerpasang);
        model.addAttribute("exerciseId", id);
        return "exercise/builder :: hasil";
    }

    @PostMapping("/exercise/{id}/item")
    public String addItem(@PathVariable UUID id,
                          @RequestParam UUID questionId,
                          @AuthenticationPrincipal UserPrincipal user,
                          Model model) {
        UUID clientId = user.requireClientId();
        exercises.addQuestion(id, questionId, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    /** Menambahkan soal-soal yang dicentang Guru di panel penelusuran, dalam satu tindakan. */
    @PostMapping("/exercise/{id}/item/terpilih")
    public String addSelected(@PathVariable UUID id,
                              @RequestParam(required = false) List<UUID> questionIds,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) {
        UUID clientId = user.requireClientId();
        exercises.addQuestions(id, questionIds, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    /**
     * Menambahkan satu Topic penuh. Jalur terpisah dari {@code /item} karena masukannya berbeda
     * jenis — {@code topicId}, bukan {@code questionId} — dan menyatukan keduanya di satu rute
     * hanya akan menghasilkan dua parameter yang saling meniadakan.
     */
    @PostMapping("/exercise/{id}/item/topik")
    public String addTopic(@PathVariable UUID id,
                           @RequestParam UUID topicId,
                           @AuthenticationPrincipal UserPrincipal user,
                           Model model) {
        UUID clientId = user.requireClientId();
        exercises.addTopic(id, topicId, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    @DeleteMapping("/exercise/{id}/item/{questionId}")
    public String removeItem(@PathVariable UUID id,
                             @PathVariable UUID questionId,
                             @AuthenticationPrincipal UserPrincipal user,
                             Model model) {
        UUID clientId = user.requireClientId();
        exercises.removeQuestion(id, questionId, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    /** Lepas beberapa item sekaligus dari centangan (AC-B26); balasannya fragmen daftar yang sama. */
    @PostMapping("/exercise/{id}/item/hapus-terpilih")
    public String removeItems(@PathVariable UUID id,
                              @RequestParam(required = false) List<UUID> itemIds,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) {
        UUID clientId = user.requireClientId();
        for (UUID questionId : itemIds == null ? List.<UUID>of() : itemIds) {
            exercises.removeQuestion(id, questionId, clientId);
        }
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    /**
     * Menambahkan seluruh isi satu Paket sekaligus — Paket milik sekolah maupun Paket master
     * lewat aksesnya (skenario "ambil Paket bulat-bulat", ADR-0021). Balasannya daftar item,
     * sama seperti tambah per Topic.
     */
    @PostMapping("/exercise/{id}/item/paket")
    public String addPaket(@PathVariable UUID id,
                           @RequestParam UUID paketId,
                           @AuthenticationPrincipal UserPrincipal user,
                           Model model) {
        UUID clientId = user.requireClientId();
        exercises.addPaket(id, paketId, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    @PutMapping("/exercise/{id}/urutan")
    public String reorder(@PathVariable UUID id,
                          @RequestParam List<UUID> questionIds,
                          @AuthenticationPrincipal UserPrincipal user,
                          Model model) {
        UUID clientId = user.requireClientId();
        exercises.reorder(id, questionIds, clientId);
        muatItem(id, clientId, model);
        return "exercise/builder :: item";
    }

    @PostMapping("/exercise/{id}/duplikat")
    public String duplicate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
        ExerciseEntity salinan = exercises.duplicate(id, user.requireClientId(), user.userId());
        return "redirect:/exercise/" + salinan.getId();
    }

    /**
     * Batang soal dimuat satu per satu per item — jumlah item satu Exercise kecil (puluhan, bukan
     * ribuan), jadi N+1 di sini tidak sepadan menambah satu method repository baru hanya untuk
     * perakit. Dibaca lewat {@code findAllForSnapshot}-nya perakit: {@code requireReadable} —
     * soal master yang aksesnya sudah lewat tetap tampil di Exercise yang sudah memuatnya
     * (FR-068), jadi yang gagal dibaca dilewati, bukan menggagalkan seluruh halaman.
     */
    private void muatItem(UUID exerciseId, UUID clientId, Model model) {
        List<ExerciseItemEntity> item = exercises.itemsOf(exerciseId);
        Map<UUID, QuestionEntity> soal = new LinkedHashMap<>();
        for (QuestionEntity q : questions.snapshotOf(item.stream().map(ExerciseItemEntity::getQuestionId).toList())) {
            soal.put(q.getId(), q);
        }
        model.addAttribute("exercise", exercises.require(exerciseId, clientId));
        model.addAttribute("item", item);
        model.addAttribute("soal", soal);
    }
}
