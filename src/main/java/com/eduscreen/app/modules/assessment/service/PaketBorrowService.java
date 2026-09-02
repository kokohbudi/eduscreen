package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.PaketEntity;
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

    public PaketBorrowService(PaketService pakets, TopicRepository topics,
                              QuestionRepository questions, QuestionOptionRepository options) {
        this.pakets = pakets;
        this.topics = topics;
        this.questions = questions;
        this.options = options;
    }

    /**
     * Menyalin soal terpilih ke satu Topic di Paket tujuan. Mengembalikan jumlah yang tersalin.
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
        Set<UUID> sudahAda = borrowedSourceIds(targetPaketId);
        int tersalin = 0;
        for (UUID id : questionIds) {
            QuestionEntity asal = bacaMilikClient(id, clientId);
            if (asal == null || sudahAda.contains(asal.getId())) {
                continue;
            }
            salin(asal, target, topic, actor);
            sudahAda.add(asal.getId());
            tersalin++;
        }
        return tersalin;
    }

    /** Menyalin seluruh soal satu Topic sumber sekaligus. */
    @Transactional
    public int borrowTopic(UUID targetPaketId, UUID targetTopicId, UUID sourceTopicId,
                           UUID clientId, UUID actor) {
        List<UUID> ids = questions.findByClientIdAndTopicIdOrderByCreatedAtAsc(clientId, sourceTopicId)
                .stream()
                .map(QuestionEntity::getId)
                .toList();
        return borrowQuestions(targetPaketId, targetTopicId, ids, clientId, actor);
    }

    /** Soal asal yang salinannya sudah ada di Paket ini, supaya tidak tersalin dua kali (AC-B04). */
    public Set<UUID> borrowedSourceIds(UUID paketId) {
        return new HashSet<>(questions.findSourceIdsInPaket(paketId));
    }

    private TopicEntity topicOf(PaketEntity target, UUID topicId) {
        TopicEntity topic = topics.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic tidak ditemukan"));
        if (!topic.getPaketId().equals(target.getId())) {
            throw new IllegalArgumentException("Topic bukan milik Paket ini");
        }
        return topic;
    }

    /** Null bila soal itu milik Client lain: nol hasil, bukan galat yang membocorkan (TC-36). */
    private QuestionEntity bacaMilikClient(UUID id, UUID clientId) {
        return clientId == null
                ? questions.findById(id).filter(q -> q.getClientId() == null).orElse(null)
                : questions.findByIdAndClientId(id, clientId).orElse(null);
    }

    /** Menyalin satu Question beserta seluruh Option-nya: jumlah, jawaban benar, dan urutan (FR-016). */
    private void salin(QuestionEntity asal, PaketEntity target, TopicEntity topic, UUID actor) {
        QuestionEntity copy = new QuestionEntity(
                target.getClientId(), target.getId(), topic.getId(), asal.getType(),
                asal.getBodyHtml(), asal.getBodyText());
        copy.setExplanationHtml(asal.getExplanationHtml());
        copy.setExplanationText(asal.getExplanationText());
        // Jejak asal saja (ADR-0001): tidak ada sinkronisasi lanjutan dari sumbernya.
        copy.setSourceQuestionId(asal.getId());
        copy.setCreatedBy(actor);
        copy.moveTo(questions.nextPosition(topic.getId()));
        questions.save(copy);

        for (QuestionOptionEntity o : options.findByQuestionIdOrderByPositionAsc(asal.getId())) {
            options.save(new QuestionOptionEntity(
                    copy.getId(), o.getBodyHtml(), o.getBodyText(), o.isCorrect(), o.getPosition()));
        }
    }
}
