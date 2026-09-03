package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.StatusTerbit;
import com.eduscreen.app.modules.assessment.repository.PaketItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository.Placement;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bank soal: pembuatan, pengubahan, pencarian, dan penghapusan lunak Question beserta
 * Option-nya, serta penempatannya di versi kerja Paket ({@link PaketItemEntity}, ADR-0021).
 */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final PaketItemRepository items;
    private final PaketService pakets;
    private final PaketAccessService access;
    private final TaxonomyService taxonomy;
    private final ContentSanitizer sanitizer;
    private final MasterPublishingService publishing;
    private final ClientClock clock;

    public QuestionService(QuestionRepository questions,
                           QuestionOptionRepository options,
                           PaketItemRepository items,
                           PaketService pakets,
                           PaketAccessService access,
                           TaxonomyService taxonomy,
                           ContentSanitizer sanitizer,
                           MasterPublishingService publishing,
                           ClientClock clock) {
        this.questions = questions;
        this.options = options;
        this.items = items;
        this.pakets = pakets;
        this.access = access;
        this.taxonomy = taxonomy;
        this.sanitizer = sanitizer;
        this.publishing = publishing;
        this.clock = clock;
    }

    /** Satu pilihan jawaban mentah dari editor, sebelum disanitasi. */
    public record OptionDraft(String bodyHtml, boolean correct) {
    }

    /** Isi Question mentah dari editor, sebelum disanitasi (dipakai create dan update). */
    public record QuestionDraft(UUID topicId, QuestionType type, String bodyHtml,
                                 String explanationHtml, List<OptionDraft> options) {
    }

    /**
     * Pencarian bank soal Client, ditambah saringan Paket, tipe soal, dan pengecualian soal
     * yang sudah terpasang di Exercise yang sedang dirakit. Dipanggil dengan {@code paketId}
     * null dan {@code excluded} kosong dari luar konteks perakit Exercise.
     *
     * <p>{@code paketId} null berarti tidak difilter Paket sama sekali — perakit boleh menelusuri
     * lintas Paket dan Topic mana pun di dalam Client (BR-E01, FR-024); saringan ini hanya
     * mempersempit tampilan panel, bukan membatasi soal apa yang boleh masuk Exercise.
     *
     * <p>{@code excluded} kosong diganti UUID nil, bukan dibelokkan ke query lain: {@code not in ()}
     * tidak sah, sedangkan UUIDv7 tidak pernah nol sehingga sentinel itu tidak menyaring apa pun.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchForBuilder(UUID clientId, UUID subjectId, UUID paketId, UUID topicId,
                                                 QuestionType type, Collection<UUID> excluded, String q,
                                                 Pageable pageable) {
        return questions.searchForBuilder(clientId, access.visibleVersionIds(clientId), subjectId, paketId,
                topicId, type, excludeOrSentinel(excluded), ExerciseService.likePattern(q), pageable);
    }

    /**
     * Ruang kerja Eduscreen Admin. {@code status} null berarti draf dan terbit ditampilkan
     * berdampingan — keadaan bawaan ruang kerja, yang memang harus melihat keduanya.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchMaster(UUID subjectId, UUID topicId, String q,
                                             StatusTerbit status, Pageable pageable) {
        String pattern = ExerciseService.likePattern(q);
        Collection<UUID> tanpaKecuali = excludeOrSentinel(null);
        return switch (status) {
            case null -> questions.searchMaster(subjectId, null, topicId, tanpaKecuali, pattern, pageable);
            case DRAF -> questions.searchUnpublishedMaster(subjectId, topicId, pattern, pageable);
            case TERBIT -> questions.searchPublishedMaster(subjectId, topicId, pattern, pageable);
        };
    }

    /**
     * Panel pinjam ruang kerja master ({@code MasterContentController#panelPinjam}): padanan
     * {@link #searchForBuilder} untuk Paket ber-{@code clientId} null.
     *
     * <p>Status terbit/draf sengaja TIDAK disaring di sini: sumber pinjam boleh berupa Paket
     * master yang masih draf. Keadaan terbit baru menentukan sesuatu saat Paket TUJUAN hendak
     * terbit (AC-B12), bukan saat isinya sekadar disalin dari Paket master lain.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchMasterBorrowable(UUID subjectId, UUID paketId, UUID topicId,
                                                        Collection<UUID> excluded, String q, Pageable pageable) {
        return questions.searchMaster(subjectId, paketId, topicId, excludeOrSentinel(excluded),
                ExerciseService.likePattern(q), pageable);
    }

    /**
     * {@code not in ()} bukan SQL yang sah, jadi daftar kecuali yang kosong diganti UUID nil
     * sentinel — UUIDv7 tidak pernah bernilai nol (ADR-0009), jadi ia tidak menyaring apa pun.
     */
    private static Collection<UUID> excludeOrSentinel(Collection<UUID> excluded) {
        return excluded == null || excluded.isEmpty() ? List.of(new UUID(0L, 0L)) : excluded;
    }

    /**
     * Soal versi kerja satu Paket dikelompokkan per Topic, terurut posisi item — halaman isi
     * Paket. Pemanggil wajib sudah lolos {@code PaketService.require} untuk Paket ini (TC-36).
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<QuestionEntity>> groupByTopic(UUID paketId) {
        List<PaketItemEntity> penempatan = items.findByVersionOrdered(pakets.versionOf(paketId).getId());
        Map<UUID, QuestionEntity> byId = questions.findAllById(
                        penempatan.stream().map(PaketItemEntity::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
        Map<UUID, List<QuestionEntity>> perTopic = new LinkedHashMap<>();
        for (PaketItemEntity item : penempatan) {
            QuestionEntity soal = byId.get(item.getQuestionId());
            if (soal != null) {
                perTopic.computeIfAbsent(item.getTopicId(), k -> new ArrayList<>()).add(soal);
            }
        }
        return perTopic;
    }

    /**
     * Isi satu versi sebagaimana terlihat Client, dikelompokkan per Topic: soal miliknya apa
     * adanya, soal master hanya yang terbit (ADR-0021). Untuk halaman Paket Eduscreen di Bank
     * Soal sekolah; versinya sudah dipastikan terlihat lewat {@code PaketAccessService}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<QuestionEntity>> groupByTopicReadable(UUID clientId, UUID versionId) {
        Map<UUID, QuestionEntity> byId = questions.findAccessibleInVersion(clientId, versionId).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, List<QuestionEntity>> perTopic = new LinkedHashMap<>();
        for (PaketItemEntity item : items.findByVersionOrdered(versionId)) {
            QuestionEntity soal = byId.get(item.getQuestionId());
            if (soal != null) {
                perTopic.computeIfAbsent(item.getTopicId(), k -> new ArrayList<>()).add(soal);
            }
        }
        return perTopic;
    }

    /**
     * Soal yang sudah terpasang di Exercise milik Client, dibaca apa adanya — termasuk soal master
     * yang aksesnya sudah lewat dan soal yang sudah dihapus lunak (FR-068, BR-Q04). Id datang dari
     * {@code exercise_item} Exercise yang sudah lolos {@code ExerciseService.require}, jadi sudah
     * tenant-aman; jangan pakai untuk id yang datang dari luar.
     */
    @Transactional(readOnly = true)
    public List<QuestionEntity> snapshotOf(Collection<UUID> questionIds) {
        return questionIds.isEmpty() ? List.of() : questions.findAllForSnapshot(questionIds);
    }

    /** Seluruh id soal di versi kerja satu Paket — daftar kecuali panel pinjam (AC-B20). */
    @Transactional(readOnly = true)
    public List<UUID> questionIdsIn(UUID paketId) {
        return items.questionIdsOf(pakets.versionOf(paketId).getId());
    }

    /**
     * Penempatan satu soal: Paket, versi, Topic, posisi. Soal tanpa penempatan (sudah dihapus
     * dari versi kerjanya) diperlakukan seolah tidak ada — 404 (TC-09).
     *
     * <p>Satu soal bisa punya lebih dari satu penempatan begitu versi Paket beku lahir (Fase 2);
     * yang dikembalikan di sini adalah penempatan di versi kerja mana pun, cukup untuk editor.
     */
    @Transactional(readOnly = true)
    public Placement requirePlacement(UUID questionId) {
        return requirePlacement(questionId, null);
    }

    /**
     * Penempatan soal di Paket tertentu — wajib bagi jalur tulis soal master, yang sejak
     * ADR-0021 bisa berada di banyak Paket. {@code paketId} null mengambil penempatan mana pun
     * (soal sekolah hanya punya satu).
     */
    @Transactional(readOnly = true)
    public Placement requirePlacement(UUID questionId, UUID paketId) {
        return items.findPlacements(List.of(questionId)).stream()
                .filter(p -> paketId == null || p.getPaketId().equals(paketId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));
    }

    /** Penempatan banyak soal sekaligus, satu query — label Paket/Topic di tabel panel pinjam. */
    @Transactional(readOnly = true)
    public Map<UUID, Placement> placementsOf(Collection<UUID> questionIds) {
        Map<UUID, Placement> perSoal = new HashMap<>();
        if (questionIds.isEmpty()) {
            return perSoal;
        }
        items.findPlacements(questionIds).forEach(p -> perSoal.putIfAbsent(p.getQuestionId(), p));
        return perSoal;
    }

    /**
     * Merakit {@link QuestionDraft} dari parameter form editor; satu tempat, dipakai lebih dari
     * satu controller.
     *
     * <p>Tipe soal yang menentukan Option ikut atau tidak, bukan keberadaan parameternya:
     * editor menyembunyikan kolom pilihan lewat {@code x-show} saat tipenya esai, dan kolom
     * tersembunyi tetap terkirim oleh peramban.
     */
    public static QuestionDraft draftOf(UUID topicId, QuestionType type, String bodyHtml,
                                        String explanationHtml, List<String> optionBody,
                                        int correctIndex) {
        if (type == QuestionType.ESSAY || optionBody == null) {
            return new QuestionDraft(topicId, type, bodyHtml, explanationHtml, List.of());
        }
        List<OptionDraft> opsi = new ArrayList<>();
        for (int i = 0; i < optionBody.size(); i++) {
            opsi.add(new OptionDraft(optionBody.get(i), i == correctIndex));
        }
        return new QuestionDraft(topicId, type, bodyHtml, explanationHtml, opsi);
    }

    /** Soal yang boleh DITULIS pemanggil: miliknya sendiri. Milik lain dan tidak ada sama-sama 404 (TC-09). */
    @Transactional(readOnly = true)
    public QuestionEntity require(UUID id, UUID clientId) {
        return questions.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));
    }

    /**
     * Soal yang boleh DIBACA Client: miliknya, atau soal master lewat akses Paket (ADR-0021).
     * Untuk perakit Exercise dan pratinjau — bukan untuk editor.
     */
    @Transactional(readOnly = true)
    public QuestionEntity requireReadable(UUID id, UUID clientId) {
        return questions.findAccessibleById(id, clientId, access.visibleVersionIds(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public List<QuestionOptionEntity> optionsOf(UUID questionId) {
        return options.findByQuestionIdOrderByPositionAsc(questionId);
    }

    /**
     * Menulis soal ke versi kerja satu Paket.
     *
     * <p>Pemilik ({@code clientId}) datang sebagai parameter, bukan disimpulkan dari principal:
     * null berarti konten master milik Eduscreen (FR-060). Controller-lah yang memutuskan
     * nilainya — bank soal Client mengirim {@code requireClientId()}, ruang kerja master mengirim
     * null — sehingga kepemilikan terlihat di tanda tangan, sejalan dengan TC-36.
     *
     * <p>{@code paketId} adalah Paket TUJUAN, dan Topic yang ditunjuk draft wajib berada di dalam
     * Paket yang sama (AC-B02). Tanpa pemeriksaan ini, Topic dari Paket lain milik Client yang
     * sama bisa lolos — soal itu lalu tersimpan sewadah dengan Topic-nya tapi bukan di Paket
     * tempat penulis sedang bekerja, dan urutannya di dalam Paket kehilangan makna.
     */
    @Transactional
    public QuestionEntity create(QuestionDraft draft, UUID clientId, UUID paketId) {
        TopicEntity topic = requireTopicOf(draft.topicId(), paketId, clientId);
        PaketVersionEntity version = pakets.draftOf(paketId);

        String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
        if (bodyHtml.isBlank()) {
            throw new IllegalArgumentException("Isi soal tidak boleh kosong");
        }
        validateOptions(draft.type(), draft.options());

        QuestionEntity question = new QuestionEntity(clientId, draft.type(), bodyHtml, sanitizer.toPlainText(bodyHtml));
        applyExplanation(question, draft.explanationHtml());
        question = questions.save(question);
        // Soal baru mendarat di ekor Topic-nya. Tanpa ini setiap soal lahir di posisi 0 dan
        // urutan yang dilihat penulis ditentukan kebetulan.
        items.save(new PaketItemEntity(version, topic, question, items.nextPosition(version.getId(), topic.getId())));

        saveOptions(question.getId(), draft.options());
        return question;
    }

    /**
     * Mengubah soal yang sudah ada; validasi Paket/Topic sama seperti {@link #create} (AC-B02).
     *
     * <p>Memindahkan soal ke Topic lain menghitung ulang posisi itemnya (AC-B08): tanpa itu soal
     * mendarat membawa posisi lamanya dan bertabrakan dengan soal yang sudah menempati posisi
     * itu di Topic tujuan. Posisinya dibaca SEBELUM item dipindah: query {@code nextPosition}
     * memicu flush otomatis Hibernate, dan item yang sudah dipindahkan lebih dulu akan ikut
     * terhitung sebagai penghuni Topic tujuan sehingga posisinya melompat satu.
     */
    @Transactional
    public QuestionEntity update(UUID id, QuestionDraft draft, UUID clientId, UUID paketId) {
        QuestionEntity question = require(id, clientId);
        // Soal master terbit beku: teksnya sedang dibaca versi terbit, Exercise, dan sesi sekolah
        // (ADR-0021). Jalannya lewat revise(), bukan menimpa di tempat.
        if (clientId == null && question.isPublished()) {
            throw new QuestionFrozenException();
        }
        TopicEntity topic = requireTopicOf(draft.topicId(), paketId, clientId);
        PaketVersionEntity version = pakets.draftOf(paketId);
        // Soal yang tidak ada di versi kerja Paket ini — sudah dibuang dari sana, atau memang
        // milik Paket lain — diperlakukan seolah tidak ada (TC-09).
        PaketItemEntity item = items.findByPaketVersionIdAndQuestionId(version.getId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
        if (bodyHtml.isBlank()) {
            throw new IllegalArgumentException("Isi soal tidak boleh kosong");
        }
        validateOptions(draft.type(), draft.options());

        if (!topic.getId().equals(item.getTopicId())) {
            int posisi = items.nextPosition(version.getId(), topic.getId());
            item.moveToTopic(topic);
            item.moveTo(posisi);
            items.save(item);
        }
        // Salinan pinjam yang disunting bukan kembaran asalnya lagi: asalnya boleh dipinjam ulang
        // (AC-B04). Setiap simpan lewat editor dihitung suntingan, tanpa membandingkan isi lama.
        question.setSourceQuestionId(null);
        // Sanitasi dan turunan teks polos ditulis dalam operasi yang sama dengan bodyHtml,
        // supaya keduanya tidak pernah sempat tidak sinkron (TC-25).
        question.setBodyHtml(bodyHtml);
        question.setBodyText(sanitizer.toPlainText(bodyHtml));
        applyExplanation(question, draft.explanationHtml());
        question = questions.save(question);

        // Seluruh Option lama diganti daripada dicocokkan satu per satu: editor selalu
        // mengirim daftar penuh, dan penomoran ulang posisi jadi otomatis benar.
        options.deleteByQuestionId(question.getId());
        // flush() wajib di antara hapus dan sisip. Hibernate mengurutkan seluruh INSERT sebelum
        // seluruh DELETE dalam satu flush, sehingga indeks parsial question_option_single_correct
        // sempat melihat dua Option benar untuk soal yang sama dan menolak pembaruan apa pun atas
        // soal pilihan ganda. Memaksa hapusnya mendarat lebih dulu memulihkan urutan yang
        // sebenarnya diniatkan kode ini.
        options.flush();
        saveOptions(question.getId(), draft.options());
        return question;
    }

    /**
     * Revisi soal master terbit (ADR-0021): baris {@code question} baru berisi draft, menggantikan
     * baris lama di versi kerja Paket ini — Topic dan posisi yang sama — lalu baris lama ditandai
     * {@code supersededById}. Baris lama tidak disentuh isinya: versi terbit, Exercise, dan sesi
     * yang menunjuknya tetap membaca teks yang sama.
     *
     * <p>Revisi lahir sebagai draf (belum terbit): ia baru terlihat sekolah setelah diterbitkan
     * dan versi kerjanya dibekukan, sama seperti soal baru mana pun (ADR-0020).
     */
    @Transactional
    public QuestionEntity revise(UUID id, QuestionDraft draft, UUID paketId) {
        QuestionEntity lama = questions.findByIdAndClientIdIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soal master tidak ditemukan"));
        if (!lama.isPublished()) {
            throw new IllegalArgumentException("Soal yang belum terbit diubah langsung, bukan direvisi");
        }
        // Rantai riwayat satu arah: soal yang sudah digantikan tidak direvisi lagi dari tempat
        // lain, supaya supersededById tidak ditimpa dan riwayat tetap bisa ditelusuri.
        if (lama.isSuperseded()) {
            throw new IllegalStateException("Soal ini sudah digantikan revisi. Sunting revisinya, bukan baris lama.");
        }
        TopicEntity topic = requireTopicOf(draft.topicId(), paketId, null);
        PaketVersionEntity version = pakets.draftOf(paketId);
        PaketItemEntity item = items.findByPaketVersionIdAndQuestionId(version.getId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Soal tidak ditemukan"));

        String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
        if (bodyHtml.isBlank()) {
            throw new IllegalArgumentException("Isi soal tidak boleh kosong");
        }
        validateOptions(draft.type(), draft.options());

        QuestionEntity baru = new QuestionEntity(null, draft.type(), bodyHtml, sanitizer.toPlainText(bodyHtml));
        applyExplanation(baru, draft.explanationHtml());
        baru.setCreatedBy(lama.getCreatedBy());
        baru = questions.save(baru);
        saveOptions(baru.getId(), draft.options());

        if (!topic.getId().equals(item.getTopicId())) {
            int posisi = items.nextPosition(version.getId(), topic.getId());
            item.moveToTopic(topic);
            item.moveTo(posisi);
        }
        item.replaceQuestion(baru);
        items.save(item);

        lama.supersede(baru.getId());
        questions.save(lama);
        return baru;
    }

    /**
     * Topic yang boleh ditulisi DAN sewadah dengan Paket tujuan (AC-B02).
     *
     * <p>{@link TaxonomyService#requireWritableTopic} sudah menjamin kepemilikannya: Topic
     * berada di dalam Paket master untuk konten Eduscreen, atau di dalam Paket milik Client ini
     * (ADR-0018) — selain itu diperlakukan seolah tidak ada (TC-09). Yang belum ditegakkannya
     * adalah Topic itu berada di Paket yang SAMA dengan {@code paketId}: satu Client bisa punya
     * banyak Paket, dan Topic dari Paket lain yang sama-sama miliknya tetap lolos pemeriksaan
     * kepemilikan itu sendiri.
     */
    private TopicEntity requireTopicOf(UUID topicId, UUID paketId, UUID clientId) {
        TopicEntity topic = taxonomy.requireWritableTopic(topicId, clientId);
        if (!topic.getPaketId().equals(paketId)) {
            throw new IllegalArgumentException("Topic bukan milik Paket ini");
        }
        return topic;
    }

    /**
     * Menghapus soal dari bank soal (FR-018, TC-35).
     *
     * <p>Soal master yang ada di sebuah versi TERBIT tidak dihapus lunak: versi terbit beku, dan
     * {@code @SQLRestriction} akan menyembunyikan barisnya dari versi itu (AC-B17, ADR-0021).
     * Yang dibuang hanya penempatannya di versi kerja — soal hilang dari versi berikutnya, tidak
     * dari versi yang sudah dibaca sekolah. Paket tanpa versi kerja menuntut pilihan lebih dulu
     * ({@link NeedsVersionChoiceException}), bukan diam-diam tidak melakukan apa pun.
     *
     * <p>Selain itu: dihapus lunak dan penempatannya di versi kerja dibuang. Soal hilang dari
     * pencarian bank soal tapi tetap terbaca oleh Exercise dan sesi yang sudah memakainya —
     * itu ditegakkan lewat {@code @SQLRestriction} plus {@code findAllForSnapshot}.
     */
    @Transactional
    public void softDelete(UUID id, UUID clientId) {
        softDelete(id, clientId, null);
    }

    /**
     * Menghapus soal dari SATU Paket: penempatannya di versi kerja Paket itu dibuang (Paket
     * tanpa versi kerja menuntut pilihan dulu, {@link NeedsVersionChoiceException}). Baris
     * soalnya sendiri baru dihapus lunak bila tidak ada lagi penempatan di mana pun — Paket
     * lain yang berbagi soal itu (instance, ADR-0021) dan versi terbit tidak tersentuh.
     *
     * <p>{@code paketId} null hanya untuk soal sekolah (satu Paket): seluruh penempatan di versi
     * kerja dibuang. Soal master wajib menyebut Paketnya.
     */
    @Transactional
    public void softDelete(UUID id, UUID clientId, UUID paketId) {
        QuestionEntity question = require(id, clientId);
        if (paketId != null) {
            pakets.require(paketId, clientId);
            PaketVersionEntity draft = pakets.draftOf(paketId);
            items.findByPaketVersionIdAndQuestionId(draft.getId(), id).ifPresent(items::delete);
        } else if (clientId == null) {
            throw new IllegalArgumentException("Menghapus soal master wajib menyebut Paketnya");
        } else {
            items.deleteDraftItemsOf(id);
        }
        if (items.findPlacements(List.of(id)).isEmpty()) {
            // Soal hilang dari pencarian bank soal tapi tetap terbaca oleh Exercise dan sesi yang
            // sudah memakainya (FR-018) — @SQLRestriction plus findAllForSnapshot.
            question.softDelete(clock.now());
            questions.save(question);
        }
    }

    private void applyExplanation(QuestionEntity question, String explanationHtmlRaw) {
        if (explanationHtmlRaw == null || explanationHtmlRaw.isBlank()) {
            question.setExplanationHtml(null);
            question.setExplanationText(null);
            return;
        }
        String explanationHtml = sanitizer.sanitize(explanationHtmlRaw);
        question.setExplanationHtml(explanationHtml);
        question.setExplanationText(sanitizer.toPlainText(explanationHtml));
    }

    private void saveOptions(UUID questionId, List<OptionDraft> drafts) {
        int position = 0;
        for (OptionDraft draft : drafts) {
            String bodyHtml = sanitizer.sanitize(draft.bodyHtml());
            options.save(new QuestionOptionEntity(
                    questionId, bodyHtml, sanitizer.toPlainText(bodyHtml), draft.correct(), position++));
        }
    }

    /**
     * MULTIPLE_CHOICE butuh minimal 2 Option dan tepat 1 benar (FR-016, AC-Q01); ESSAY tidak
     * boleh punya Option sama sekali karena jawabannya bebas dan dinilai manual.
     */
    private void validateOptions(QuestionType type, List<OptionDraft> drafts) {
        if (type == QuestionType.ESSAY) {
            if (drafts != null && !drafts.isEmpty()) {
                throw new IllegalArgumentException("Soal esai tidak boleh punya pilihan jawaban");
            }
            return;
        }
        if (drafts == null || drafts.size() < 2) {
            throw new IllegalArgumentException("Soal pilihan ganda butuh minimal 2 pilihan jawaban");
        }
        long correctCount = drafts.stream().filter(OptionDraft::correct).count();
        if (correctCount != 1) {
            throw new IllegalArgumentException("Soal pilihan ganda wajib punya tepat 1 pilihan benar");
        }
    }
}
