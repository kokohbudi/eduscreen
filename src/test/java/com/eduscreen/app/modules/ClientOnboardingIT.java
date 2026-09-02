package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T094 — Onboarding Client baru (AC-O01, AC-O02).
 *
 * <p>Konten master (baris ber-{@code client_id} null) tidak dibangun {@link TestData}, yang
 * hanya tahu cara membuat konten milik Client — jadi dibuat langsung lewat repository di sini.
 */
class ClientOnboardingIT extends PostgresTestBase {

    @Autowired
    private TestData testData;
    @Autowired
    private ClientOnboardingService onboardingService;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private QuestionOptionRepository optionRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private ExerciseItemRepository exerciseItemRepository;
    @Autowired
    private RuanganRepository ruanganRepository;
    @Autowired
    private AppUserRepository appUserRepository;

    /**
     * Paket master: satu Subject GLOBAL, satu Topic di bawahnya, lima Question (bukan 20 seperti
     * di spec) beserta Option, dirangkai jadi satu Exercise master. Jumlahnya diperkecil karena
     * yang diuji di sini adalah ATURAN penyalinannya (copy-on-adopt, non-sinkronisasi), bukan
     * skalanya — 20 baris tidak membuktikan apa pun yang tidak sudah dibuktikan lima baris.
     */
    private ExerciseEntity buatPaketMaster(String namaSubject) {
        SubjectEntity subject = subjectRepository.save(SubjectEntity.global(namaSubject));
        TopicEntity topic = topicRepository.save(TopicEntity.global(subject.getId(), "Aljabar Master"));

        List<QuestionEntity> masterQuestions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            QuestionEntity question = new QuestionEntity(null, topic.getId(), QuestionType.MULTIPLE_CHOICE,
                    "<p>Soal master " + i + "</p>", "Soal master " + i);
            // Terbit: onboarding hanya boleh menyalin konten master yang sudah terbit, dan
            // paket yang masih digarap tidak boleh mendarat di sekolah baru lewat pintu
            // belakang onboarding (FR-067).
            question.publish(java.time.OffsetDateTime.now());
            questionRepository.save(question);
            for (int opt = 0; opt < 4; opt++) {
                optionRepository.save(new QuestionOptionEntity(question.getId(),
                        "<p>Opsi " + opt + "</p>", "Opsi " + opt, opt == 0, opt));
            }
            masterQuestions.add(question);
        }

