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
import java.util.Set;
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
     * Adopsi satu atau beberapa Paket sekaligus (AC-B14, FR-077).
     *
     * <p>Tidak mencentang apa pun dan menekan tombol MUST tidak diam-diam gagal (TC-13: HTMX
     * hanya menukar fragmen pada respons 2xx) — {@code paketIds} karena itu opsional, dibalas
     * ringkasan nol.
     *
     * <p>Kalau ada {@code paketId} yang diminta dan salinannya sudah ada di Client ini,
     * permintaan BERHENTI sebelum menyalin dan membalas fragmen peringatan yang menyebut Paket
     * mana yang sudah pernah diadopsi — bukan lencana pasif di sebelah checkbox (itu penanda,
     * AC-B11), melainkan jeda yang benar-benar mendahului tindakan. Ditegakkan di server lewat
     * penanda {@code confirm}, sengaja tanpa {@code confirm()} JavaScript: permintaan ulang yang
     * sama disertai {@code confirm=true} tetap menyalin, melahirkan salinan kedua yang terpisah.
     */
    @PostMapping("/katalog/adopsi")
    public String adopt(@RequestParam(required = false) List<UUID> paketIds,
                        @RequestParam(defaultValue = "false") boolean confirm,
                        @AuthenticationPrincipal UserPrincipal admin,
                        Model model) {
        UUID clientId = admin.requireClientId();
        if (paketIds == null || paketIds.isEmpty()) {
            model.addAttribute("ringkasan", new ContentAdoptionService.AdoptionSummary(0, 0, 0));
            return "katalog/index :: ringkasan";
        }

        if (!confirm) {
            Set<UUID> sudahDiadopsi = adoption.adoptedSourcePaketIds(clientId, paketIds);
            if (!sudahDiadopsi.isEmpty()) {
                model.addAttribute("paketIds", paketIds);
                model.addAttribute("paketSudahDiadopsi", pakets.findAllById(sudahDiadopsi));
                return "katalog/index :: peringatanAdopsiUlang";
            }
        }

        model.addAttribute("ringkasan", adoption.adoptPakets(clientId, paketIds, admin.userId()));
        return "katalog/index :: ringkasan";
    }
}
