package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.ImageService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Taksonomi (Subject/Topic), bank soal, dan unggah gambar untuk Client Admin dan Guru.
 *
 * <p>Jalur dan bentuk keluaran mengikuti {@code contracts/content-authoring.md} persis: setiap
 * pembacaan menyaring {@code clientId} lewat {@link QuestionService}/{@link TaxonomyService}
 * (TC-36), dan setiap endpoint HTMX membalas fragmen, bukan JSON (TC-14).
 */
@Controller
public class QuestionBankController {

    private static final int UKURAN_HALAMAN = 20;

    private final QuestionService questions;
    private final TaxonomyService taxonomy;
    private final PaketService pakets;
    private final ImageService images;
    private final ExerciseService exercises;

    public QuestionBankController(QuestionService questions, TaxonomyService taxonomy,
                                  PaketService pakets, ImageService images, ExerciseService exercises) {
        this.questions = questions;
        this.taxonomy = taxonomy;
        this.pakets = pakets;
        this.images = images;
        this.exercises = exercises;
    }

    @GetMapping("/subject")
    public String subjects(@AuthenticationPrincipal UserPrincipal user, Model model) {
        model.addAttribute("subjects", taxonomy.visibleSubjects(user.requireClientId()));
        return "soal/daftar :: subjects";
    }

    /** Memuat ulang ke Subject yang baru, sebab yang sama seperti jalur master. */
    @PostMapping("/admin/subject")
    public String createSubject(@RequestParam String name,
                                @AuthenticationPrincipal UserPrincipal user) {
        return "redirect:/soal?subjectId="
                + taxonomy.createClientSubject(user.requireClientId(), name).getId();
    }

    /**
     * Isi ulang daftar Topic saat Subject berganti, untuk formulir tulis maupun penyaring bank
     * soal. Keduanya hanya berurusan dengan konten milik Client ini; katalog konten master
     * punya jalurnya sendiri di {@code CatalogController} (ADR-0018).
     */
    @GetMapping("/subject/{id}/topic")
    public String topics(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user, Model model) {
        model.addAttribute("topics", taxonomy.topicsOwnedBy(id, user.requireClientId()));
        return "soal/daftar :: topics";
    }

    @PostMapping("/subject/{id}/topic")
    public String createTopic(@PathVariable UUID id,
                              @RequestParam String name,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) {
        model.addAttribute("topic", pakets.createClientTopic(id, user.requireClientId(), name));
        return "soal/daftar :: opsiTopicBaru";
    }

