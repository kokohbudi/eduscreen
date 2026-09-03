package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.modules.assessment.service.ClientOnboardingService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

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
    private PaketRepository paketRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private QuestionOptionRepository optionRepository;
    @Autowired
    private RuanganRepository ruanganRepository;
    @Autowired
    private AppUserRepository appUserRepository;

    /**
     * Paket master: satu Subject GLOBAL, satu Topic di bawahnya, lima Question (bukan 20 seperti
     * di spec) beserta Option. Jumlahnya diperkecil karena yang diuji di sini adalah ATURAN
     * penyalinannya (copy-on-adopt, non-sinkronisasi), bukan skalanya — 20 baris tidak
     * membuktikan apa pun yang tidak sudah dibuktikan lima baris.
     *
     * <p>Paket, bukan lagi Exercise, adalah satuan adopsi sejak ADR-0018 (§6.1 business-rules) —
     * onboarding menyalin Paket beserta Topic dan Question-nya, Exercise tidak pernah jadi objek
     * adopsi.
     */
    private PaketEntity buatPaketMaster(String namaSubject) {
        SubjectEntity subject = subjectRepository.save(SubjectEntity.global(namaSubject));
        PaketEntity paket = paketRepository.save(
                PaketEntity.master(subject.getId(), "Aljabar Master", null));
        testData.draftVersion(paket);
        TopicEntity topic = topicRepository.save(TopicEntity.of(paket.getId(), "Aljabar Master", 0));

        for (int i = 1; i <= 5; i++) {
            QuestionEntity question = new QuestionEntity(null, QuestionType.MULTIPLE_CHOICE,
                    "<p>Soal master " + i + "</p>", "Soal master " + i);
            question.publish(java.time.OffsetDateTime.now());
            questionRepository.save(question);
            testData.place(question, topic);
            for (int opt = 0; opt < 4; opt++) {
                optionRepository.save(new QuestionOptionEntity(question.getId(),
                        "<p>Opsi " + opt + "</p>", "Opsi " + opt, opt == 0, opt));
            }
        }

        // Gerbang adopsi ada di tingkat Paket sejak ADR-0018 (FR-067): Paket yang masih digarap
        // tidak boleh mendarat di sekolah baru lewat pintu belakang onboarding.
        paket.publish(java.time.OffsetDateTime.now());
        return paketRepository.save(paket);
    }

    @Test
    @DisplayName("AC-O01: onboarding menyalin paket master ke Client, dan menyunting master setelahnya tidak mengubah salinan Client")
    void ac_o01_onboardingMenyalinPaketMasterSecaraLepasDariAsalnya() {
        PaketEntity masterPaket = buatPaketMaster("Matematika Kelas 4 O01");
        List<QuestionEntity> masterQuestions =
                testData.questionsInPaket(masterPaket.getId());
        assertThat(masterQuestions).hasSize(5);

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O01", "Asia/Jakarta", testData.uniqueEmail("admin.o01"),
                "Admin O01", List.of(masterPaket.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        // Client mendapat Paket dan Question ber-clientId miliknya sendiri, sejumlah yang sama
        // dengan Paket master yang dipilih.
        List<PaketEntity> paketClient = paketRepository.findByClientIdAndSubjectIdOrderByTitleAsc(
                clientBaru.getId(), masterPaket.getSubjectId());
        assertThat(paketClient).hasSize(1);
        PaketEntity paketSalinan = paketClient.get(0);
        assertThat(paketSalinan.getSourcePaketId()).isEqualTo(masterPaket.getId());

        List<QuestionEntity> questionsSalinan =
                testData.questionsInPaket(paketSalinan.getId());
        assertThat(questionsSalinan).hasSize(masterQuestions.size());

        // Menyunting Question MASTER setelah onboarding tidak boleh mengubah salinan Client:
        // adopsi adalah copy-on-adopt, bukan referensi (ADR-0001).
        QuestionEntity masterUntukDiubah = masterQuestions.get(0);
        masterUntukDiubah.setBodyHtml("<p>DIUBAH SETELAH ONBOARDING</p>");
        questionRepository.save(masterUntukDiubah);

        List<QuestionEntity> questionsSalinanSetelahEdit =
                testData.questionsInPaket(paketSalinan.getId());
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
    @DisplayName("AC-O02: paket master di bawah Subject GLOBAL tidak melahirkan Subject baru, dan Paket salinan tetap menunjuk Subject global yang sama")
    void ac_o02_tidakAdaSubjectBaruDanPaketSalinanMenunjukSubjectGlobalYangSama() {
        PaketEntity masterPaket = buatPaketMaster("Matematika Kelas 4 O02");
        UUID subjectGlobalId = masterPaket.getSubjectId();

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O02", "Asia/Jakarta", testData.uniqueEmail("admin.o02"),
                "Admin O02", List.of(masterPaket.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        // Subject GLOBAL tidak pernah disalin (BR-O02): menyalinnya hanya akan menggandakan data
        // yang sudah dibaca langsung oleh semua Client tanpa tujuan apa pun.
        assertThat(subjectRepository.findByClientIdOrderByNameAsc(clientBaru.getId())).isEmpty();

        // Paket salinan milik sekolah baru tetap menunjuk subjectId GLOBAL yang persis sama —
        // hanya Paket beserta Topic dan Question-nya yang disalin, Subject induknya tidak
        // (ADR-0018).
        List<PaketEntity> paketClient = paketRepository.findByClientIdAndSubjectIdOrderByTitleAsc(
                clientBaru.getId(), subjectGlobalId);
        assertThat(paketClient).hasSize(1);
        assertThat(paketClient.get(0).getSubjectId()).isEqualTo(subjectGlobalId);
        assertThat(paketClient.get(0).getClientId()).isEqualTo(clientBaru.getId());
    }
}
