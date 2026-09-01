package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Ruang kerja konten master Eduscreen: Topic GLOBAL dan Question milik Eduscreen.
 *
 * <p>Seluruh pemanggilan ke {@link QuestionService} mengirim {@code clientId} <b>null</b> —
 * itulah penanda kepemilikan Eduscreen (FR-060). Tidak ada satu pun jalur di sini yang menerima
 * {@code clientId} bukan-null, sehingga Eduscreen Admin tidak punya jalan membaca bank soal
 * sebuah sekolah dari layar ini (FR-080, BR-P04). Satu-satunya jalur bacanya ke data Client
 * tetap akses dukungan berizin dan berbatas waktu (ADR-0015).
 *
 * <p>Rute berada di bawah {@code /eduscreen/**} yang sudah dipagari
 * {@code hasRole("EDUSCREEN_ADMIN")} di {@code SecurityConfig}.
 *
 * <p>Templat {@code soal/daftar} dan {@code soal/editor} dipakai ulang apa adanya lewat atribut
 * {@code basePath}: editor soal master dan editor soal Client menampilkan hal yang sama persis,
 * dan menggandakannya berarti setiap perbaikan editor harus dikerjakan dua kali.
 */
@Controller
public class MasterContentController {

    private static final int UKURAN_HALAMAN = 20;
    private static final int UKURAN_HALAMAN_BANK_SOAL = 10;

    /** Konten master tidak dimiliki Client mana pun; null-lah yang menyatakannya. */
    private static final UUID MASTER = null;

    private final QuestionService questions;
    private final TaxonomyService taxonomy;
    private final MasterPublishingService publishing;
    private final ExerciseService paket;

    public MasterContentController(QuestionService questions,
                                   TaxonomyService taxonomy,
                                   MasterPublishingService publishing,
                                   ExerciseService paket) {
        this.questions = questions;
        this.taxonomy = taxonomy;
        this.publishing = publishing;
        this.paket = paket;
    }

    // -------------------------------------------------------------- Subject

    /** Subject yang lahir di sini GLOBAL — dibaca semua Client, tidak pernah disalin (BR-O02). */
    @PostMapping("/eduscreen/subject")
    public String createSubject(@RequestParam String name, Model model) {
        model.addAttribute("subject", taxonomy.createGlobalSubject(name));
        return "soal/daftar :: opsiSubjectBaru";
    }

    /**
     * Memperbaiki nama Subject global yang salah ketik.
     *
     * <p>ponytail: sengaja bukan HTMX melainkan muat ulang penuh. Menukar satu {@code <option>} di
     * tengah {@code <select>} menuntut penanda id per opsi dan pemulihan keadaan terpilih, sementara
     * rename adalah tindakan langka. Konsekuensinya nama kembar memunculkan fragmen galat 400
     * telanjang; kalau itu mulai mengganggu, ubah menjadi hx-post bertarget pesan tersendiri.
     */
    @PostMapping("/eduscreen/subject/{id}/nama")
    public String renameSubject(@PathVariable UUID id, @RequestParam String name) {
        taxonomy.renameGlobalSubject(id, name);
        return "redirect:/eduscreen/soal?subjectId=" + id;
    }

    // ---------------------------------------------------------------- Topic

    @GetMapping("/eduscreen/subject/{id}/topic")
    public String topics(@PathVariable UUID id, Model model) {
        taxonomy.requireGlobalSubject(id);
        model.addAttribute("topics", taxonomy.visibleTopics(id, MASTER));
        return "soal/daftar :: topics";
    }

    /** Topic yang lahir di sini GLOBAL: dibaca semua Client, dan disalin saat adopsi (BR-O02). */
    @PostMapping("/eduscreen/subject/{id}/topic")
    public String createTopic(@PathVariable UUID id, @RequestParam String name, Model model) {
        model.addAttribute("topic", taxonomy.createGlobalTopic(id, name));
        return "soal/daftar :: opsiTopicBaru";
    }

    // -------------------------------------------------------- Question master

    @GetMapping("/eduscreen/soal")
    public String search(@RequestParam(required = false) UUID subjectId,
                         @RequestParam(required = false) UUID topicId,
                         @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) UUID exerciseId,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         Model model) {
        model.addAttribute("hasil",
                questions.searchMaster(subjectId, topicId, q, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
        model.addAttribute("exerciseId", exerciseId);
        isiJalur(model);
        if (hxRequest != null) {
            return "soal/daftar :: hasil";
        }
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        model.addAttribute("topics", subjectId != null ? taxonomy.visibleTopics(subjectId, MASTER) : List.of());
        model.addAttribute("menuAktif", "soal");
        return "soal/daftar";
    }

    @GetMapping("/eduscreen/soal/baru")
    public String baru(@RequestParam(required = false) UUID topicId, Model model) {
        model.addAttribute("soal", null);
        model.addAttribute("opsi", List.<QuestionOptionEntity>of());
        model.addAttribute("topicId", topicId);
        isiTaksonomiEditor(topicId, model);
        model.addAttribute("menuAktif", "soal");
        return "soal/editor";
    }

    @PostMapping("/eduscreen/soal")
    public String create(@RequestParam UUID topicId,
                         @RequestParam QuestionType type,
                         @RequestParam String bodyHtml,
                         @RequestParam(required = false) String explanationHtml,
                         @RequestParam(required = false) List<String> optionBody,
                         @RequestParam(defaultValue = "-1") int correctIndex) {
        QuestionEntity soal = questions.create(
                new QuestionService.QuestionDraft(topicId, type, bodyHtml, explanationHtml,
                        buildOptions(type, optionBody, correctIndex)),
                MASTER);
        return "redirect:/eduscreen/soal/" + soal.getId();
    }

    @GetMapping("/eduscreen/soal/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        QuestionEntity soal = questions.require(id, MASTER);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(id));
        model.addAttribute("topicId", soal.getTopicId());
        isiTaksonomiEditor(soal.getTopicId(), model);
        model.addAttribute("menuAktif", "soal");
        return "soal/editor";
    }

    @PutMapping("/eduscreen/soal/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam UUID topicId,
                         @RequestParam QuestionType type,
                         @RequestParam String bodyHtml,
                         @RequestParam(required = false) String explanationHtml,
                         @RequestParam(required = false) List<String> optionBody,
                         @RequestParam(defaultValue = "-1") int correctIndex,
                         Model model) {
        QuestionEntity soal = questions.update(id,
                new QuestionService.QuestionDraft(topicId, type, bodyHtml, explanationHtml,
                        buildOptions(type, optionBody, correctIndex)),
                MASTER);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(id));
        model.addAttribute("topicId", soal.getTopicId());
        isiTaksonomiEditor(soal.getTopicId(), model);
        return "soal/editor :: detail";
    }

    /**
     * Soft delete (FR-065): Question hilang dari ruang kerja dan dari katalog seluruh Client,
     * sementara salinan yang sudah diadopsi tetap utuh — salinan itu baris tersendiri yang tidak
     * punya tautan hidup ke master (ADR-0001).
     */
    @DeleteMapping("/eduscreen/soal/{id}")
    public String delete(@PathVariable UUID id, Model model) {
        questions.softDelete(id, MASTER);
        model.addAttribute("pesan", "Soal master dihapus. Salinan yang sudah diadopsi Client tidak terpengaruh.");
        isiJalur(model);
        return "soal/daftar :: konfirmasiHapus";
    }

    // ----------------------------------------------------------- paket master

    @GetMapping("/eduscreen/paket")
    public String daftarPaket(@RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        model.addAttribute("daftar", paket.list(MASTER, q, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("q", q);
        isiJalur(model);
        model.addAttribute("menuAktif", "paket");
        return "eduscreen/paket-daftar";
    }

    @PostMapping("/eduscreen/paket")
    public String buatPaket(@RequestParam String title) {
        return "redirect:/eduscreen/paket/" + paket.create(MASTER, title, null).getId();
    }

    @GetMapping("/eduscreen/paket/{id}")
    public String perakitPaket(@PathVariable UUID id, Model model) {
        muatItem(id, model);
        // Penelusuran konten master saat perakit dibuka; fragmen hasilnya identik dengan yang
        // dipakai ruang kerja Question, hanya dengan exerciseId terisi (FR-071).
        model.addAttribute("hasil",
                questions.searchMaster(null, null, null, PageRequest.of(0, UKURAN_HALAMAN_BANK_SOAL)));
        model.addAttribute("subjectId", null);
        model.addAttribute("topicId", null);
        model.addAttribute("q", null);
        model.addAttribute("exerciseId", id);
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        model.addAttribute("menuAktif", "paket");
        return "eduscreen/paket";
    }

    /**
     * Tidak ada validasi Subject atau Topic di sini: paket master boleh memuat Question master
     * mana pun, dan Eduscreen Admin berpindah bebas antar Subject dalam satu sesi perakitan
     * (FR-071). Paket master juga tidak pernah terkunci — ia tidak pernah menjadi Assignment
     * (FR-073).
     */
    @PostMapping("/eduscreen/paket/{id}/item")
    public String tambahItem(@PathVariable UUID id, @RequestParam UUID questionId, Model model) {
        paket.addQuestion(id, questionId, MASTER);
        muatItem(id, model);
        return "eduscreen/paket :: item";
    }

    /** Padanan borongan {@code POST /exercise/{id}/item/terpilih}; panel penelusurannya sama. */
    @PostMapping("/eduscreen/paket/{id}/item/terpilih")
    public String tambahItemTerpilih(@PathVariable UUID id,
                                     @RequestParam(required = false) List<UUID> questionIds,
                                     Model model) {
        paket.addQuestions(id, questionIds, MASTER);
        muatItem(id, model);
        return "eduscreen/paket :: item";
    }

    @DeleteMapping("/eduscreen/paket/{id}/item/{questionId}")
    public String hapusItem(@PathVariable UUID id, @PathVariable UUID questionId, Model model) {
        paket.removeQuestion(id, questionId, MASTER);
        muatItem(id, model);
        return "eduscreen/paket :: item";
    }

    @PutMapping("/eduscreen/paket/{id}/urutan")
    public String urutkanItem(@PathVariable UUID id, @RequestParam List<UUID> questionIds, Model model) {
        paket.reorder(id, questionIds, MASTER);
        muatItem(id, model);
        return "eduscreen/paket :: item";
    }

    // ---------------------------------------------------------- penerbitan

    @PostMapping("/eduscreen/soal/{id}/terbit")
    public String terbitkanSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.publishQuestion(id), model);
    }

    /**
     * Menarik dari peredaran (FR-068): Question hilang dari katalog seluruh Client, sementara
     * salinan yang sudah diadopsi tetap utuh — ia baris tersendiri tanpa tautan ke master.
     */
    @PostMapping("/eduscreen/soal/{id}/tarik")
    public String tarikSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.unpublishQuestion(id), model);
    }

    @PostMapping("/eduscreen/paket/{id}/terbit")
    public String terbitkanPaket(@PathVariable UUID id, Model model) {
        model.addAttribute("paketIni", publishing.publishExercise(id));
        isiJalur(model);
        return "eduscreen/paket :: statusPaket";
    }

    @PostMapping("/eduscreen/paket/{id}/tarik")
    public String tarikPaket(@PathVariable UUID id, Model model) {
        model.addAttribute("paketIni", publishing.unpublishExercise(id));
        isiJalur(model);
        return "eduscreen/paket :: statusPaket";
    }

    // ------------------------------------------------------------- pembantu

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

    /** Isi paket beserta batang soalnya, dipakai seluruh jalur yang mengubah susunannya. */
    private void muatItem(UUID paketId, Model model) {
        ExerciseEntity exercise = paket.require(paketId, MASTER);
        List<ExerciseItemEntity> item = paket.itemsOf(paketId);
        Map<UUID, QuestionEntity> soal = new LinkedHashMap<>();
        for (ExerciseItemEntity satu : item) {
            soal.put(satu.getQuestionId(), questions.require(satu.getQuestionId(), MASTER));
        }
        model.addAttribute("paketIni", exercise);
        model.addAttribute("item", item);
        model.addAttribute("soal", soal);
        isiJalur(model);
    }

    /** Satu baris hasil setelah keadaan terbitnya berubah; HTMX menukar baris itu saja. */
    private String barisSoal(QuestionEntity soal, Model model) {
        model.addAttribute("pertanyaan", soal);
        model.addAttribute("exerciseId", null);
        isiJalur(model);
        return "soal/daftar :: barisSoal";
    }

    /** Jalur yang membedakan ruang kerja master dari bank soal Client di templat bersama. */
    private void isiJalur(Model model) {
        model.addAttribute("basePath", "/eduscreen/soal");
        model.addAttribute("subjectBasePath", "/eduscreen/subject");
        model.addAttribute("subjectCreatePath", "/eduscreen/subject");
        model.addAttribute("itemPath", "/eduscreen/paket");
        model.addAttribute("master", true);
    }

    private void isiTaksonomiEditor(UUID topicId, Model model) {
        isiJalur(model);
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        if (topicId == null) {
            model.addAttribute("subjectId", null);
            model.addAttribute("topics", List.<TopicEntity>of());
            return;
        }
        TopicEntity topic = taxonomy.requireWritableTopic(topicId, MASTER);
        model.addAttribute("subjectId", topic.getSubjectId());
        model.addAttribute("topics", taxonomy.visibleTopics(topic.getSubjectId(), MASTER));
    }
}
