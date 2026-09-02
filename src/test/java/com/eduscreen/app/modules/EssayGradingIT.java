package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.ResultStatus;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.ScoreAuditEntity;
import com.eduscreen.app.modules.assessment.repository.ScoreAuditRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.GradingService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T085 — Penilaian essay (AC-C02, AC-G01, AC-G02).
 */
class EssayGradingIT extends PostgresTestBase {

    @Autowired
    private TestData testData;
    @Autowired
    private ExamSessionService examSessionService;
    @Autowired
    private AnswerService answerService;
    @Autowired
    private SessionFinalizer sessionFinalizer;
    @Autowired
    private GradingService gradingService;
    @Autowired
    private ResultRepository resultRepository;
    @Autowired
    private ScoreAuditRepository scoreAuditRepository;

    @Test
    @DisplayName("AC-C02: sesi 9 MCQ (8 benar) + 1 essay berhenti PENDING_REVIEW pada 0.8, lalu FINAL pada 0.875 setelah dinilai")
    void ac_c02_skorSementaraLaluSkorFinalSetelahPenilaianEssay() {
        ClientEntity client = testData.client("SD Campuran");
        AppUserEntity guru = testData.user(client, UserRole.GURU, "Guru Campuran");
        AppUserEntity siswa = testData.user(client, UserRole.SISWA, "Siswa Campuran");
        RuanganEntity ruangan = testData.ruangan(client, "Kelas Campuran");
        testData.join(ruangan, guru, MemberRole.GURU);
        testData.join(ruangan, siswa, MemberRole.SISWA);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        List<QuestionEntity> content = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            content.add(testData.mcq(client, topic, "Soal MCQ " + i, 4));
        }
        QuestionEntity essayQuestion = testData.essay(client, topic, "Soal essai campuran");
        content.add(essayQuestion);

        ExerciseEntity exercise = testData.exercise(client, guru, "Ulangan Campuran", content);
        AssignmentEntity assignment = testData.publishedQuiz(client, exercise, ruangan, guru,
                60, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal siswaPrincipal = testData.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        // shuffleQuestions=false pada publishedQuiz, jadi urutan snapshot sama persis dengan
        // urutan penyusunan Exercise di atas.
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        assertThat(snapshot).hasSize(10);

        // 8 dari 9 MCQ dijawab benar; MCQ ke-9 (indeks 8) sengaja dijawab salah.
        for (int i = 0; i < 9; i++) {
            QuestionEntity question = content.get(i);
            var selected = i == 8 ? testData.wrongOptionOf(question) : testData.correctOptionOf(question);
            answerService.save(session.getId(), snapshot.get(i).getId(), selected, null, siswaPrincipal);
        }
        AnswerService.Saved essaySaved = answerService.save(
                session.getId(), snapshot.get(9).getId(), null, "Jawaban essai siswa", siswaPrincipal);

        ResultEntity setelahSubmit = sessionFinalizer.submit(session.getId(), client.getId());
        assertThat(setelahSubmit.getStatus()).isEqualTo(ResultStatus.PENDING_REVIEW);
        // BigDecimal wajib dibandingkan dengan compareTo, bukan equals — skala penyimpanannya
        // bisa berbeda (0.8 vs 0.8000) walau nilainya identik.
        assertThat(setelahSubmit.getScore().compareTo(new BigDecimal("0.8"))).isZero();

        UserPrincipal guruPrincipal = testData.principal(guru);
        ResultEntity setelahDinilai = gradingService.grade(
                essaySaved.answer().getId(), 75, guruPrincipal);
        assertThat(setelahDinilai.getStatus()).isEqualTo(ResultStatus.FINAL);
        // (8 + 0.75) / 10 = 0.875 — delapan MCQ benar ditambah tiga per empat poin essay.
        assertThat(setelahDinilai.getScore().compareTo(new BigDecimal("0.875"))).isZero();
    }

