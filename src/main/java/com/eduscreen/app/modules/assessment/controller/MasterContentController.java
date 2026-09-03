package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.StatusTerbit;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository.Placement;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.NeedsVersionChoiceException;
import com.eduscreen.app.modules.assessment.service.PaketBorrowService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.PaketVersionService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ruang kerja kembar Bank Soal untuk konten master Eduscreen (ADR-0018).
 *
 * <p>Rute kembar {@code /eduscreen/bank-soal} dari {@link BankSoalController}: templat
 * {@code bank/*} yang sama, {@code basePath} berbeda. Yang membedakannya dari Bank Soal Client
 * hanya kepemilikan — {@code clientId} selalu {@code null} di sini, itulah penanda konten
 * Eduscreen (FR-060) — dan dua kemampuan tambahan yang tidak dimiliki Client: menerbitkan/menarik
 * Paket ke katalog (FR-066 sampai FR-068), dan menerbitkan/menarik Question satu per satu, yang
 * menentukan isi mana yang ikut terbit saat Paket-nya naik ke katalog (AC-B12, ADR-0020).
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

    private static final int UKURAN_HALAMAN = 20;

    private final QuestionService questions;
    private final QuestionRepository questionRepository;
    private final TaxonomyService taxonomy;
    private final PaketService pakets;
    private final PaketRepository paketRepository;
    private final PaketBorrowService borrow;
    private final MasterPublishingService publishing;
    private final PaketVersionService versions;

    public MasterContentController(QuestionService questions,
                                   QuestionRepository questionRepository,
                                   TaxonomyService taxonomy,
                                   PaketService pakets,
                                   PaketRepository paketRepository,
                                   PaketBorrowService borrow,
                                   MasterPublishingService publishing,
                                   PaketVersionService versions) {
        this.questions = questions;
        this.questionRepository = questionRepository;
        this.taxonomy = taxonomy;
        this.pakets = pakets;
        this.paketRepository = paketRepository;
        this.borrow = borrow;
        this.publishing = publishing;
        this.versions = versions;
    }

    /**
     * Paket master yang seluruh versinya sudah terbit menolak tulisan (ADR-0021). Bukan 409
     * telanjang: pengguna dialihkan ke halaman Paket, yang memuat pilihan "versi baru" atau
     * "instance baru" di bagian atasnya. Permintaan HTMX dialihkan lewat {@code HX-Redirect}
     * supaya klien memuat ulang halaman penuh, bukan menukar satu baris dengan halaman.
     */
    @ExceptionHandler(NeedsVersionChoiceException.class)
    public ResponseEntity<Void> pilihVersiDulu(NeedsVersionChoiceException e, HttpServletRequest request) {
        String tujuan = BASE_PATH + "/paket/" + e.getPaketId() + "?beku";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return ResponseEntity.ok().header("HX-Redirect", tujuan).build();
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header("Location", tujuan).build();
    }

    /**
     * Tingkat 1: tabel Paket master, seluruh Subject GLOBAL sekaligus. Tersaring ke satu Subject
     * bila {@code subjectId} terisi — sejajar {@link BankSoalController#index}.
     */
    @GetMapping("/eduscreen/bank-soal")
    public String index(@RequestParam(required = false) UUID subjectId,
                        @RequestParam(required = false) String cariPaket,
                        Model model) {
        List<SubjectEntity> subjects = taxonomy.visibleSubjects(MASTER);
        model.addAttribute("subjects", subjects);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("cariPaket", cariPaket);
        isiJalur(model);
        model.addAttribute("pakets", saringJudul(subjectId == null
                ? paketRepository.findAllMaster()
                : paketRepository.findMaster(subjectId), cariPaket));
        if (subjectId != null) {
            model.addAttribute("subject", taxonomy.requireGlobalSubject(subjectId));
        }
        model.addAttribute("jumlahSoal", jumlahSoalMaster());
        model.addAttribute("namaSubject", namaSubject(subjects));
        return "bank/paket";
    }

    /**
     * Pencarian Question master lintas-Paket (FR-064) — kemampuan yang hilang saat rute
     * {@code /eduscreen/soal} lama dicabut Task 10, dikembalikan di sini karena
     * {@code QuestionService.searchMaster} tidak punya jalur lain menuju Eduscreen Admin (temuan
     * review Task 10).
     */
    @GetMapping("/eduscreen/bank-soal/cari")
    public String cari(@RequestParam(required = false) UUID subjectId,
                       @RequestParam(required = false) UUID topicId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) StatusTerbit status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("hasil",
                questions.searchMaster(subjectId, topicId, q, status, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        model.addAttribute("topics", subjectId != null ? taxonomy.topicsOwnedBy(subjectId, MASTER) : List.of());
        isiJalur(model);
        return "bank/cari";
    }

    /**
     * Memperbaiki nama Subject GLOBAL yang salah ketik (BR-O04: "boleh diperbaiki kapan saja").
     * Tidak ada peran lain yang boleh melakukan ini — Client Admin memang tidak berwenang atas
     * Subject GLOBAL — jadi tanpa rute ini aturannya mati (temuan review Task 10).
     */
    @PostMapping("/eduscreen/bank-soal/subject/{id}/nama")
    public String renameSubject(@PathVariable UUID id, @RequestParam String name) {
        taxonomy.renameGlobalSubject(id, name);
        return "redirect:" + BASE_PATH + "?subjectId=" + id;
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

    /** Tingkat 2: isi Paket master, soal dikelompokkan per Topic. */
    @GetMapping("/eduscreen/bank-soal/paket/{id}")
    public String isiPaket(@PathVariable UUID id, Model model) {
        PaketEntity paket = pakets.require(id, MASTER);
        model.addAttribute("paket", paket);
        // Versi yang sedang dibaca: versi kerja, atau versi terbit terakhir bila semuanya beku.
        // Templat memakainya untuk menampilkan nomor versi dan menawarkan pilihan versi baru.
        model.addAttribute("versi", pakets.versionOf(id));
        model.addAttribute("topics", pakets.topicsOf(id));
        model.addAttribute("soalPerTopic", questions.groupByTopic(id));
        model.addAttribute("jumlahDraf", publishing.draftQuestionsOf(id).size());
        isiJalur(model);
        return "bank/isi";
    }

    /** Versi kerja baru dari versi terbit terakhir; penempatannya disalin, soalnya tidak (ADR-0021). */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/versi-baru")
    public String versiBaru(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
        versions.newVersion(id, user.userId());
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    /** Paket master lain yang berbagi soal yang sama; berangkat ke ruang kerja Paket barunya. */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/instance-baru")
    public String instanceBaru(@PathVariable UUID id, @RequestParam String title,
                               @AuthenticationPrincipal UserPrincipal user) {
        PaketEntity baru = versions.newInstance(id, title, user.userId());
        return "redirect:" + BASE_PATH + "/paket/" + baru.getId();
    }

    @PostMapping("/eduscreen/bank-soal/paket/{id}/topic")
    public String addTopic(@PathVariable UUID id, @RequestParam String title) {
        pakets.addTopic(id, title, MASTER);
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    @GetMapping("/eduscreen/bank-soal/paket/{id}/soal/baru")
    public String soalBaru(@PathVariable UUID id,
                           @RequestParam(required = false) UUID topicId, Model model) {
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
                             @RequestParam String topicTitle,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @RequestParam(required = false) String lanjut) {
        UUID topicId = pakets.resolveTopic(id, topicTitle, MASTER).getId();
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
        Placement penempatan = questions.requirePlacement(id);
        // require pada Paket induk: soal di bawah Paket yang sudah dihapus lunak ikut 404.
        PaketEntity paket = pakets.require(penempatan.getPaketId(), MASTER);
        isiEditor(paket, soal, penempatan.getTopicId(), model);
        return "soal/editor";
    }

    /** Perubahan dibalas fragmen detail di tempat, bukan halaman penuh (TC-14). */
    /** Padanan {@link BankSoalController#pratinjauSoal} untuk ruang kerja master. */
    @GetMapping("/eduscreen/bank-soal/soal/{id}/pratinjau")
    public String pratinjauSoal(@PathVariable UUID id, Model model) {
        QuestionEntity soal = questions.require(id, MASTER);
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(soal.getId()));
        return "soal/editor :: pratinjauPanel";
    }

    /**
     * Soal master terbit beku: editor menyimpannya sebagai revisi — baris baru yang menggantikan
     * di versi kerja (ADR-0021). Balasannya fragmen detail SOAL BARU, jadi simpan berikutnya dari
     * editor yang sama sudah menyunting revisinya, bukan mencoba merevisi ulang baris lama.
     */
    @PutMapping("/eduscreen/bank-soal/soal/{id}/revisi")
    public String revisiSoal(@PathVariable UUID id,
                             @RequestParam String topicTitle,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             Model model) {
        questions.require(id, MASTER);
        PaketEntity paket = pakets.require(questions.requirePlacement(id).getPaketId(), MASTER);
        UUID topicId = pakets.resolveTopic(paket.getId(), topicTitle, MASTER).getId();
        QuestionEntity baru = questions.revise(id,
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml, optionBody, correctIndex),
                paket.getId());
        isiEditor(paket, baru, topicId, model);
        return "soal/editor :: detail";
    }

    @PutMapping("/eduscreen/bank-soal/soal/{id}")
    public String updateSoal(@PathVariable UUID id,
                             @RequestParam String topicTitle,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             Model model) {
        questions.require(id, MASTER);
        PaketEntity paket = pakets.require(questions.requirePlacement(id).getPaketId(), MASTER);
        UUID topicId = pakets.resolveTopic(paket.getId(), topicTitle, MASTER).getId();
        QuestionEntity soal = questions.update(id,
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml, optionBody, correctIndex),
                MASTER, paket.getId());
        isiEditor(paket, soal, topicId, model);
        return "soal/editor :: detail";
    }

    /**
     * Data panel pinjam master sebagai JSON (ADR-0019), sejajar
     * {@link BankSoalController#panelPinjamData}: {@code searchMasterBorrowable} yang membawa
     * {@code clientId is null} adalah padanan {@code searchForBuilder} sisi Client, dan
     * {@code PinjamPanelData} yang sama dirakit lewat helper statis
     * {@code BankSoalController.pinjamPanelData} supaya bentuk JSON kedua sisi identik.
     *
     * <p>Tidak butuh {@code isiJalur}/{@code basePath} seperti rute HTML lain di kelas ini: JSON
     * tidak mengandung tautan template yang bisa jatuh ke fallback Client — klien (Alpine di
     * {@code bank/isi.html}) sudah tahu {@code basePath} dari halaman SSR yang memuatnya.
     */
    @GetMapping("/eduscreen/bank-soal/paket/{id}/pinjam")
    @ResponseBody
    public PinjamPanelData panelPinjamData(@PathVariable UUID id,
                                           @RequestParam(required = false) UUID filterSubjectId,
                                           @RequestParam(required = false) UUID filterPaketId,
                                           @RequestParam(required = false) UUID filterTopicId,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page) {
        pakets.require(id, MASTER);

        List<PaketEntity> paketLain = paketRepository.findAllMaster().stream()
                .filter(p -> !p.getId().equals(id))
                .toList();
        Map<UUID, PaketEntity> paketById = paketLain.stream()
                .collect(Collectors.toMap(PaketEntity::getId, p -> p));
        // AC-B21: Subject yang ditawarkan hanya yang benar-benar punya Paket master sumber, sama
        // alasan dengan BankSoalController#panelPinjamData.
        Set<UUID> subjectIdDenganPaket = paketLain.stream().map(PaketEntity::getSubjectId).collect(Collectors.toSet());
        List<SubjectEntity> subjects = taxonomy.visibleSubjects(MASTER).stream()
                .filter(s -> subjectIdDenganPaket.contains(s.getId()))
                .toList();
        Map<UUID, String> namaSubject = namaSubject(subjects);
        List<PaketEntity> paketPilihan = filterSubjectId == null ? paketLain
                : paketLain.stream().filter(p -> p.getSubjectId().equals(filterSubjectId)).toList();
        // require() DULU wajib (TC-36, TC-09), sama alasan dengan BankSoalController#panelPinjamData:
        // findByPaketIdOrderByPositionAsc tidak menyaring clientId sama sekali, jadi tanpa ini
        // filterPaketId milik sebuah Client membalas judul Topic-nya begitu saja ke ruang kerja
        // master.
        List<TopicEntity> filterTopics = filterPaketId != null
                ? pakets.topicsOf(pakets.require(filterPaketId, MASTER).getId())
                : List.of();

        Set<UUID> dikecualikan = new HashSet<>(borrow.borrowedSourceIds(id));
        dikecualikan.addAll(questions.questionIdsIn(id));
        Page<QuestionEntity> hasil = questions.searchMasterBorrowable(filterSubjectId, filterPaketId,
                filterTopicId, dikecualikan, q, PageRequest.of(page, UKURAN_HALAMAN));

        Map<UUID, Placement> penempatan = questions.placementsOf(
                hasil.getContent().stream().map(QuestionEntity::getId).toList());
        Map<UUID, String> judulTopic = new HashMap<>();
        pakets.topicsByIds(penempatan.values().stream().map(Placement::getTopicId).collect(Collectors.toSet()))
                .forEach(t -> judulTopic.put(t.getId(), t.getTitle()));

        return BankSoalController.pinjamPanelData(
                subjects, namaSubject, paketPilihan, paketById, filterTopics, hasil, penempatan, judulTopic);
    }

    /** Kembaran {@link BankSoalController#pinjam}: Topic tujuan sebagai nama, diselesaikan
     *  {@code resolveTopic}, dan submit kosong tidak melahirkan Topic kosong. */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/pinjam")
    public String pinjam(@PathVariable UUID id,
                         @RequestParam String topicTitle,
                         @RequestParam(required = false) List<UUID> questionIds,
                         @RequestParam(required = false) UUID sourceTopicId,
                         @AuthenticationPrincipal UserPrincipal user) {
        if (sourceTopicId == null && (questionIds == null || questionIds.isEmpty())) {
            return "redirect:" + BASE_PATH + "/paket/" + id;
        }
        UUID topicId = pakets.resolveTopic(id, topicTitle, MASTER).getId();
        if (sourceTopicId != null) {
            borrow.borrowTopic(id, topicId, sourceTopicId, MASTER, user.userId());
        } else {
            borrow.borrowQuestions(id, topicId, questionIds, MASTER, user.userId());
        }
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    // ----------------------------------------------------------- penerbitan

    /**
     * Terbit membuat Paket terlihat di katalog seluruh Client (FR-066).
     *
     * <p>Kalau Paket masih menyimpan soal draf, permintaan tanpa {@code soalDraf} tidak langsung
     * menerbitkan apa pun: ia membalas panel pilihan (ADR-0020) — ikut terbitkan drafnya, atau
     * terbitkan yang sudah siap saja. Panel itu bukan baris tabel, jadi balasannya dialihkan ke
     * wadah panel lewat {@code HX-Retarget} alih-alih menuntut tombolnya tahu dua target.
     */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/terbit")
    public String terbitPaket(@PathVariable UUID id,
                              @RequestParam(required = false) String soalDraf,
                              Model model,
                              HttpServletResponse response) {
        if (soalDraf == null) {
            List<QuestionEntity> draf = publishing.draftQuestionsOf(id);
            if (!draf.isEmpty()) {
                response.setHeader("HX-Retarget", "#panel");
                response.setHeader("HX-Reswap", "innerHTML");
                model.addAttribute("paket", pakets.require(id, MASTER));
                model.addAttribute("jumlahDraf", draf.size());
                model.addAttribute("jumlahSiap", publishing.publishedCountOf(id));
                isiJalur(model);
                return "bank/paket :: pilihanTerbit";
            }
        }
        return barisPaket(publishing.publishPaket(id, "semua".equals(soalDraf)), model);
    }

    /**
     * Tarik beberapa Paket sekaligus dari centangan di daftar (AC-B26). Hanya tarik: menerbitkan
     * Paket punya dialog per Paket soal draf di dalamnya (ADR-0020), yang tidak bisa dijawab
     * sekali untuk banyak Paket tanpa mengambil keputusan diam-diam atas nama pengguna.
     */
    @PostMapping("/eduscreen/bank-soal/paket/massal")
    public String massalPaket(@RequestParam String aksi,
                              @RequestParam(required = false) List<UUID> paketIds,
                              @RequestParam(required = false) UUID subjectId) {
        if (!"tarik".equals(aksi)) {
            throw new IllegalArgumentException("Aksi massal tidak dikenal: " + aksi);
        }
        for (UUID id : paketIds == null ? List.<UUID>of() : paketIds) {
            publishing.withdrawPaket(id);
        }
        return "redirect:" + BASE_PATH + (subjectId != null ? "?subjectId=" + subjectId : "");
    }

    /** Menarik tidak menyentuh satu pun salinan yang sudah diadopsi Client (FR-068, ADR-0001). */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/tarik")
    public String tarikPaket(@PathVariable UUID id, Model model) {
        return barisPaket(publishing.withdrawPaket(id), model);
    }

    /**
     * Menerbitkan seluruh soal draf di satu Paket sekaligus (AC-B19).
     *
     * <p>Form biasa dengan redirect, bukan swap HTMX: yang berubah adalah setiap baris soal di
     * setiap Topic sekaligus, dan memuat ulang halaman jauh lebih murah daripada menyusun fragmen
     * yang menukar semuanya.
     */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/soal/terbit-semua")
    public String terbitkanSemuaSoal(@PathVariable UUID id) {
        publishing.publishDraftQuestions(id);
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    /**
     * Tarik atau hapus beberapa Question sekaligus dari centangan di satu Topic (AC-B26). Form
     * biasa dengan redirect, alasan yang sama dengan {@link #terbitkanSemuaSoal}: yang berubah
     * banyak baris sekaligus. Per soalnya memanggil jalur yang sama persis dengan tombol per
     * baris, jadi gerbang AC-B17 ({@code requirePaketBelumTerbit}) ikut berlaku tanpa ditulis
     * ulang — layar menyembunyikan aksi ini selama Paket masih terbit, dan permintaan yang
     * menerobos tetap ditolak 409 di sini.
     *
     * <p>Soal yang tidak ditemukan atau bukan milik ruang kerja master menghentikan seluruh
     * permintaan dengan 404 (bukan dilewati diam-diam seperti pinjam): daftar ini datang dari
     * centangan di halaman yang sama, bukan dari pengenal yang bisa ditebak, jadi ketidakcocokan
     * adalah galat, bukan campuran wajar.
     */
    @PostMapping("/eduscreen/bank-soal/paket/{id}/soal/massal")
    public String massalSoal(@PathVariable UUID id,
                             @RequestParam String aksi,
                             @RequestParam(required = false) List<UUID> questionIds,
                             @AuthenticationPrincipal UserPrincipal user) {
        pakets.require(id, MASTER);
        for (UUID qid : questionIds == null ? List.<UUID>of() : questionIds) {
            switch (aksi) {
                case "terbit" -> publishing.publishQuestion(qid);
                case "tarik" -> publishing.unpublishQuestion(qid);
                case "hapus" -> questions.softDelete(qid, MASTER);
                default -> throw new IllegalArgumentException("Aksi massal tidak dikenal: " + aksi);
            }
        }
        return "redirect:" + BASE_PATH + "/paket/" + id;
    }

    /**
     * Terbit Question menentukan isi mana yang ikut menyeberang: hanya Question dalam keadaan ini
     * yang tampil di katalog dan tersalin saat sekolah mengadopsi Paketnya (AC-B23).
     */
    @PostMapping("/eduscreen/bank-soal/soal/{id}/terbit")
    public String terbitSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.publishQuestion(id), model);
    }

    @PostMapping("/eduscreen/bank-soal/soal/{id}/tarik")
    public String tarikSoal(@PathVariable UUID id, Model model) {
        return barisSoal(publishing.unpublishQuestion(id), model);
    }

    /**
     * Soft delete (FR-060, FR-065): Question hilang dari ruang kerja dan dari katalog seluruh
     * Client, sementara salinan yang sudah diadopsi tetap utuh — salinan itu baris tersendiri
     * yang tidak punya tautan hidup ke master (ADR-0001). Dikembalikan setelah sempat tercabut
     * bersama rute /eduscreen/soal lama (temuan review Task 10): tanpa ini Eduscreen Admin
     * tidak punya jalan menghapus konten master dari layar mana pun.
     */
    @DeleteMapping("/eduscreen/bank-soal/soal/{id}")
    public String hapusSoal(@PathVariable UUID id, Model model) {
        questions.softDelete(id, MASTER);
        model.addAttribute("pesan", "Soal master dihapus. Salinan yang sudah diadopsi Client tidak terpengaruh.");
        return "bank/isi :: konfirmasiHapus";
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
        List<TopicEntity> daftarTopic = pakets.topicsOf(paket.getId());
        model.addAttribute("topics", daftarTopic);
        // Editor menyunting JUDUL Topic, bukan id-nya: satu kolom ber-datalist yang sekaligus
        // memilih yang sudah ada dan membuat yang belum (PaketService#resolveTopic).
        model.addAttribute("topicTitle", daftarTopic.stream()
                .filter(t -> t.getId().equals(topicId))
                .map(TopicEntity::getTitle)
                .findFirst()
                .orElse(""));
        // Soal master terbit beku: editor menyimpannya sebagai revisi, bukan menimpa (ADR-0021).
        model.addAttribute("revisi", soal != null && soal.isPublished());
        model.addAttribute("basePath", BASE_PATH);
    }

    /** Satu baris Paket setelah keadaan terbitnya berubah; HTMX menukar baris itu saja (TC-14). */
    private String barisPaket(PaketEntity paket, Model model) {
        model.addAttribute("paket", paket);
        model.addAttribute("jumlahSoal", jumlahSoalMaster());
        // Satu baris saja yang ditukar (TC-14), tapi kolom Subject-nya tetap butuh nama: peta
        // satu-entri, bukan memuat ulang seluruh daftar Subject demi satu baris.
        model.addAttribute("namaSubject",
                Map.of(paket.getSubjectId(), taxonomy.requireGlobalSubject(paket.getSubjectId()).getName()));
        isiJalur(model);
        return "bank/paket :: barisPaket";
    }

    /** Nama Subject per id, untuk kolom Subject tabel Paket — {@code bank/paket.html} tidak menyimpan relasi. */
    private static Map<UUID, String> namaSubject(List<SubjectEntity> subjects) {
        Map<UUID, String> nama = new HashMap<>();
        subjects.forEach(s -> nama.put(s.getId(), s.getName()));
        return nama;
    }

    /** Satu baris Question setelah keadaan terbitnya berubah; sama alasan dengan {@link #barisPaket}. */
    private String barisSoal(QuestionEntity soal, Model model) {
        model.addAttribute("q", soal);
        model.addAttribute("nomor", nomorSoal(soal));
        isiJalur(model);
        return "bank/isi :: barisSoal";
    }

    /**
     * Nomor tampilan: indeks Question ini di antara saudara hidupnya dalam Topic yang sama,
     * padat walaupun {@code position} tersimpan berlubang setelah sebuah soal dihapus
     * (temuan review Task 10) — {@code nextPosition} hanya menambah dari maksimum, tidak pernah
     * merapatkan kembali. Dipakai saat merender satu baris sendirian lewat swap HTMX, karena di
     * situ tidak ada {@code ${it.index}} perulangan untuk disandarkan.
     */
    private int nomorSoal(QuestionEntity soal) {
        Placement tempat = questions.requirePlacement(soal.getId());
        List<QuestionEntity> saudara = questionRepository.findByVersionAndTopicOrdered(
                MASTER, tempat.getVersionId(), tempat.getTopicId());
        for (int i = 0; i < saudara.size(); i++) {
            if (saudara.get(i).getId().equals(soal.getId())) {
                return i + 1;
            }
        }
        return 1;
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
    }

    /**
     * Menyaring daftar Paket berdasarkan potongan judul. Penyaringnya di sini, bukan di query:
     * satu ruang kerja hanya punya puluhan Paket dan daftarnya memang sudah ditarik utuh untuk
     * halaman ini, jadi menambah kombinasi query baru tidak membayar dirinya sendiri. Pindahkan
     * ke repository begitu jumlah Paket menuntut paginasi.
     */
    private static List<PaketEntity> saringJudul(List<PaketEntity> pakets, String cari) {
        if (cari == null || cari.isBlank()) {
            return pakets;
        }
        String kunci = cari.trim().toLowerCase(Locale.ROOT);
        return pakets.stream()
                .filter(p -> p.getTitle() != null && p.getTitle().toLowerCase(Locale.ROOT).contains(kunci))
                .toList();
    }

}
