package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketBorrowService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ruang kerja kembar Bank Soal untuk konten master Eduscreen (ADR-0018).
 *
 * <p>Rute kembar {@code /eduscreen/bank-soal} dari {@link BankSoalController}: templat
 * {@code bank/*} yang sama, {@code basePath} berbeda. Yang membedakannya dari Bank Soal Client
 * hanya kepemilikan — {@code clientId} selalu {@code null} di sini, itulah penanda konten
 * Eduscreen (FR-060) — dan dua kemampuan tambahan yang tidak dimiliki Client: menerbitkan/menarik
 * Paket ke katalog (FR-066 sampai FR-068), dan menerbitkan/menarik Question satu per satu, yang
 * menjadi gerbang AC-B12 sebelum Paket-nya sendiri bisa terbit.
 *
 * <p>Sampai Task 9, rute {@code /eduscreen/paket*} di sini adalah perakit <b>Exercise master</b>
 * (bukan Paket) peninggalan sebelum ADR-0018 — Exercise ber-{@code clientId} null yang tidak
 * pernah dijual, diadopsi, atau ditugaskan sejak katalog dan onboarding pindah ke Paket (Task 8).
 * Task 10 mencabutnya dan mengambil alih rutenya untuk Paket yang sungguhan. {@code Exercise}
 * sendiri TIDAK dicabut — ia tetap milik alur Guru lewat {@code ExerciseController}.
 *
 * <p>Rute berada di bawah {@code /eduscreen/**} yang sudah dipagari
 * {@code hasRole("EDUSCREEN_ADMIN")} di {@code SecurityConfig}.
 */
@Controller
public class MasterContentController {

    /** Konten master tidak dimiliki Client mana pun; null-lah yang menyatakannya. */
    private static final UUID MASTER = null;

    private static final String BASE_PATH = "/eduscreen/bank-soal";

    private final QuestionService questions;
    private final QuestionRepository questionRepository;
    private final TaxonomyService taxonomy;
    private final PaketService pakets;
    private final PaketRepository paketRepository;
    private final PaketBorrowService borrow;
    private final MasterPublishingService publishing;

    public MasterContentController(QuestionService questions,
                                   QuestionRepository questionRepository,
                                   TaxonomyService taxonomy,
                                   PaketService pakets,
                                   PaketRepository paketRepository,
                                   PaketBorrowService borrow,
                                   MasterPublishingService publishing) {
        this.questions = questions;
        this.questionRepository = questionRepository;
        this.taxonomy = taxonomy;
        this.pakets = pakets;
        this.paketRepository = paketRepository;
        this.borrow = borrow;
        this.publishing = publishing;
    }

    /** Tingkat 1: daftar Subject GLOBAL dengan jumlah Paket master. Tingkat 2 bila {@code subjectId} terisi. */
    @GetMapping("/eduscreen/bank-soal")
    public String index(@RequestParam(required = false) UUID subjectId, Model model) {
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        isiJalur(model);
        if (subjectId == null) {
            Map<UUID, Long> jumlahPaket = new HashMap<>();
            paketRepository.countMasterBySubject()
                    .forEach(c -> jumlahPaket.put(c.getSubjectId(), c.getJumlah()));
            model.addAttribute("jumlahPaket", jumlahPaket);
            return "bank/subject";
        }
        model.addAttribute("subject", taxonomy.requireGlobalSubject(subjectId));
        model.addAttribute("pakets", paketRepository.findMaster(subjectId));
        model.addAttribute("jumlahSoal", jumlahSoalMaster());
        return "bank/paket";
    }

    /** Subject GLOBAL sudah ada dipakai ulang, belum ada dibuat — satu kolom nama (AC-B06 setara). */
    @PostMapping("/eduscreen/bank-soal/paket")
    public String createPaket(@RequestParam String title,
                              @RequestParam(required = false) UUID subjectId,
                              @RequestParam(required = false) String subjectName,
                              @AuthenticationPrincipal UserPrincipal user) {
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft(title, subjectId, subjectName), MASTER, user.userId());
        return "redirect:" + BASE_PATH + "/paket/" + paket.getId();
    }

    /** Tingkat 3: isi Paket master, soal dikelompokkan per Topic. */
    @GetMapping("/eduscreen/bank-soal/paket/{id}")
    public String isiPaket(@PathVariable UUID id, Model model) {
        PaketEntity paket = pakets.require(id, MASTER);
        model.addAttribute("paket", paket);
        model.addAttribute("topics", pakets.topicsOf(id));
        model.addAttribute("soalPerTopic", questions.groupByTopic(id));
        isiJalur(model);
        return "bank/isi";
    }

    @PostMapping("/eduscreen/bank-soal/paket/{id}/topic")
    public String addTopic(@PathVariable UUID id, @RequestParam String title) {
        pakets.addTopic(id, title, MASTER);
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    @GetMapping("/eduscreen/bank-soal/paket/{id}/soal/baru")
    public String soalBaru(@PathVariable UUID id, @RequestParam UUID topicId, Model model) {
        PaketEntity paket = pakets.require(id, MASTER);
        isiEditor(paket, null, topicId, model);
        return "soal/editor";
    }

    /**
     * Menyimpan soal baru ke Paket master di jalur — bukan Paket yang disimpulkan dari Topic-nya.
     * {@link QuestionService#create} yang menolak Topic dari Paket lain (AC-B02 setara).
     */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/soal")
    public String simpanSoal(@PathVariable UUID id,
                             @RequestParam UUID topicId,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @RequestParam(required = false) String lanjut) {
        questions.create(
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml, optionBody, correctIndex),
                MASTER, id);
        return lanjut != null
                ? "redirect:" + BASE_PATH + "/paket/" + id + "/soal/baru?topicId=" + topicId
                : "redirect:" + BASE_PATH + "/paket/" + id;
    }

    @GetMapping("/eduscreen/bank-soal/soal/{id}")
    public String ubahSoal(@PathVariable UUID id, Model model) {
        QuestionEntity soal = questions.require(id, MASTER);
        // require pada Paket induk: soal di bawah Paket yang sudah dihapus lunak ikut 404.
        PaketEntity paket = pakets.require(soal.getPaketId(), MASTER);
        isiEditor(paket, soal, soal.getTopicId(), model);
        return "soal/editor";
    }

    /** Perubahan dibalas fragmen detail di tempat, bukan halaman penuh (TC-14). */
    @PutMapping("/eduscreen/bank-soal/soal/{id}")
    public String updateSoal(@PathVariable UUID id,
                             @RequestParam UUID topicId,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             Model model) {
        PaketEntity paket = pakets.require(questions.require(id, MASTER).getPaketId(), MASTER);
        QuestionEntity soal = questions.update(id,
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml, optionBody, correctIndex),
                MASTER, paket.getId());
        isiEditor(paket, soal, soal.getTopicId(), model);
        return "soal/editor :: detail";
    }

    /** Panel pinjam antar-Paket master, sama alasan dengan {@link BankSoalController#panelPinjam}. */
    @GetMapping("/eduscreen/bank-soal/paket/{id}/pinjam")
    public String panelPinjam(@PathVariable UUID id,
                              @RequestParam(required = false) UUID sourcePaketId,
                              Model model) {
        PaketEntity target = pakets.require(id, MASTER);
        model.addAttribute("paket", target);
        model.addAttribute("topics", pakets.topicsOf(id));
        model.addAttribute("sudahDipinjam", borrow.borrowedSourceIds(id));
        model.addAttribute("paketLain", paketRepository.findMaster(target.getSubjectId())
                .stream().filter(p -> !p.getId().equals(id)).toList());
        if (sourcePaketId != null) {
            PaketEntity sumber = pakets.require(sourcePaketId, MASTER);
            model.addAttribute("sumber", sumber);
            model.addAttribute("topicSumber", pakets.topicsOf(sumber.getId()));
            model.addAttribute("soalSumber", questions.groupByTopic(sumber.getId()));
        }
        return "bank/isi :: panelPinjam";
    }

    @PostMapping("/eduscreen/bank-soal/paket/{id}/pinjam")
    public String pinjam(@PathVariable UUID id,
                         @RequestParam UUID topicId,
                         @RequestParam(required = false) List<UUID> questionIds,
                         @RequestParam(required = false) UUID sourceTopicId,
                         @AuthenticationPrincipal UserPrincipal user) {
        if (sourceTopicId != null) {
            borrow.borrowTopic(id, topicId, sourceTopicId, MASTER, user.userId());
        } else {
            borrow.borrowQuestions(id, topicId, questionIds, MASTER, user.userId());
        }
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    // ----------------------------------------------------------- penerbitan

    /** Terbit membuat Paket terlihat di katalog seluruh Client (FR-066). */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/terbit")
    public String terbitPaket(@PathVariable UUID id, Model model) {
        return barisPaket(publishing.publishPaket(id), model);
    }

    /** Menarik tidak menyentuh satu pun salinan yang sudah diadopsi Client (FR-068, ADR-0001). */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/tarik")
    public String tarikPaket(@PathVariable UUID id, Model model) {
        return barisPaket(publishing.withdrawPaket(id), model);
    }

    /**
     * Terbit Question membuka gerbang AC-B12: Paket induknya baru bisa terbit begitu seluruh
     * isinya sudah dalam keadaan ini.
     */
    @PostMapping("/eduscreen/bank-soal/soal/{id}/terbit")
    public String terbitSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.publishQuestion(id), model);
    }

    @PostMapping("/eduscreen/bank-soal/soal/{id}/tarik")
    public String tarikSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.unpublishQuestion(id), model);
    }

    // ------------------------------------------------------------- pembantu

    /**
     * Atribut bersama editor {@code soal/editor}, sejajar
     * {@link BankSoalController#isiEditor(PaketEntity, QuestionEntity, UUID, Model)}.
     */
    private void isiEditor(PaketEntity paket, QuestionEntity soal, UUID topicId, Model model) {
        model.addAttribute("paket", paket);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", soal == null ? List.of() : questions.optionsOf(soal.getId()));
        model.addAttribute("topicId", topicId);
        model.addAttribute("topics", pakets.topicsOf(paket.getId()));
        model.addAttribute("basePath", BASE_PATH);
    }

    /** Satu baris Paket setelah keadaan terbitnya berubah; HTMX menukar baris itu saja (TC-14). */
    private String barisPaket(PaketEntity paket, Model model) {
        model.addAttribute("paket", paket);
        model.addAttribute("jumlahSoal", jumlahSoalMaster());
        isiJalur(model);
        return "bank/paket :: barisPaket";
    }

    /** Satu baris Question setelah keadaan terbitnya berubah; sama alasan dengan {@link #barisPaket}. */
    private String barisSoal(QuestionEntity soal, Model model) {
        model.addAttribute("q", soal);
        isiJalur(model);
        return "bank/isi :: barisSoal";
    }

    private Map<UUID, Long> jumlahSoalMaster() {
        Map<UUID, Long> jumlahSoal = new HashMap<>();
        questionRepository.countMasterByPaket().forEach(c -> jumlahSoal.put(c.getPaketId(), c.getJumlah()));
        return jumlahSoal;
    }

    /** Jalur dan penanda yang membedakan ruang kerja master dari Bank Soal Client di templat bersama. */
    private void isiJalur(Model model) {
        model.addAttribute("basePath", BASE_PATH);
        model.addAttribute("master", true);
        model.addAttribute("menuAktif", "bank-soal");
    }
}
