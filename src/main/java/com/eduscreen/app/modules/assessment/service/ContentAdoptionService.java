package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.PaketVersionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adopsi konten master Eduscreen oleh satu Client: copy-on-adopt (ADR-0001, FR-021), per Paket
 * (ADR-0018, AC-B05).
 *
 * <p>Adopsi membuat <b>salinan penuh</b> — Paket, seluruh Topic, seluruh Question beserta
 * Option — bukan referensi ke baris master. Alasannya bukan teknis melainkan operasional:
 * sekolah yang sudah menyesuaikan soal (mengubah redaksi, memperbaiki kunci jawaban, menambah
 * pembahasan) tidak boleh mendapat perubahan dari Eduscreen di tengah semester begitu master-nya
 * direvisi. Setelah adopsi, salinan Client hidup sendiri — {@code sourcePaketId} dan
 * {@code sourceQuestionId} hanya jejak asal untuk ditelusuri, bukan tautan yang disinkronkan.
 *
 * <p>Subject {@code GLOBAL} sengaja TIDAK pernah disalin (BR-O02, AC-O02): ia dibaca langsung
 * oleh semua Client, sehingga menyalinnya hanya akan menggandakan data tanpa tujuan. Salinan
 * Paket menunjuk {@code subjectId} yang sama persis dengan Paket master-nya.
 *
 * <p>Satu Paket adalah satu-satunya satuan adopsi. Exercise tidak pernah jadi objek adopsi —
 * Exercise milik alur Guru, dirakit dari Question yang sudah ada di bank soal Client-nya sendiri
 * (BR-E01, FR-081), termasuk Question hasil adopsi ini.
 */
@Service
public class ContentAdoptionService {

    private final TopicRepository topics;
    private final PaketRepository pakets;
    private final PaketVersionRepository versions;
    private final PaketItemRepository items;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;

    public ContentAdoptionService(TopicRepository topics,
                                  PaketRepository pakets,
                                  PaketVersionRepository versions,
                                  PaketItemRepository items,
                                  QuestionRepository questions,
                                  QuestionOptionRepository options) {
        this.topics = topics;
        this.pakets = pakets;
        this.versions = versions;
        this.items = items;
        this.questions = questions;
        this.options = options;
    }

    public record AdoptionSummary(int pakets, int topics, int questions) {
    }

    /**
     * Adopsi satu atau beberapa Paket master. Salinan penuh milik Client (ADR-0001, FR-021):
     * perubahan Eduscreen sesudahnya tidak merambat.
     *
     * <p>Subject GLOBAL tidak disalin — salinan Paket menunjuk Subject yang sama (BR-O02).
     *
     * <p>Setiap Paket menyalin seluruh Question di dalam Topic-nya, terlepas dari keadaan terbit
     * Question itu sendiri: gerbang adopsi ada di tingkat Paket (FR-067), bukan di tingkat
     * Question — konsisten dengan Task 8/ADR-0018 yang memindahkan satuan katalog dan adopsi dari
     * Question/Exercise menjadi Paket.
     *
     * <p>Adopsi kedua atas Paket yang sama tetap diizinkan dan melahirkan Paket, Topic, dan
     * Question baru yang terpisah (FR-077 setara) — bukan menolak atau memakai ulang salinan
     * lama. Yang memperingatkan pengulangan itu adalah katalog ({@link #adoptedSourcePaketIds}),
     * bukan gerbang di sini.
     */
    @Transactional
    public AdoptionSummary adoptPakets(UUID clientId, List<UUID> paketIds, UUID actor) {
        int jumlahPaket = 0;
        int jumlahTopic = 0;
        int jumlahQuestion = 0;

        for (UUID id : paketIds) {
            // Hanya Paket master TERBIT yang bisa diadopsi (FR-067). Paket yang masih digarap dan
            // Paket yang tidak ada sama-sama 404, sehingga menebak pengenal tidak membuktikan
            // apa pun (TC-09).
            PaketEntity master = pakets.findPublishedMasterById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Paket master tidak ditemukan"));

            PaketEntity copy = pakets.save(PaketEntity.adoptedFrom(
                    clientId, master.getSubjectId(), master.getTitle(), actor, master.getId()));
            PaketVersionEntity copyVersion = versions.save(PaketVersionEntity.draft(copy, 1, actor));
            PaketVersionEntity masterVersion = versions.findDraft(master.getId())
                    .orElseThrow(() -> new IllegalStateException("Paket master tanpa versi kerja"));
            jumlahPaket++;

            for (TopicEntity topicMaster : topics.findByPaketIdOrderByPositionAsc(master.getId())) {
                TopicEntity topicCopy = topics.save(TopicEntity.of(
                        copy.getId(), topicMaster.getTitle(), topicMaster.getPosition()));
                jumlahTopic++;

                // Hanya soal TERBIT yang ikut menyeberang: sejak ADR-0020 Paket terbit boleh menyisakan
                // draf di dalamnya, dan draf itu pekerjaan yang belum selesai — bukan isi katalog.
                for (PaketItemEntity item : items.findPublishedByVersionAndTopicOrdered(
                        masterVersion.getId(), topicMaster.getId())) {
                    QuestionEntity q = questions.findById(item.getQuestionId()).orElseThrow();
                    salinQuestion(q, item.getPosition(), copy, copyVersion, topicCopy, actor);
                    jumlahQuestion++;
                }
            }
        }
        return new AdoptionSummary(jumlahPaket, jumlahTopic, jumlahQuestion);
    }