    /**
     * Sengaja MENERIMA {@code exerciseId} opsional di luar tabel kontrak: itu satu-satunya cara
     * fragmen {@code hasil} ini dipakai ulang tanpa perubahan bentuk oleh panel penelusuran bank
     * soal di perakit Exercise (lihat {@code exercise/builder.html}) — kontrak metode/jalur/
     * keluarannya sendiri tidak berubah.
     */
    @GetMapping("/soal")
    public String search(@RequestParam(required = false) UUID subjectId,
                         @RequestParam(required = false) UUID topicId,
                         @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) UUID exerciseId,
                         @RequestParam(required = false) QuestionType type,
                         @RequestParam(defaultValue = "false") boolean sembunyikanTerpasang,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         @AuthenticationPrincipal UserPrincipal user,
                         Model model) {
        UUID clientId = user.requireClientId();
        // Pengecualian hanya bermakna di dalam perakit; di daftar bank soal biasa tidak ada
        // Exercise yang sedang dirakit, jadi tidak ada yang perlu disembunyikan.
        List<UUID> terpasang = sembunyikanTerpasang && exerciseId != null
                ? exercises.itemsOf(exercises.require(exerciseId, clientId).getId()).stream()
                        .map(ExerciseItemEntity::getQuestionId).toList()
                : List.of();
        // paketId selalu null di jalur lama ini: penyaring Paket khusus panel perakit sekarang
        // hidup di GET /exercise/{id}/cari, bukan di sini (Task 12, ADR-0018).
        model.addAttribute("hasil", questions.searchForBuilder(
                clientId, null, topicId, type, terpasang, q, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
        model.addAttribute("exerciseId", exerciseId);
        model.addAttribute("type", type);
        model.addAttribute("sembunyikanTerpasang", sembunyikanTerpasang);
        if (hxRequest != null) {
            return "soal/daftar :: hasil";
        }
        model.addAttribute("subjects", taxonomy.visibleSubjects(clientId));
        model.addAttribute("topics", subjectId != null ? taxonomy.topicsOwnedBy(subjectId, clientId) : List.of());
        return "soal/daftar";
    }

    @GetMapping("/soal/baru")
    public String baru(@RequestParam(required = false) UUID topicId,
                       @AuthenticationPrincipal UserPrincipal user,
                       Model model) {
        UUID clientId = user.requireClientId();
        model.addAttribute("soal", null);
        model.addAttribute("opsi", List.<QuestionOptionEntity>of());
        model.addAttribute("topicId", topicId);
        isiTaksonomiEditor(topicId, clientId, model);
        return "soal/editor";
    }

    @PostMapping("/soal")
    public String create(@RequestParam UUID topicId,
                         @RequestParam QuestionType type,
                         @RequestParam String bodyHtml,
                         @RequestParam(required = false) String explanationHtml,
                         @RequestParam(required = false) List<String> optionBody,
                         @RequestParam(defaultValue = "-1") int correctIndex,
                         @AuthenticationPrincipal UserPrincipal user) {
        UUID clientId = user.requireClientId();
        // sementara sampai Task 9: formulir ini belum dibuka dari dalam satu Paket, jadi
        // paketId diturunkan dari Topic tujuan. Ruang kerja per-Paket yang mengirim paketId
        // eksplisit menggantikan jalur ini (ADR-0018).
        UUID paketId = taxonomy.requireWritableTopic(topicId, clientId).getPaketId();
        QuestionEntity soal = questions.create(
                new QuestionService.QuestionDraft(topicId, type, bodyHtml, explanationHtml,
                        buildOptions(type, optionBody, correctIndex)),
                clientId, paketId);
        return "redirect:/soal/" + soal.getId();
    }

    @GetMapping("/soal/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user, Model model) {
        UUID clientId = user.requireClientId();
        QuestionEntity soal = questions.require(id, clientId);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(id));
        model.addAttribute("topicId", soal.getTopicId());
        isiTaksonomiEditor(soal.getTopicId(), clientId, model);
        return "soal/editor";
    }

    @PutMapping("/soal/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam UUID topicId,
                         @RequestParam QuestionType type,
                         @RequestParam String bodyHtml,
                         @RequestParam(required = false) String explanationHtml,
                         @RequestParam(required = false) List<String> optionBody,
                         @RequestParam(defaultValue = "-1") int correctIndex,
                         @AuthenticationPrincipal UserPrincipal user,
                         Model model) {
        UUID clientId = user.requireClientId();
        // sementara sampai Task 9: lihat catatan yang sama di create().
        UUID paketId = taxonomy.requireWritableTopic(topicId, clientId).getPaketId();
        QuestionEntity soal = questions.update(id,
                new QuestionService.QuestionDraft(topicId, type, bodyHtml, explanationHtml,
                        buildOptions(type, optionBody, correctIndex)),
                clientId, paketId);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(id));
        model.addAttribute("topicId", soal.getTopicId());
        isiTaksonomiEditor(soal.getTopicId(), clientId, model);
        return "soal/editor :: detail";
    }

    @DeleteMapping("/soal/{id}")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user, Model model) {
        questions.softDelete(id, user.requireClientId());
        model.addAttribute("pesan", "Soal dihapus dari bank soal.");
        return "soal/daftar :: konfirmasiHapus";
    }

    /**
     * Isi tidak pernah dicatat log (TC-44) — hanya {@code imageId} yang lahir dari
     * {@link ImageService#store} yang aman ditaruh di model.
     */
    @PostMapping("/gambar")
    public String uploadImage(@RequestParam MultipartFile berkas,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) throws IOException {
        model.addAttribute("imageId", images.store(berkas.getBytes(), user).getId());
        return "soal/editor :: gambarTerunggah";
    }

    private List<QuestionService.OptionDraft> buildOptions(QuestionType type, List<String> optionBody, int correctIndex) {
        if (type == QuestionType.ESSAY || optionBody == null) {
            return List.of();
        }
        List<QuestionService.OptionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < optionBody.size(); i++) {
            drafts.add(new QuestionService.OptionDraft(optionBody.get(i), i == correctIndex));
        }
        return drafts;
    }

    /**
     * Subject dan daftar Topic untuk editor selalu ditentukan dari Topic yang sedang aktif
     * (bukan dipilih terpisah): Topic melekat pada tepat satu Subject (FR-015), jadi Subject
     * induknya cukup diturunkan, bukan dikirim ulang lewat form.
     */
    private void isiTaksonomiEditor(UUID topicId, UUID clientId, Model model) {
        model.addAttribute("subjects", taxonomy.visibleSubjects(clientId));
        if (topicId == null) {
            model.addAttribute("subjectId", null);
            model.addAttribute("topics", List.<TopicEntity>of());
            return;
        }
        TopicEntity topic = taxonomy.requireWritableTopic(topicId, clientId);
        // sementara sampai Task 6: Subject diturunkan dari Paket induk Topic, karena Topic
        // sendiri sudah tidak membawanya (ADR-0018).
        UUID subjectId = taxonomy.subjectIdOf(topic);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topics", taxonomy.topicsOwnedBy(subjectId, clientId));
    }
}
