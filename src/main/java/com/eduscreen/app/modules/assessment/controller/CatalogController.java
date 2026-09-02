package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.service.ContentAdoptionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Katalog konten master dan adopsinya.
 *
 * <p>Adopsi membuat <b>salinan penuh</b> milik Client (ADR-0001). Perubahan Eduscreen atas soal
 * master setelahnya tidak merambat: sekolah yang sudah menyesuaikan sebuah soal tidak boleh
 * mendapati soalnya berubah di tengah semester.
 */
@Controller
public class CatalogController {

    private static final int UKURAN_HALAMAN = 20;

    private final ContentAdoptionService adoption;
    private final TaxonomyService taxonomy;
    private final ExerciseService exercises;
    private final QuestionService questions;

    public CatalogController(ContentAdoptionService adoption,
                             TaxonomyService taxonomy,
                             ExerciseService exercises,
                             QuestionService questions) {
        this.adoption = adoption;
        this.taxonomy = taxonomy;
        this.exercises = exercises;
        this.questions = questions;
    }

    @GetMapping("/katalog")
    public String catalog(@RequestParam(required = false) UUID subjectId,
                          @RequestParam(required = false) UUID topicId,
                          @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page,
                          @AuthenticationPrincipal UserPrincipal admin,
                          Model model) {
        UUID clientId = admin.requireClientId();
        model.addAttribute("subjects", taxonomy.visibleSubjects(clientId));
        model.addAttribute("topics", subjectId != null ? taxonomy.visibleTopics(subjectId, clientId) : List.of());
        // Paket master adalah Exercise ber-clientId null, dan hanya yang TERBIT boleh muncul
        // di sini: konten yang masih digarap Eduscreen tidak pernah terlihat Client (FR-067).
        model.addAttribute("paket",
                exercises.listPublishedMaster(null, PageRequest.of(0, 100)).getContent());
        isiHasilSoal(clientId, subjectId, topicId, q, page, model);
        return "katalog/index";
    }

    /** Fragmen hasil untuk penelusuran HTMX; bentuknya identik dengan yang ada di halaman penuh. */
    @GetMapping("/katalog/soal")
    public String catalogQuestions(@RequestParam(required = false) UUID subjectId,
                                   @RequestParam(required = false) UUID topicId,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "0") int page,
                                   @AuthenticationPrincipal UserPrincipal admin,
                                   Model model) {
        isiHasilSoal(admin.requireClientId(), subjectId, topicId, q, page, model);
        return "katalog/index :: hasilSoal";
    }

    /**
     * Satu halaman Question master terbit, beserta himpunan yang sudah pernah diadopsi Client
     * ini (FR-074, FR-076).
     *
     * <p>Penanda adopsi ditanyakan hanya untuk pengenal yang tampil di halaman ini, bukan untuk
     * seluruh katalog — biayanya karena itu tidak tumbuh bersama besarnya katalog (SC-015).
     */
    private void isiHasilSoal(UUID clientId, UUID subjectId, UUID topicId, String q, int page, Model model) {
        Page<QuestionEntity> hasil = questions.searchPublishedMaster(
                subjectId, topicId, q, PageRequest.of(page, UKURAN_HALAMAN));
        model.addAttribute("hasil", hasil);
        model.addAttribute("sudahDiadopsi", adoption.adoptedSourceIds(clientId,
                hasil.getContent().stream().map(QuestionEntity::getId).toList()));
        // Peringatan setingkat Topic, sejajar penanda per Question (FR-076, FR-077). Muncul saat
        // Client Admin menyaring ke sebuah Topic yang sudah pernah ia ambil; adopsi keduanya
        // tetap boleh dilanjutkan, karena itu ini peringatan, bukan gerbang.
        model.addAttribute("topicSudahDiadopsi", adoption.hasAdoptedTopic(clientId, topicId));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
    }

    @PostMapping("/katalog/adopsi")
    public String adopt(@RequestParam(required = false) List<UUID> questionIds,
                        @RequestParam(required = false) List<UUID> exerciseIds,
                        @AuthenticationPrincipal UserPrincipal admin,
                        Model model) {
        UUID clientId = admin.requireClientId();
        ContentAdoptionService.AdoptionSummary summary = exerciseIds != null && !exerciseIds.isEmpty()
                ? adoption.adoptExercises(clientId, exerciseIds, admin.userId())
                : adoption.adoptQuestions(clientId,
                        questionIds == null ? List.of() : questionIds, admin.userId());
        model.addAttribute("ringkasan", summary);
        return "katalog/index :: ringkasan";
    }
}
