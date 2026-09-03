package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository.Placement;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketBorrowService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
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
 * Bank Soal: daftar Paket › isi Paket (ADR-0018).
 *
 * <p>Dua tingkat. Tingkat pertama menyambut dengan tabel Paket lintas Subject sekaligus formulir
 * buat Paket — bukan tabel Subject yang dulu jadi jalan buntu tanpa cara membuat apa pun; Subject
 * sekarang hanya penyaring lewat {@code subjectId} (revisi tingkat pertama pasca-ADR-0018).
 * Tidak ada dropdown bertingkat: tiap tingkat punya URL sendiri, sehingga hasilnya bisa disalin
 * dan tombol kembali peramban bekerja.
 *
 * <p>Setiap pembacaan lewat {@link PaketService#require} atau query ber-{@code clientId},
 * sehingga milik Client lain dijawab 404, bukan 403 (TC-36, TC-09). Itu juga syarat memakai
 * {@code questionIdsIn}/{@code borrowedSourceIds}, yang sengaja tidak memeriksa kepemilikan
 * maupun soft delete Paket sendiri.
 */
@Controller
public class BankSoalController {

    private static final int UKURAN_HALAMAN = 20;

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

    /**
     * Tingkat 1: tabel Paket milik Client, seluruh Subject sekaligus formulir buat Paket. Tersaring
     * ke satu Subject bila {@code subjectId} terisi — Subject di sini penyaring, bukan tingkat
     * navigasi sendiri.
     */
    @GetMapping("/bank-soal")
    public String index(@RequestParam(required = false) UUID subjectId,
                        @RequestParam(required = false) String cariPaket,
                        @AuthenticationPrincipal UserPrincipal user,
                        Model model) {
        UUID clientId = user.requireClientId();
        List<SubjectEntity> subjects = taxonomy.visibleSubjects(clientId);
        model.addAttribute("subjects", subjects);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("cariPaket", cariPaket);
        model.addAttribute("pakets", saringJudul(subjectId == null
                ? paketRepository.findByClientIdOrderByTitleAsc(clientId)
                : paketRepository.findByClientIdAndSubjectIdOrderByTitleAsc(clientId, subjectId), cariPaket));
        if (subjectId != null) {
            model.addAttribute("subject", taxonomy.requireVisibleSubject(subjectId, clientId));
        }
        Map<UUID, Long> jumlahSoal = new HashMap<>();
        questionRepository.countByPaket(clientId)
                .forEach(c -> jumlahSoal.put(c.getPaketId(), c.getJumlah()));
        model.addAttribute("jumlahSoal", jumlahSoal);
        model.addAttribute("namaSubject", namaSubject(subjects));
        return "bank/paket";
    }

    /**
     * Pencarian lintas-Paket (FR-019, TC-25): sejajar {@code MasterContentController#cari}, tanpa
     * penyaring {@code status} karena Question milik Client tidak punya keadaan terbit/draf
     * (FR-066 hanya berlaku konten master). {@code subjectId} sekadar mempersempit pilihan Topic
     * di formulir — penyaring sungguhan di query cuma {@code topicId}, sama seperti jalur
     * {@code GET /soal} lama sebelum dicabut (Task 14).
     */
    @GetMapping("/bank-soal/cari")
    public String cari(@RequestParam(required = false) UUID subjectId,
                       @RequestParam(required = false) UUID topicId,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserPrincipal user,
                       Model model) {
        UUID clientId = user.requireClientId();
        model.addAttribute("hasil", questions.searchForBuilder(
                clientId, null, null, topicId, null, List.of(), q, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("q", q);
        model.addAttribute("status", null);
        model.addAttribute("subjects", taxonomy.visibleSubjects(clientId));
        model.addAttribute("topics", subjectId != null ? taxonomy.topicsOwnedBy(subjectId, clientId) : List.of());
        return "bank/cari";
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

    /** Tingkat 2: isi Paket, soal dikelompokkan per Topic. */
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
    public String soalBaru(@PathVariable UUID id,
                           @RequestParam(required = false) UUID topicId,
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
                             @RequestParam String topicTitle,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @RequestParam(required = false) String lanjut,
                             @AuthenticationPrincipal UserPrincipal user) {
        UUID topicId = pakets.resolveTopic(id, topicTitle, user.requireClientId()).getId();
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
        Placement penempatan = questions.requirePlacement(id);
        // require pada Paket induk: soal di bawah Paket yang sudah dihapus lunak ikut 404.
        PaketEntity paket = pakets.require(penempatan.getPaketId(), clientId);
        isiEditor(paket, soal, penempatan.getTopicId(), model);
        return "soal/editor";
    }

    /**
     * Pratinjau soal sebagaimana Siswa melihatnya, dibalas sebagai fragmen panel untuk ditumpuk
     * di atas halaman isi Paket — memeriksa pilihan dan kuncinya tidak perlu membuka editor.
     */
    @GetMapping("/bank-soal/soal/{id}/pratinjau")
    public String pratinjauSoal(@PathVariable UUID id,
                                @AuthenticationPrincipal UserPrincipal user, Model model) {
        QuestionEntity soal = questions.require(id, user.requireClientId());
        model.addAttribute("soal", soal);
        model.addAttribute("opsi", questions.optionsOf(soal.getId()));
        return "soal/editor :: pratinjauPanel";
    }

    /** Perubahan dibalas fragmen detail di tempat, bukan halaman penuh (TC-14). */
    @PutMapping("/bank-soal/soal/{id}")
    public String updateSoal(@PathVariable UUID id,
                             @RequestParam String topicTitle,
                             @RequestParam QuestionType type,
                             @RequestParam String bodyHtml,
                             @RequestParam(required = false) String explanationHtml,
                             @RequestParam(required = false) List<String> optionBody,
                             @RequestParam(defaultValue = "-1") int correctIndex,
                             @AuthenticationPrincipal UserPrincipal user, Model model) {
        UUID clientId = user.requireClientId();
        questions.require(id, clientId);
        PaketEntity paket = pakets.require(questions.requirePlacement(id).getPaketId(), clientId);
        UUID topicId = pakets.resolveTopic(paket.getId(), topicTitle, user.requireClientId()).getId();
        QuestionEntity soal = questions.update(id,
                QuestionService.draftOf(topicId, type, bodyHtml, explanationHtml,
                        optionBody, correctIndex),
                clientId, paket.getId());
        isiEditor(paket, soal, topicId, model);
        return "soal/editor :: detail";
    }

    /**
     * Soft delete (FR-018, BR-Q04): Question hilang dari pencarian bank soal begitu saja, tapi
     * Exercise/Assignment/pengerjaan yang sudah memakainya tetap utuh — {@code softDelete} yang
     * sama persis dipakai {@code MasterContentController#hapusSoal}, cuma {@code clientId}-nya
     * bukan {@code null}.
     */
    /** Kembaran {@link MasterContentController#massalSoal} sisi Client: hanya Hapus (AC-B26). */
    @PostMapping("/bank-soal/paket/{id}/soal/massal")
    public String massalSoal(@PathVariable UUID id,
                             @RequestParam String aksi,
                             @RequestParam(required = false) List<UUID> questionIds,
                             @AuthenticationPrincipal UserPrincipal user) {
        UUID clientId = user.requireClientId();
        pakets.require(id, clientId);
        if (!"hapus".equals(aksi)) {
            throw new IllegalArgumentException("Aksi massal tidak dikenal: " + aksi);
        }
        for (UUID qid : questionIds == null ? List.<UUID>of() : questionIds) {
            questions.softDelete(qid, clientId);
        }
        return "redirect:/bank-soal/paket/" + id;
    }

    @DeleteMapping("/bank-soal/soal/{id}")
    public String hapusSoal(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user, Model model) {
        questions.softDelete(id, user.requireClientId());
        model.addAttribute("pesan", "Soal dihapus dari bank soal.");
        return "bank/isi :: konfirmasiHapus";
    }

    /**
     * Data panel pinjam sebagai JSON (ADR-0019), dimuat dan dirender ulang Alpine di klien
     * ({@code pinjamPanel} di {@code bank/isi.html}) setiap penyaring berubah — bukan lagi
     * fragmen HTMX. Alasan perubahannya: pengguna mencentang soal lintas Subject/Paket sebelum
     * menyalin, dan keadaan centangan itu harus bertahan melewati setiap perubahan penyaring;
     * fragmen menukar HTML yang memuat centangan itu sendiri, JSON tidak (lihat ADR-0019).
     *
     * <p>Tanpa satu pun penyaring terisi, {@code soal} SUDAH berisi lintas Paket dan Subject
     * (AC-B19) — itulah cacat asal yang diperbaiki: Paket baru di Subject yang belum punya Paket
     * lain dulu tidak menawarkan sumber sama sekali karena daftar sumbernya dipersempit ke
     * Subject yang sama lebih dulu.
     *
     * <p>Soal yang salinannya sudah ada di Paket tujuan (AC-B04) maupun yang memang milik Paket
     * tujuan sendiri (AC-B20) sama-sama tidak dirender — keduanya masuk {@code excludeIds} query
     * utama ({@code searchForBuilder}), bukan disaring belakangan di kode pemanggil (TC-36):
     * panel perakit Exercise sudah memakai mekanisme pengecualian ini untuk soal yang sudah
     * terpasang, di sini isinya cuma alasan yang beda.
     *
     * <p>{@code pakets}/{@code topics} balasan ini sudah disempitkan Subject→Paket→Topic di
     * server (aturan 7 ADR-0019): klien tidak pernah menawarkan Paket yang pasti nol hasil untuk
     * Subject yang sedang dipilih. {@code Paket} milik Client lain menjawab 404 lewat
     * {@link PaketService#require} sebelum satu baris pun dirakit (TC-36, TC-09) — galat itu
     * ditangani {@code GlobalExceptionAdvice} yang sama dengan seluruh rute lain, bukan jalur
     * baru (ADR-0019 pagar 2 dan 3).
     */
    @GetMapping("/bank-soal/paket/{id}/pinjam")
    @ResponseBody
    public PinjamPanelData panelPinjamData(@PathVariable UUID id,
                                           @RequestParam(required = false) UUID filterSubjectId,
                                           @RequestParam(required = false) UUID filterPaketId,
                                           @RequestParam(required = false) UUID filterTopicId,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @AuthenticationPrincipal UserPrincipal user) {
        UUID clientId = user.requireClientId();
        pakets.require(id, clientId);

        List<PaketEntity> paketLain = paketRepository.findByClientIdOrderByTitleAsc(clientId).stream()
                .filter(p -> !p.getId().equals(id))
                .toList();
        Map<UUID, PaketEntity> paketById = paketLain.stream()
                .collect(Collectors.toMap(PaketEntity::getId, p -> p));
        // AC-B21: Subject yang ditawarkan hanya yang benar-benar punya Paket sumber — bukan
        // seluruh Subject GLOBAL/Client yang kebetulan terlihat. visibleSubjects() tanpa
        // penyempitan ini akan menawarkan Subject yang pasti berujung "Semua Paket" kosong begitu
        // dipilih, persis pelanggaran yang AC-B21 larang.
        Set<UUID> subjectIdDenganPaket = paketLain.stream().map(PaketEntity::getSubjectId).collect(Collectors.toSet());
        List<SubjectEntity> subjects = taxonomy.visibleSubjects(clientId).stream()
                .filter(s -> subjectIdDenganPaket.contains(s.getId()))
                .toList();
        Map<UUID, String> namaSubject = namaSubject(subjects);
        List<PaketEntity> paketPilihan = filterSubjectId == null ? paketLain
                : paketLain.stream().filter(p -> p.getSubjectId().equals(filterSubjectId)).toList();
        // Dropdown Topic mengikuti Paket yang sedang dipilih saja: seluruh Topic lintas-Paket
        // Client bisa jadi ratusan baris tanpa struktur yang berguna untuk dipilih, sedangkan
        // Topic satu Paket sudah tersedia lewat method yang sama dengan halaman isi Paket
        // (PaketService.topicsOf) — bukan query baru. require() DULU wajib (TC-36, TC-09):
        // TopicRepository.findByPaketIdOrderByPositionAsc cuma join ke Paket untuk menghormati
        // deleted_at, tidak menyaring clientId sama sekali — tanpa require ini, filterPaketId
        // milik Client lain membalas judul Topic-nya begitu saja (200 berisi, bukan 404), dan itu
        // sekaligus oracle keberadaan: id asing membalas tidak kosong, id yang tidak ada membalas
        // kosong, keduanya 200 — persis yang TC-09 larang bisa dibedakan.
        List<TopicEntity> filterTopics = filterPaketId != null
                ? pakets.topicsOf(pakets.require(filterPaketId, clientId).getId())
                : List.of();

        Set<UUID> dikecualikan = new HashSet<>(borrow.borrowedSourceIds(id));
        dikecualikan.addAll(questions.questionIdsIn(id));
        Page<QuestionEntity> hasil = questions.searchForBuilder(clientId, filterSubjectId, filterPaketId,
                filterTopicId, null, dikecualikan, q, PageRequest.of(page, UKURAN_HALAMAN));

        Map<UUID, Placement> penempatan = questions.placementsOf(
                hasil.getContent().stream().map(QuestionEntity::getId).toList());
        Map<UUID, String> judulTopic = new HashMap<>();
        pakets.topicsByIds(penempatan.values().stream().map(Placement::getTopicId).collect(Collectors.toSet()))
                .forEach(t -> judulTopic.put(t.getId(), t.getTitle()));

        return pinjamPanelData(subjects, namaSubject, paketPilihan, paketById, filterTopics, hasil,
                penempatan, judulTopic);
    }

    /**
     * Merakit {@link PinjamPanelData} dari entitas yang sudah dibaca controller — satu tempat,
     * dipakai {@link #panelPinjamData} maupun {@code MasterContentController#panelPinjamData},
     * supaya bentuk JSON kedua sisi benar-benar identik, bukan cuma sengaja disamakan tangan.
     */
    static PinjamPanelData pinjamPanelData(List<SubjectEntity> subjects, Map<UUID, String> namaSubject,
                                           List<PaketEntity> paketPilihan, Map<UUID, PaketEntity> paketById,
                                           List<TopicEntity> filterTopics, Page<QuestionEntity> hasil,
                                           Map<UUID, Placement> penempatan, Map<UUID, String> judulTopic) {
        List<PinjamPanelData.SoalRow> baris = hasil.getContent().stream().map(s -> {
            Placement tempat = penempatan.get(s.getId());
            UUID paketId = tempat != null ? tempat.getPaketId() : null;
            UUID topicId = tempat != null ? tempat.getTopicId() : null;
            PaketEntity p = paketId != null ? paketById.get(paketId) : null;
            return new PinjamPanelData.SoalRow(s.getId(), abbreviate(s.getBodyText()),
                    s.getType() == QuestionType.ESSAY ? "Esai" : "Pilihan Ganda",
                    paketId, p != null ? p.getTitle() : null,
                    p != null ? p.getSubjectId() : null, p != null ? namaSubject.get(p.getSubjectId()) : null,
                    topicId, judulTopic.get(topicId));
        }).toList();
        return new PinjamPanelData(
                subjects.stream().map(s -> new PinjamPanelData.Opsi(s.getId(), s.getName())).toList(),
                paketPilihan.stream().map(p -> new PinjamPanelData.Opsi(p.getId(), p.getTitle())).toList(),
                filterTopics.stream().map(t -> new PinjamPanelData.Opsi(t.getId(), t.getTitle())).toList(),
                new PinjamPanelData.HasilSoal(baris, hasil.getNumber(), hasil.getTotalPages(),
                        hasil.getTotalElements(), hasil.hasPrevious(), hasil.hasNext()));
    }

    /**
     * Cuplikan isi soal untuk tabel panel pinjam — padanan {@code #strings.abbreviate(text, 110)}
     * Thymeleaf, ditulis ulang di Java karena balasan ini JSON, bukan HTML yang dirender Thymeleaf.
     */
    static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 110 ? text : text.substring(0, 107) + "...";
    }

    /**
     * Topic tujuan datang sebagai NAMA, bukan id: kolomnya di panel pinjam menerima Topic yang
     * sudah ada di Paket ini maupun nama yang belum ada, sama seperti kolom Topic di editor soal.
     * {@link PaketService#resolveTopic} yang memutuskan menempel atau membuat.
     *
     * <p>Tidak ada yang disalin berarti tidak ada Topic yang dibuat: submit kosong (mis. Enter di
     * salah satu penyaring) tidak boleh meninggalkan Topic kosong di Paket ini sebagai efek
     * samping.
     */
    @PostMapping("/bank-soal/paket/{id}/pinjam")
    public String pinjam(@PathVariable UUID id,
                         @RequestParam String topicTitle,
                         @RequestParam(required = false) List<UUID> questionIds,
                         @RequestParam(required = false) UUID sourceTopicId,
                         @AuthenticationPrincipal UserPrincipal user) {
        UUID clientId = user.requireClientId();
        if (sourceTopicId == null && (questionIds == null || questionIds.isEmpty())) {
            return "redirect:/bank-soal/paket/" + id;
        }
        UUID topicId = pakets.resolveTopic(id, topicTitle, clientId).getId();
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
        List<TopicEntity> daftarTopic = pakets.topicsOf(paket.getId());
        model.addAttribute("topics", daftarTopic);
        // Editor menyunting JUDUL Topic, bukan id-nya: satu kolom ber-datalist yang sekaligus
        // memilih yang sudah ada dan membuat yang belum (PaketService#resolveTopic).
        model.addAttribute("topicTitle", daftarTopic.stream()
                .filter(t -> t.getId().equals(topicId))
                .map(TopicEntity::getTitle)
                .findFirst()
                .orElse(""));
        model.addAttribute("basePath", "/bank-soal");
    }

    /** Nama Subject per id, untuk kolom Subject tabel Paket — {@code bank/paket.html} tidak menyimpan relasi. */
    private static Map<UUID, String> namaSubject(List<SubjectEntity> subjects) {
        Map<UUID, String> nama = new HashMap<>();
        subjects.forEach(s -> nama.put(s.getId(), s.getName()));
        return nama;
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
