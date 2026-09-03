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
import com.eduscreen.app.modules.assessment.service.PaketAccessService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
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
    private PaketAccessService access;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private com.eduscreen.app.modules.assessment.service.PaketVersionService versionService;
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
     * aksesnya (referensi, bukan salinan — ADR-0021), bukan skalanya.
     *
     * <p>Paket adalah satuan akses sejak ADR-0018 (§6.1 business-rules); Exercise tidak pernah
     * jadi objeknya.
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

        // Gerbang akses ada di tingkat Paket (FR-067): Paket yang masih digarap tidak boleh
        // terbaca sekolah baru lewat pintu belakang onboarding. Versi kerjanya dibekukan sebagai
        // versi 1 (ADR-0021) — akses menunjuk versi terbit.
        paket.publish(java.time.OffsetDateTime.now());
        versionService.freeze(testData.versionOf(topic));
        return paketRepository.save(paket);
    }

    @Test
    @DisplayName("AC-O01 (ADR-0021): onboarding memberi akses ke Paket master, bukan salinan — sekolah baru membaca soal master apa adanya, nol baris soal baru")
    void ac_o01_onboardingMemberiAksesBukanSalinan() {
        PaketEntity masterPaket = buatPaketMaster("Matematika Kelas 4 O01");
        List<QuestionEntity> masterQuestions = testData.questionsInPaket(masterPaket.getId());
        assertThat(masterQuestions).hasSize(5);
        long soalSebelum = questionRepository.count();

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O01", "Asia/Jakarta", testData.uniqueEmail("admin.o01"),
                "Admin O01", List.of(masterPaket.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        // Tidak ada Paket maupun soal yang lahir milik Client: hanya satu baris akses.
        assertThat(paketRepository.findByClientIdOrderByTitleAsc(clientBaru.getId())).isEmpty();
        assertThat(questionRepository.count()).isEqualTo(soalSebelum);
        assertThat(access.masterPaketsFor(clientBaru.getId()))
                .extracting(PaketEntity::getId).containsExactly(masterPaket.getId());
        assertThat(questionService.requireReadable(masterQuestions.get(0).getId(), clientBaru.getId()).getId())
                .isEqualTo(masterQuestions.get(0).getId());

        // BR-O01: onboarding sengaja TIDAK membuat Ruangan maupun akun Siswa — hanya Client Admin
        // yang tahu struktur kelasnya sendiri, jadi dialah yang membuatnya setelah login.
        assertThat(ruanganRepository.findByClientIdOrderByNameAsc(clientBaru.getId())).isEmpty();

        List<AppUserEntity> penggunaClientBaru = appUserRepository
                .findByClientId(clientBaru.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(penggunaClientBaru).hasSize(1);
        assertThat(penggunaClientBaru.get(0).getRole()).isEqualTo(UserRole.CLIENT_ADMIN);
    }

    @Test
    @DisplayName("AC-O02 (BR-O02): Subject GLOBAL tidak pernah disalin ke sekolah baru; Paket master tetap milik Eduscreen di Subject yang sama")
    void ac_o02_tidakAdaSubjectBaruDanTidakAdaPaketSalinan() {
        PaketEntity masterPaket = buatPaketMaster("Matematika Kelas 4 O02");
        UUID subjectGlobalId = masterPaket.getSubjectId();

        ClientOnboardingService.OnboardingRequest request = new ClientOnboardingService.OnboardingRequest(
                "SD Onboarding O02", "Asia/Jakarta", testData.uniqueEmail("admin.o02"),
                "Admin O02", List.of(masterPaket.getId()));
        ClientEntity clientBaru = onboardingService.onboard(request);

        assertThat(subjectRepository.findByClientIdOrderByNameAsc(clientBaru.getId())).isEmpty();
        assertThat(paketRepository.findByClientIdAndSubjectIdOrderByTitleAsc(clientBaru.getId(), subjectGlobalId))
                .isEmpty();
        assertThat(paketRepository.findById(masterPaket.getId()).orElseThrow().getClientId()).isNull();
        assertThat(access.readablePakets(clientBaru.getId()))
                .extracting(PaketEntity::getSubjectId).containsExactly(subjectGlobalId);
    }
}