    @Test
    @DisplayName("AC-G01: Guru yang tidak ditugaskan di Ruangan Assignment ditolak menilai essay, dan essayScore tetap null")
    void ac_g01_guruTakDitugaskanDitolakMenilai() {
        ClientEntity client = testData.client("SD Penilaian Ditolak");
        AppUserEntity guruDitugaskan = testData.user(client, UserRole.GURU, "Guru Ditugaskan");
        AppUserEntity guruLain = testData.user(client, UserRole.GURU, "Guru Tidak Ditugaskan");
        AppUserEntity siswa = testData.user(client, UserRole.SISWA, "Siswa G01");
        RuanganEntity ruangan = testData.ruangan(client, "Kelas G01");
        // guruLain sengaja TIDAK di-join ke Ruangan ini.
        testData.join(ruangan, guruDitugaskan, MemberRole.GURU);
        testData.join(ruangan, siswa, MemberRole.SISWA);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity essayQuestion = testData.essay(client, topic, "Soal essai G01");
        ExerciseEntity exercise = testData.exercise(client, guruDitugaskan, "Ulangan G01",
                List.of(essayQuestion));
        AssignmentEntity assignment = testData.publishedQuiz(client, exercise, ruangan, guruDitugaskan,
                60, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal siswaPrincipal = testData.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        AnswerService.Saved essaySaved = answerService.save(
                session.getId(), snapshot.get(0).getId(), null, "Jawaban essai G01", siswaPrincipal);
        sessionFinalizer.submit(session.getId(), client.getId());

        UserPrincipal guruLainPrincipal = testData.principal(guruLain);
        // Guru B tidak ditugaskan di Ruangan Assignment ini: identik dengan jawaban yang tidak
        // ada (BR-G01, TC-09) — 404, bukan 403.
        assertThrows(ResourceNotFoundException.class,
                () -> gradingService.grade(essaySaved.answer().getId(), 80, guruLainPrincipal));

        ExamSessionService.QuestionView view = examSessionService.view(session, 0, false);
        assertThat(view.answer().getEssayScore()).isNull();
    }

    @Test
    @DisplayName("AC-G02: menilai ulang Result FINAL meninggalkan jejak audit yang bertambah, bukan tergantikan")
    void ac_g02_penilaianUlangMeninggalkanJejakAuditBertambah() {
        ClientEntity client = testData.client("SD Nilai Ulang");
        AppUserEntity guru = testData.user(client, UserRole.GURU, "Guru Nilai Ulang");
        AppUserEntity siswa = testData.user(client, UserRole.SISWA, "Siswa Nilai Ulang");
        RuanganEntity ruangan = testData.ruangan(client, "Kelas Nilai Ulang");
        testData.join(ruangan, guru, MemberRole.GURU);
        testData.join(ruangan, siswa, MemberRole.SISWA);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity essayQuestion = testData.essay(client, topic, "Soal essai nilai ulang");
        ExerciseEntity exercise = testData.exercise(client, guru, "Ulangan Nilai Ulang",
                List.of(essayQuestion));
        AssignmentEntity assignment = testData.publishedQuiz(client, exercise, ruangan, guru,
                60, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal siswaPrincipal = testData.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        AnswerService.Saved essaySaved = answerService.save(
                session.getId(), snapshot.get(0).getId(), null, "Jawaban essai nilai ulang", siswaPrincipal);
        sessionFinalizer.submit(session.getId(), client.getId());

        UserPrincipal guruPrincipal = testData.principal(guru);
        ResultEntity pertama = gradingService.grade(essaySaved.answer().getId(), 75, guruPrincipal);
        assertThat(pertama.getStatus()).isEqualTo(ResultStatus.FINAL);
        assertThat(pertama.getScore().compareTo(new BigDecimal("0.75"))).isZero();

        // Di sekolah, nilai yang berubah adalah bahan sengketa: orang tua atau Siswa akan
        // bertanya kenapa nilainya berubah, dan sistem harus bisa menjawab siapa yang mengubahnya
        // dan kapan — bukan cuma menyimpan angka terakhirnya.
        ResultEntity kedua = gradingService.grade(essaySaved.answer().getId(), 90, guruPrincipal);
        assertThat(kedua.getStatus()).isEqualTo(ResultStatus.FINAL);
        assertThat(kedua.getScore().compareTo(new BigDecimal("0.90"))).isZero();

        // (a) skor Result dihitung ulang seketika, bisa dibaca ulang dari database.
        ResultEntity dariDatabase = resultRepository.findBySessionId(session.getId()).orElseThrow();
        assertThat(dariDatabase.getScore().compareTo(new BigDecimal("0.90"))).isZero();

        // (b) dan (c): dua baris audit, bukan satu baris yang tertimpa; masing-masing mencatat
        // pelaku dan perpindahan nilainya sendiri.
        List<ScoreAuditEntity> jejak = scoreAuditRepository.findByResultIdOrderByChangedAtAsc(kedua.getId());
        assertThat(jejak).hasSize(2);
        assertThat(jejak.get(0).getChangedBy()).isEqualTo(guru.getId());
        assertThat(jejak.get(0).getNewValue().compareTo(new BigDecimal("0.75"))).isZero();
        assertThat(jejak.get(1).getChangedBy()).isEqualTo(guru.getId());
        assertThat(jejak.get(1).getOldValue().compareTo(new BigDecimal("0.75"))).isZero();
        assertThat(jejak.get(1).getNewValue().compareTo(new BigDecimal("0.90"))).isZero();
    }
}