        ExerciseEntity masterExercise = new ExerciseEntity(null, "Paket Master Uji", null);
        masterExercise.publish(java.time.OffsetDateTime.now());
        exerciseRepository.save(masterExercise);
        int position = 0;
        for (QuestionEntity question : masterQuestions) {
            exerciseItemRepository.save(new ExerciseItemEntity(masterExercise.getId(), question.getId(), position++));
        }
        return masterExercise;
    }

    @Test
    @DisplayName("AC-O01: onboarding menyalin paket master ke Client, dan menyunting master setelahnya tidak mengubah salinan Client")
    void ac_o01_onboardingMenyalinPaketMasterSecaraLepasDariAsalnya() {
        ExerciseEntity masterExercise = buatPaketMaster("Matematika Kelas 4 O01");
        List<UUID> masterQuestionIds = exerciseItemRepository
                .findByExerciseIdOrderByPositionAsc(masterExercise.getId())
                .stream().map(ExerciseItemEntity::getQuestionId).toList();
        assertThat(masterQuestionIds).hasSize(5);

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O01", "Asia/Jakarta", testData.uniqueEmail("admin.o01"),
                "Admin O01", List.of(masterExercise.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        // Client mendapat Exercise dan Question ber-clientId miliknya sendiri, sejumlah yang sama
        // dengan paket master yang dipilih.
        List<ExerciseEntity> exerciseClient = exerciseRepository
                .findByClientIdOrderByUpdatedAtDesc(clientBaru.getId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(exerciseClient).hasSize(1);
        ExerciseEntity exerciseSalinan = exerciseClient.get(0);

        List<UUID> questionIdsSalinan = exerciseItemRepository
                .findByExerciseIdOrderByPositionAsc(exerciseSalinan.getId())
                .stream().map(ExerciseItemEntity::getQuestionId).toList();
        assertThat(questionIdsSalinan).hasSize(masterQuestionIds.size());

        List<QuestionEntity> questionsSalinan = questionRepository
                .findByClientIdAndIdIn(clientBaru.getId(), questionIdsSalinan);
        assertThat(questionsSalinan).hasSize(5);

        // Menyunting Question MASTER setelah onboarding tidak boleh mengubah salinan Client:
        // adopsi adalah copy-on-adopt, bukan referensi (ADR-0001).
        QuestionEntity masterUntukDiubah = questionRepository.findByIdAndClientId(masterQuestionIds.get(0), null)
                .orElseThrow();
        masterUntukDiubah.setBodyHtml("<p>DIUBAH SETELAH ONBOARDING</p>");
        questionRepository.save(masterUntukDiubah);

        List<QuestionEntity> questionsSalinanSetelahEdit = questionRepository
                .findByClientIdAndIdIn(clientBaru.getId(), questionIdsSalinan);
        assertThat(questionsSalinanSetelahEdit)
                .noneMatch(q -> q.getBodyHtml().contains("DIUBAH SETELAH ONBOARDING"));

        // BR-O01: onboarding sengaja TIDAK membuat Ruangan maupun akun Siswa — hanya Client Admin
        // yang tahu struktur kelasnya sendiri, jadi dialah yang membuatnya setelah login.
        assertThat(ruanganRepository.findByClientIdOrderByNameAsc(clientBaru.getId())).isEmpty();

        List<AppUserEntity> penggunaClientBaru = appUserRepository
                .findByClientId(clientBaru.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(penggunaClientBaru).hasSize(1);
        assertThat(penggunaClientBaru.get(0).getRole()).isEqualTo(UserRole.CLIENT_ADMIN);
    }

    @Test
    @DisplayName("AC-O02: paket master di bawah Subject GLOBAL tidak melahirkan Subject baru, dan Topic salinan tetap menunjuk Subject global yang sama")
    void ac_o02_tidakAdaSubjectBaruDanTopicSalinanMenunjukSubjectGlobalYangSama() {
        ExerciseEntity masterExercise = buatPaketMaster("Matematika Kelas 4 O02");
        UUID masterQuestionId = exerciseItemRepository
                .findByExerciseIdOrderByPositionAsc(masterExercise.getId()).get(0)
                .getQuestionId();
        QuestionEntity masterQuestion = questionRepository.findByIdAndClientId(masterQuestionId, null).orElseThrow();
        UUID subjectGlobalId = topicRepository.findById(masterQuestion.getTopicId()).orElseThrow().getSubjectId();

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O02", "Asia/Jakarta", testData.uniqueEmail("admin.o02"),
                "Admin O02", List.of(masterExercise.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        // Subject GLOBAL tidak pernah disalin (BR-O02): menyalinnya hanya akan menggandakan data
        // yang sudah dibaca langsung oleh semua Client tanpa tujuan apa pun.
        assertThat(subjectRepository.findByClientIdOrderByNameAsc(clientBaru.getId())).isEmpty();

        List<ExerciseEntity> exerciseClient = exerciseRepository
                .findByClientIdOrderByUpdatedAtDesc(clientBaru.getId(), PageRequest.of(0, 10))
                .getContent();
        ExerciseEntity exerciseSalinan = exerciseClient.get(0);
        UUID questionIdSalinan = exerciseItemRepository
                .findByExerciseIdOrderByPositionAsc(exerciseSalinan.getId()).get(0)
                .getQuestionId();
        QuestionEntity questionSalinan = questionRepository
                .findByIdAndClientId(questionIdSalinan, clientBaru.getId()).orElseThrow();

        // Topic yang disalin (origin CLIENT) tetap menunjuk subjectId GLOBAL yang persis sama —
        // hanya Topic yang disalin, Subject induknya tidak.
        TopicEntity topicSalinan = topicRepository.findById(questionSalinan.getTopicId()).orElseThrow();
        assertThat(topicSalinan.getSubjectId()).isEqualTo(subjectGlobalId);
        assertThat(topicSalinan.getClientId()).isEqualTo(clientBaru.getId());
    }
}