    /**
     * Menyalin satu Question beserta seluruh Option-nya, mempertahankan {@code position} milik
     * master alih-alih menghitung ulang lewat {@code nextPosition}.
     *
     * <p>Topic tujuan selalu baru dan kosong (baru saja lahir beberapa baris di atas), jadi kedua
     * pendekatan kebetulan menghasilkan angka yang sama untuk Topic master yang posisinya rapat
     * tanpa celah. Bedanya baru terlihat kalau Topic master punya celah posisi (mis. Question di
     * tengah pernah dihapus lunak): mempertahankan {@code position} master menjaga urutan buku
     * aslinya utuh, sedangkan {@code nextPosition} akan merapatkannya — diam-diam mengubah urutan
     * yang dilihat penulis master. {@link PaketBorrowService#salin} sebaliknya memang memakai
     * {@code nextPosition}, karena Topic tujuan pinjam bisa saja sudah berisi soal lain; konteks
     * itu tidak berlaku di sini.
     */
    private void salinQuestion(QuestionEntity master, int position, PaketEntity paket,
                               PaketVersionEntity version, TopicEntity topic, UUID actor) {
        QuestionEntity copy = new QuestionEntity(
                paket.getClientId(), master.getType(), master.getBodyHtml(), master.getBodyText());
        copy.setExplanationHtml(master.getExplanationHtml());
        copy.setExplanationText(master.getExplanationText());
        // Jejak adopsi saja (ADR-0001): tidak ada sinkronisasi lanjutan dari master ini.
        copy.setSourceQuestionId(master.getId());
        copy.setCreatedBy(actor);
        questions.save(copy);
        // Posisi master dipertahankan apa adanya, bukan dihitung ulang: Topic tujuan baru lahir
        // dan kosong, dan celah posisi master (soal di tengah pernah dihapus) ikut terbawa utuh.
        items.save(new PaketItemEntity(version, topic, copy, position));

        for (QuestionOptionEntity o : options.findByQuestionIdOrderByPositionAsc(master.getId())) {
            options.save(new QuestionOptionEntity(
                    copy.getId(), o.getBodyHtml(), o.getBodyText(), o.isCorrect(), o.getPosition()));
        }
    }

    /**
     * Paket master yang sudah pernah diadopsi Client ini, di antara {@code masterPaketIds} yang
     * sedang ditampilkan (FR-076, FR-077).
     *
     * <p>Dibaca dari {@code sourcePaketId} yang sudah ditulis sejak adopsi pertama — jejak asal
     * yang memang dirancang untuk ditelusuri (ADR-0001), bukan tabel jejak baru. Dibatasi pada
     * satu halaman katalog supaya biayanya tetap datar berapa pun besar katalognya.
     */
    @Transactional(readOnly = true)
    public Set<UUID> adoptedSourcePaketIds(UUID clientId, Collection<UUID> masterPaketIds) {
        if (masterPaketIds == null || masterPaketIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(pakets.findAdoptedSourceIds(clientId, masterPaketIds));
    }
}
