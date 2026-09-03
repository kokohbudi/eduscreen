package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketItemRepository;
import com.eduscreen.app.modules.assessment.repository.PaketVersionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Meminjam soal dari Paket lain di dalam satu Client.
 *
 * <p>Pinjam adalah SALINAN penuh, bukan referensi (ADR-0018). Alasannya: Client Admin menyunting
 * soal hasil pinjaman di Paket tujuan — mengubah {@code bodyHtml}, menambah Option, memperbaiki
 * pembahasan — dan suntingan itu tidak boleh bocor ke Paket asal maupun ke Paket lain yang
 * kebetulan pernah meminjam soal yang sama. Referensi tidak bisa memberikan itu.
 *
 * <p>Bedakan dengan {@code ExerciseItem}, yang memang memakai referensi ke {@code questionId}:
 * Guru merakit Exercise, tidak menyuntingnya, sehingga tidak ada suntingan yang bisa bocor di
 * sana dan referensi aman dipakai.
 */
@Service
public class PaketBorrowService {

    private final PaketService pakets;
    private final TopicRepository topics;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final PaketItemRepository items;

    public PaketBorrowService(PaketService pakets, TopicRepository topics,
                              QuestionRepository questions, QuestionOptionRepository options,
                              PaketItemRepository items) {
        this.pakets = pakets;
        this.topics = topics;
        this.questions = questions;
        this.options = options;
        this.items = items;
    }

    /**
     * Menyalin soal terpilih ke satu Topic di versi kerja Paket tujuan. Mengembalikan jumlah
     * yang tersalin.
     *
     * <p>Soal yang pemanggilnya bukan pemilik ({@code clientId} tidak cocok) dan soal yang
     * {@code sourceQuestionId}-nya sudah ada di Paket tujuan sama-sama dilewati diam-diam,
     * bukan menghentikan seluruh permintaan dengan galat (TC-36, AC-B04) — daftar pinjam boleh
     * berisi campuran pengenal yang valid dan tidak, dan yang valid tetap tersalin.
     */
    @Transactional
    public int borrowQuestions(UUID targetPaketId, UUID targetTopicId, List<UUID> questionIds,
                               UUID clientId, UUID actor) {
        PaketEntity target = pakets.require(targetPaketId, clientId);
        TopicEntity topic = topicOf(target, targetTopicId);
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        PaketVersionEntity version = pakets.draftOf(target.getId());
        Set<UUID> sudahAda = new HashSet<>(questions.findSourceIdsInVersion(version.getId()));
        int tersalin = 0;
        for (UUID id : questionIds) {
            QuestionEntity asal = bacaMilikClient(id, clientId);
            if (asal == null || sudahAda.contains(asal.getId())) {
                continue;
            }
            salin(asal, target, version, topic, actor);
            sudahAda.add(asal.getId());
            tersalin++;
        }
        return tersalin;
    }

    /**
     * Menyalin seluruh soal satu Topic sumber sekaligus, urut posisinya di Paket asal.
     *
     * <p>Topic sumber milik pemilik lain, atau tidak ada, menghasilkan nol tersalin tanpa galat
     * (TC-36): {@code findWritable}/{@code findWritableMaster} menyaring pemilik lewat join ke
     * Paket, jadi keduanya sama-sama kosong.
     */
    @Transactional
    public int borrowTopic(UUID targetPaketId, UUID targetTopicId, UUID sourceTopicId,
                           UUID clientId, UUID actor) {
        List<UUID> ids = (clientId == null
                ? topics.findWritableMaster(sourceTopicId)
                : topics.findWritable(sourceTopicId, clientId))
                .map(sumber -> questions.findByVersionAndTopicOrdered(
                        clientId, pakets.versionOf(sumber.getPaketId()).getId(), sumber.getId()))
                .orElse(List.of())
                .stream()
                .map(QuestionEntity::getId)
                .toList();
        return borrowQuestions(targetPaketId, targetTopicId, ids, clientId, actor);
    }

    /**
     * Soal asal yang salinannya sudah ada di versi kerja Paket ini, supaya tidak tersalin dua
     * kali (AC-B04). Pemanggil wajib sudah lolos {@code PaketService.require} untuk Paket ini.
     */
    public Set<UUID> borrowedSourceIds(UUID paketId) {
        return new HashSet<>(questions.findSourceIdsInVersion(pakets.versionOf(paketId).getId()));
    }

    private TopicEntity topicOf(PaketEntity target, UUID topicId) {
        TopicEntity topic = topics.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic tidak ditemukan"));
        if (!topic.getPaketId().equals(target.getId())) {
            throw new IllegalArgumentException("Topic bukan milik Paket ini");
        }
        return topic;
    }

    /**
     * Null bila soal itu milik Client lain: nol hasil, bukan galat yang membocorkan (TC-36).
     * Kedua cabang menyaring pemilik DI DALAM query.
     */
    private QuestionEntity bacaMilikClient(UUID id, UUID clientId) {
        return (clientId == null
                ? questions.findByIdAndClientIdIsNull(id)
                : questions.findByIdAndClientId(id, clientId))
                .orElse(null);
    }

    /** Menyalin satu Question beserta seluruh Option-nya: jumlah, jawaban benar, dan urutan (FR-016). */
    private void salin(QuestionEntity asal, PaketEntity target, PaketVersionEntity version,
                       TopicEntity topic, UUID actor) {
        QuestionEntity copy = new QuestionEntity(
                target.getClientId(), asal.getType(), asal.getBodyHtml(), asal.getBodyText());
        copy.setExplanationHtml(asal.getExplanationHtml());
        copy.setExplanationText(asal.getExplanationText());
        // Jejak asal saja (ADR-0001): tidak ada sinkronisasi lanjutan dari sumbernya.
        copy.setSourceQuestionId(asal.getId());
        copy.setCreatedBy(actor);
        questions.save(copy);
        // Topic tujuan bisa saja sudah berisi soal lain, jadi salinan mendarat di ekornya.
        items.save(new PaketItemEntity(version, topic, copy, items.nextPosition(version.getId(), topic.getId())));

        for (QuestionOptionEntity o : options.findByQuestionIdOrderByPositionAsc(asal.getId())) {
            options.save(new QuestionOptionEntity(
                    copy.getId(), o.getBodyHtml(), o.getBodyText(), o.isCorrect(), o.getPosition()));
        }
    }
}
