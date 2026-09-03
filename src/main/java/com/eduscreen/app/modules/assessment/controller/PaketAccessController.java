package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.repository.PaketAccessEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.service.ClientDirectoryService;
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Akses Paket master per sekolah (ADR-0021), ruang kerja Eduscreen Admin.
 *
 * <p>Menggantikan katalog self-service: sekolah tidak bisa mengambil Paket yang tidak diberikan
 * (Pasal 3). Rute di bawah {@code /eduscreen/**} yang sudah dipagari {@code EDUSCREEN_ADMIN}.
 */
@Controller
public class PaketAccessController {

    private static final String BASE = "/eduscreen/akses";

    private final PaketAccessService access;
    private final ClientDirectoryService clients;
    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final ClientClock clock;

    public PaketAccessController(PaketAccessService access, ClientDirectoryService clients,
                                 PaketRepository pakets, PaketVersionRepository versions, ClientClock clock) {
        this.access = access;
        this.clients = clients;
        this.pakets = pakets;
        this.versions = versions;
        this.clock = clock;
    }

    /** Daftar sekolah; memilih satu memuat akses aktifnya beserta formulir memberi Paket. */
    @GetMapping("/eduscreen/akses")
    public String index(@RequestParam(required = false) UUID clientId, Model model) {
        model.addAttribute("clients", clients.all());
        model.addAttribute("clientId", clientId);
        if (clientId != null) {
            model.addAttribute("client", clients.require(clientId));
            List<PaketAccessEntity> aktif = access.activeFor(clientId);
            model.addAttribute("akses", aktif);
            Map<UUID, PaketEntity> paketById = new HashMap<>();
            Map<UUID, PaketVersionEntity> versiById = new HashMap<>();
            Map<UUID, List<PaketVersionEntity>> versiTerbit = new HashMap<>();
            for (PaketAccessEntity a : aktif) {
                pakets.findById(a.getPaketId()).ifPresent(p -> paketById.put(p.getId(), p));
                versions.findById(a.getVersionId()).ifPresent(v -> versiById.put(v.getId(), v));
                versiTerbit.put(a.getPaketId(), versions.findByPaketIdOrderByNomorDesc(a.getPaketId())
                        .stream().filter(v -> !v.isDraft()).toList());
            }
            model.addAttribute("paketById", paketById);
            model.addAttribute("versiById", versiById);
            model.addAttribute("versiTerbit", versiTerbit);
            model.addAttribute("sekarang", clock.now());
            // Hanya Paket master terbit yang bisa diberikan (FR-067).
            model.addAttribute("paketTerbit", pakets.findAllMasterPublished());
        }
        return "eduscreen/akses";
    }

    /** Memberi (atau memperpanjang) akses; tanggal berakhir opsional, ditafsirkan akhir hari zona sekolah. */
    @PostMapping("/eduscreen/akses")
    public String grant(@RequestParam UUID clientId,
                        @RequestParam UUID paketId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
                        @AuthenticationPrincipal UserPrincipal admin) {
        access.grant(clientId, paketId, akhirHari(validUntil, clients.zoneOf(clientId)), admin.userId());
        return "redirect:" + BASE + "?clientId=" + clientId;
    }

    @PostMapping("/eduscreen/akses/{id}/cabut")
    public String revoke(@PathVariable UUID id, @RequestParam UUID clientId) {
        access.revoke(id);
        return "redirect:" + BASE + "?clientId=" + clientId;
    }

    /** Eduscreen Admin memindahkan sekolah ke versi terbit lain; sekolah punya tombolnya sendiri di Bank Soal. */
    @PostMapping("/eduscreen/akses/{id}/versi")
    public String switchVersion(@PathVariable UUID id, @RequestParam UUID versionId, @RequestParam UUID clientId) {
        access.switchVersion(id, versionId, null);
        return "redirect:" + BASE + "?clientId=" + clientId;
    }

    private static OffsetDateTime akhirHari(LocalDate tanggal, ZoneId zona) {
        return tanggal == null ? null : tanggal.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
    }
}
