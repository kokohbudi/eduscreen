package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
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
 * Bank Soal: Subject › Paket › isi Paket (ADR-0018).
 *
 * <p>Tiga tingkat, tiga tabel, tautan biasa. Tidak ada dropdown bertingkat: tiap tingkat punya
 * URL sendiri, sehingga hasilnya bisa disalin dan tombol kembali peramban bekerja.
 *
 * <p>Setiap pembacaan lewat {@link PaketService#require} atau query ber-{@code clientId},
 * sehingga milik Client lain dijawab 404, bukan 403 (TC-36, TC-09). Itu juga syarat memakai
 * {@code findByPaketIdOrderByPositionAsc}/{@code borrowedSourceIds}, yang sengaja tidak
 * memeriksa kepemilikan maupun soft delete Paket sendiri.
 */
@Controller
public class BankSoalController {

    private final PaketService pakets;
    private final PaketRepository paketRepository;
    private final PaketBorrowService borrow;
    private final QuestionService questions;
    private final QuestionRepository questionRepository;
    private final TaxonomyService taxonomy;

    public BankSoalController(PaketService pakets, PaketRepository paketRepository,
                              PaketBorrowService borrow, QuestionService questions,
                              QuestionRepository questionRepository, TaxonomyService taxonomy) {
        this.pakets = pakets;
        this.paketRepository = paketRepository;
        this.borrow = borrow;
        this.questions = questions;
        this.questionRepository = questionRepository;
        this.taxonomy = taxonomy;
    }

    /** Tingkat 1: daftar Subject dengan jumlah Paket. Tingkat 2 bila {@code subjectId} terisi. */
    @GetMapping("/bank-soal")
    public String index(@RequestParam(required = false) UUID subjectId,
                        @AuthenticationPrincipal UserPrincipal user,
                        Model model) {
        UUID clientId = user.requireClientId();
        List<SubjectEntity> subjects = taxonomy.visibleSubjects(clientId);
        model.addAttribute("subjects", subjects);
        if (subjectId == null) {
            // Jumlah diagregasi satu query, bukan dihitung per baris tabel.
            Map<UUID, Long> jumlahPaket = new HashMap<>();
            paketRepository.countBySubject(clientId)
                    .forEach(c -> jumlahPaket.put(c.getSubjectId(), c.getJumlah()));
            model.addAttribute("jumlahPaket", jumlahPaket);
            return "bank/subject";
        }
        model.addAttribute("subject", taxonomy.requireVisibleSubject(subjectId, clientId));
        model.addAttribute("pakets",
                paketRepository.findByClientIdAndSubjectIdOrderByTitleAsc(clientId, subjectId));
        Map<UUID, Long> jumlahSoal = new HashMap<>();
        questionRepository.countByPaket(clientId)
                .forEach(c -> jumlahSoal.put(c.getPaketId(), c.getJumlah()));
        model.addAttribute("jumlahSoal", jumlahSoal);
        return "bank/paket";
    }

    /** Subject sudah ada dipakai ulang, belum ada dibuat — satu kolom nama (AC-B06). */
    @PostMapping("/bank-soal/paket")
    public String createPaket(@RequestParam String title,
                              @RequestParam(required = false) UUID subjectId,
                              @RequestParam(required = false) String subjectName,
                              @AuthenticationPrincipal UserPrincipal user) {
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft(title, subjectId, subjectName),
                user.requireClientId(), user.userId());
        return "redirect:/bank-soal/paket/" + paket.getId();
    }

    /** Tingkat 3: isi Paket, soal dikelompokkan per Topic. */
    @GetMapping("/bank-soal/paket/{id}")
    public String isiPaket(@PathVariable UUID id,
                           @AuthenticationPrincipal UserPrincipal user,
                           Model model) {
        PaketEntity paket = pakets.require(id, user.requireClientId());
        model.addAttribute("paket", paket);
        model.addAttribute("topics", pakets.topicsOf(id));
        model.addAttribute("soalPerTopic", questions.groupByTopic(id));
        return "bank/isi";
    }

    @PostMapping("/bank-soal/paket/{id}/topic")
    public String addTopic(@PathVariable UUID id, @RequestParam String title,
                           @AuthenticationPrincipal UserPrincipal user) {
        pakets.addTopic(id, title, user.requireClientId());
        return "redirect:/bank-soal/paket/" + id;
    }

    /**
     * Fragmen {@code <option>} Topic satu Paket, dipakai ulang panel penelusuran perakit Exercise
     * (Task 12) supaya daftar dan urutan Topic-nya konsisten dengan halaman isi Paket ini —
     * bukan menulis ulang query yang sama di {@code ExerciseController}.
     */
    @GetMapping("/bank-soal/paket/{id}/topic-options")
    public String topicOptions(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal user,
                               Model model) {
        pakets.require(id, user.requireClientId());
        model.addAttribute("topics", pakets.topicsOf(id));
        return "bank/isi :: opsiTopic";
    }

    @GetMapping("/bank-soal/paket/{id}/soal/baru")
    public String soalBaru(@PathVariable UUID id, @RequestParam UUID topicId,
                           @AuthenticationPrincipal UserPrincipal user, Model model) {
        PaketEntity paket = pakets.require(id, user.requireClientId());
        isiEditor(paket, null, topicId, model);
        return "soal/editor";
    }

    /**
     * Menyimpan soal baru ke Paket di jalur — bukan Paket yang disimpulkan dari Topic-nya.
     * {@link QuestionService#create} yang menolak Topic dari Paket lain (AC-B02).
     */
    @PostMapping("/bank-soal/paket/{id}/soal")
    public String simpanSoal(@PathVariable UUID id,
                             @RequestParam UUID topicId,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @RequestParam(required = false) String lanjut,
                             @AuthenticationPrincipal UserPrincipal user) {
        questions.create(
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml,
                        optionBody, correctIndex),
                user.requireClientId(), id);
        // "Simpan & buat lagi" kembali ke formulir Topic yang sama: menulis 30 soal berurutan
        // tidak boleh memaksa satu perjalanan bolak-balik lewat halaman isi per soal.
        return lanjut != null
                ? "redirect:/bank-soal/paket/" + id + "/soal/baru?topicId=" + topicId
                : "redirect:/bank-soal/paket/" + id;
    }

    @GetMapping("/bank-soal/soal/{id}")
    public String ubahSoal(@PathVariable UUID id,
                           @AuthenticationPrincipal UserPrincipal user, Model model) {
        UUID clientId = user.requireClientId();
        QuestionEntity soal = questions.require(id, clientId);
        // require pada Paket induk: soal di bawah Paket yang sudah dihapus lunak ikut 404.
        PaketEntity paket = pakets.require(soal.getPaketId(), clientId);
        isiEditor(paket, soal, soal.getTopicId(), model);
        return "soal/editor";
    }

    /** Perubahan dibalas fragmen detail di tempat, bukan halaman penuh (TC-14). */
    @PutMapping("/bank-soal/soal/{id}")
    public String updateSoal(@PathVariable UUID id,
                             @RequestParam UUID topicId,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @AuthenticationPrincipal UserPrincipal user, Model model) {
        UUID clientId = user.requireClientId();
        PaketEntity paket = pakets.require(
                questions.require(id, clientId).getPaketId(), clientId);
        QuestionEntity soal = questions.update(id,
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml,
                        optionBody, correctIndex),
                clientId, paket.getId());
        isiEditor(paket, soal, soal.getTopicId(), model);
        return "soal/editor :: detail";
    }

    /**
     * Panel pinjam sebagai fragmen HTMX. Soal yang salinannya sudah ada di Paket tujuan tidak
     * dirender sama sekali (AC-B04) — menampilkannya lalu menolak saat disimpan hanya
     * memindahkan kekecewaan ke belakang.
     */
    @GetMapping("/bank-soal/paket/{id}/pinjam")
    public String panelPinjam(@PathVariable UUID id,
                              @RequestParam(required = false) UUID sourcePaketId,
                              @AuthenticationPrincipal UserPrincipal user,
                              Model model) {
        UUID clientId = user.requireClientId();
        PaketEntity target = pakets.require(id, clientId);
        model.addAttribute("paket", target);
        model.addAttribute("topics", pakets.topicsOf(id));
        model.addAttribute("sudahDipinjam", borrow.borrowedSourceIds(id));
        // Sumber dibatasi Paket se-Subject: meminjam lintas mapel hampir selalu salah pilih.
        model.addAttribute("paketLain", paketRepository
                .findByClientIdAndSubjectIdOrderByTitleAsc(clientId, target.getSubjectId())
                .stream().filter(p -> !p.getId().equals(id)).toList());
        if (sourcePaketId != null) {
            // require dulu (TC-36): groupByTopic sendiri tidak memeriksa pemilik Paket sumber.
            PaketEntity sumber = pakets.require(sourcePaketId, clientId);
            model.addAttribute("sumber", sumber);
            model.addAttribute("topicSumber", pakets.topicsOf(sumber.getId()));
            model.addAttribute("soalSumber", questions.groupByTopic(sumber.getId()));
        }
        return "bank/isi :: panelPinjam";
    }

    @PostMapping("/bank-soal/paket/{id}/pinjam")
    public String pinjam(@PathVariable UUID id,
                         @RequestParam UUID topicId,
                         @RequestParam(required = false) List<UUID> questionIds,
                         @RequestParam(required = false) UUID sourceTopicId,
                         @AuthenticationPrincipal UserPrincipal user) {
        UUID clientId = user.requireClientId();
        if (sourceTopicId != null) {
            borrow.borrowTopic(id, topicId, sourceTopicId, clientId, user.userId());
        } else {
            borrow.borrowQuestions(id, topicId, questionIds, clientId, user.userId());
        }
        return "redirect:/bank-soal/paket/" + id;
    }

    /**
     * Atribut bersama editor {@code soal/editor}. {@code paket} terisi itulah yang mengubah
     * templatnya ke mode Bank Soal: breadcrumb Paket, Topic milik Paket saja, jalur simpan
     * per-Paket.
     */
    private void isiEditor(PaketEntity paket, QuestionEntity soal, UUID topicId, Model model) {
        model.addAttribute("paket", paket);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", soal == null ? List.of() : questions.optionsOf(soal.getId()));
        model.addAttribute("topicId", topicId);
        model.addAttribute("topics", pakets.topicsOf(paket.getId()));
        model.addAttribute("basePath", "/bank-soal");
    }
}
