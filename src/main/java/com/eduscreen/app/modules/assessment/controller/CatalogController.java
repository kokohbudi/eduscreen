package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.service.ContentAdoptionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Katalog Paket master dan adopsinya (Task 11, ADR-0018).
 *
 * <p>Adopsi membuat <b>salinan penuh</b> milik Client (ADR-0001). Perubahan Eduscreen atas Paket
 * master setelahnya tidak merambat: sekolah yang sudah menyesuaikan isinya tidak boleh mendapati
 * Paketnya berubah di tengah semester. Satuan katalog dan adopsi adalah Paket, bukan lagi
 * Question atau Exercise satu per satu — lihat {@code ContentAdoptionService}.
 */
@Controller
public class CatalogController {

    private final ContentAdoptionService adoption;
    private final TaxonomyService taxonomy;
    private final PaketRepository pakets;

    public CatalogController(ContentAdoptionService adoption,
                             TaxonomyService taxonomy,
                             PaketRepository pakets) {
        this.adoption = adoption;
        this.taxonomy = taxonomy;
        this.pakets = pakets;
    }

    /**
     * Katalog per Subject: sebelum Subject dipilih tidak ada Paket yang perlu dimuat sama sekali
     * — daftar Subject terlihat tanpa harus menembak satu Subject bawaan mana pun.
     */
    @GetMapping("/katalog")
    public String catalog(@RequestParam(required = false) UUID subjectId,
                          @AuthenticationPrincipal UserPrincipal admin,
                          Model model) {
        UUID clientId = admin.requireClientId();
        model.addAttribute("subjects", taxonomy.visibleSubjects(clientId));
        // Hanya Paket master yang TERBIT boleh muncul di sini: yang masih digarap Eduscreen
        // tidak pernah terlihat Client (FR-067).
        List<PaketEntity> katalog = subjectId == null
                ? List.of()
                : pakets.findMasterPublished(subjectId);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("paket", katalog);
        // Penanda "sudah diadopsi" ditanyakan hanya untuk Paket yang tampil di halaman ini, bukan
        // seluruh katalog, supaya biayanya tidak tumbuh bersama besarnya katalog (FR-076, SC-015).
        model.addAttribute("sudahDiadopsi", adoption.adoptedSourcePaketIds(
                clientId, katalog.stream().map(PaketEntity::getId).toList()));
        return "katalog/index";
    }

    /**
     * Adopsi satu atau beberapa Paket sekaligus. Peringatan "sudah pernah diadopsi" di layar
     * katalog tidak menghalangi ini — adopsi kedua tetap melahirkan salinan kedua yang terpisah
     * (FR-076, FR-077).
     */
    @PostMapping("/katalog/adopsi")
    public String adopt(@RequestParam List<UUID> paketIds,
                        @AuthenticationPrincipal UserPrincipal admin,
                        Model model) {
        model.addAttribute("ringkasan",
                adoption.adoptPakets(admin.requireClientId(), paketIds, admin.userId()));
        return "katalog/index :: ringkasan";
    }
}
