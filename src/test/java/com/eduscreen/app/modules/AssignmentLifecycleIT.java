package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.SessionStatus;
import com.eduscreen.app.modules.assessment.domain.TerminalReason;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AssignmentLifecycleService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService.PublishRequest;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.UnprocessableException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Menutup lubang cakupan siklus hidup Assignment setelah terbit: perpanjangan batas akhir
 * (BR-A02), imunitas Timer (BR-A03), keleluasaan DRAFT (BR-A01), penghapusan (BR-A04), penutupan
 * awal (BR-A05), dan interaksi perpanjangan dengan sesi yang sudah terminal (BR-T06).
 */
class AssignmentLifecycleIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    AssignmentPublishingService publishing;
    @Autowired
    AssignmentLifecycleService lifecycle;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    AssignmentRepository assignments;
    @Autowired
    ExamSessionRepository sessions;
    @Autowired
    ResultRepository results;

    @Test
    @DisplayName("AC-A01: perpanjangan expiresAt ke waktu lebih awal ditolak; ke waktu lebih lambat diterima")
    void extendRejectsEarlierDeadlineButAcceptsLater() {
        ClientEntity client = data.client("SD Lifecycle1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4A");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Matematika", "Pecahan");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Pecahan",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        OffsetDateTime original = OffsetDateTime.now().plusDays(7);
        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT, original, 3);
        UserPrincipal guruPrincipal = data.principal(guru);

        // Memajukan batas akhir memotong ujian yang sedang berjalan tanpa peringatan (BR-A02) —
        // gerbangnya harus menolak sebelum satu baris pun berubah.
        OffsetDateTime earlier = original.minusDays(4);
        assertThatThrownBy(() -> lifecycle.extend(assignment.getId(), earlier, guruPrincipal))
                .isInstanceOf(UnprocessableException.class);

        OffsetDateTime later = original.plusDays(3);
        AssignmentEntity extended = lifecycle.extend(assignment.getId(), later, guruPrincipal);
        // OffsetDateTime.equals() juga membandingkan representasi offset-nya, bukan hanya
        // instant yang diwakilinya; isEqual() adalah pembanding yang benar di sini (BR-T01).
        assertThat(extended.getExpiresAt().isEqual(later)).isTrue();
    }

    @Test
    @DisplayName("AC-A02: tidak ada jalur yang mengubah Timer setelah Assignment terbit")
    void publishedAssignmentTimerNeverChangesThroughExtend() {
        ClientEntity client = data.client("SD Lifecycle2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4B");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Matematika", "Geometri");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Geometri",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, 60, OffsetDateTime.now().plusDays(2), 3);
        UserPrincipal guruPrincipal = data.principal(guru);

        // BR-A03 tidak dijaga oleh validasi yang menolak permintaan — dijaga oleh KETIADAAN
        // jalur: AssignmentLifecycleService.extend hanya mengambil expiresAt sebagai parameter,
        // dan tidak ada service atau endpoint lain yang menyentuh timerDurationMinutes pada
        // Assignment yang sudah PUBLISHED. Memanggil satu-satunya jalur pasca-terbit yang ada
        // dan membuktikan Timer tetap 60 adalah bukti operasional dari ketiadaan itu.
        AssignmentEntity extended = lifecycle.extend(
                assignment.getId(), OffsetDateTime.now().plusDays(5), guruPrincipal);
        assertThat(extended.getTimerDurationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("AC-A03: Assignment DRAFT menerima perubahan Exercise, mode, Timer, dan kedua sakelar pengacakan")
    void draftAssignmentAcceptsEveryFieldChange() {
        ClientEntity client = data.client("SD Lifecycle3");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4C");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "IPA", "Ekosistem");
        ExerciseEntity exerciseA = data.exercise(client, guru, "Latihan A",
                List.of(data.mcq(client, topic, "Soal A1", 4)));
        ExerciseEntity exerciseB = data.exercise(client, guru, "Latihan B",
                List.of(data.mcq(client, topic, "Soal B1", 4)));

        UserPrincipal guruPrincipal = data.principal(guru);
        AssignmentEntity draft = publishing.createDraft(new PublishRequest(
                exerciseA.getId(), room.getId(), "Draf", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 2, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        assertThat(draft.isDraft()).isTrue();

        // BR-A01: selagi DRAFT, Assignment adalah dokumen kerja biasa — belum ada Siswa yang
        // menyandarkan sesi padanya, jadi tidak ada aturan yang perlu ditolak di sini. Perubahan
        // dilakukan langsung lewat setter entitas (jalur yang dipakai penyuntingan draf biasa),
        // bukan lewat gerbang publish/extend yang memang untuk Assignment yang sudah terbit.
        draft.setExerciseId(exerciseB.getId());
        draft.setMode(AssignmentMode.PRACTICE);
        draft.setTimerDurationMinutes(45);
        draft.setShuffleQuestions(true);
        draft.setShuffleOptions(true);
        assignments.save(draft);

        AssignmentEntity reread = assignments.findById(draft.getId()).orElseThrow();
        assertThat(reread.getExerciseId()).isEqualTo(exerciseB.getId());
        assertThat(reread.getMode()).isEqualTo(AssignmentMode.PRACTICE);
        assertThat(reread.getTimerDurationMinutes()).isEqualTo(45);
        assertThat(reread.isShuffleQuestions()).isTrue();
        assertThat(reread.isShuffleOptions()).isTrue();
    }

    @Test
    @DisplayName("AC-A04: draf terhapus lewat deleteDraft; Assignment terbit ditolak dan barisnya tetap ada")
    void deleteDraftRemovesDraftButRejectsPublished() {
        ClientEntity client = data.client("SD Lifecycle4");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4D");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "IPS", "Sejarah");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Sejarah",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        UserPrincipal guruPrincipal = data.principal(guru);
        AssignmentEntity draft = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Draf", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 2, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        AssignmentEntity published = data.publishedQuiz(
                client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT,
                OffsetDateTime.now().plusDays(1), 3);

        lifecycle.deleteDraft(draft.getId(), guruPrincipal);
        assertThat(assignments.findById(draft.getId())).isEmpty();

        // BR-A04: Assignment terbit tidak boleh menghilang — Siswa mungkin sudah punya Result
        // yang menunjuknya. Ia ditutup, bukan dihapus, sehingga barisnya WAJIB tetap ada setelah
        // penolakan ini, bukan sekadar "penghapusan gagal tapi mungkin sudah setengah jalan".
        assertThatThrownBy(() -> lifecycle.deleteDraft(published.getId(), guruPrincipal))
                .isInstanceOf(IllegalStateException.class);
        assertThat(assignments.findById(published.getId())).isPresent();
    }

    @Test
    @DisplayName("AC-A05: menutup Assignment lebih awal mengakhiri seluruh sesi IN_PROGRESS sebagai EXPIRED dengan Result")
    void closeEarlyExpiresAllRunningSessions() {
        ClientEntity client = data.client("SD Lifecycle5");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4E");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Bahasa", "Puisi");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Puisi",
                List.of(data.mcq(client, topic, "Soal 1", 4)));
        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT,
                OffsetDateTime.now().plusDays(1), 3);

        List<AppUserEntity> siswaList = List.of(
                data.user(client, UserRole.SISWA, "Siswa 1"),
                data.user(client, UserRole.SISWA, "Siswa 2"),
                data.user(client, UserRole.SISWA, "Siswa 3"));
        List<ExamSessionEntity> started = siswaList.stream()
                .peek(siswa -> data.join(room, siswa, MemberRole.SISWA))
                .map(siswa -> examSessionService.start(assignment.getId(), data.principal(siswa)))
                .toList();
        assertThat(started).allMatch(ExamSessionEntity::isInProgress);

        lifecycle.closeEarly(assignment.getId(), data.principal(guru));

        for (ExamSessionEntity session : started) {
            ExamSessionEntity reread = sessions.findByIdAndClientId(session.getId(), client.getId())
                    .orElseThrow();
            assertThat(reread.getStatus()).isEqualTo(SessionStatus.EXPIRED);
            assertThat(reread.getTerminalReason()).isEqualTo(TerminalReason.EXPIRATION_REACHED);
            // BR-A05 tidak hanya menutup sesinya — tiap Siswa harus tetap mendapat Result,
            // meski ia belum sempat menjawab apa pun, sehingga rekap Guru tidak bolong.
            assertThat(results.findBySessionId(session.getId())).isPresent();
        }
    }

    @Test
    @DisplayName("AC-T02: perpanjangan expiresAt tidak menghidupkan kembali sesi EXPIRED, tapi membuka Mulai baru bagi yang belum pernah Mulai")
    void extendDoesNotResurrectExpiredSessionsButAllowsNewStarts() {
        ClientEntity client = data.client("SD Lifecycle6");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 4F");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Bahasa", "Prosa");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Prosa",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        // expiresAt sengaja di masa lalu untuk mensimulasikan Assignment yang sudah kedaluwarsa
        // sebelum diperpanjang. TestData.publishedQuiz dipakai (bukan publish() service) karena
        // gerbang penerbitan menolak expiresAt yang sudah lewat (BR-M05) — keadaan awal ini
        // hanya bisa disiapkan lewat repository, memotong gerbang yang justru bukan yang diuji.
        OffsetDateTime pastExpiry = OffsetDateTime.now().minusHours(2);
        AssignmentEntity assignment = data.publishedQuiz(
                client, exercise, room, guru, TestData.TIMER_TAK_MENGIKAT, pastExpiry, 3);

        AppUserEntity siswa1 = data.user(client, UserRole.SISWA, "Siswa Lama 1");
        AppUserEntity siswa2 = data.user(client, UserRole.SISWA, "Siswa Lama 2");
        AppUserEntity siswaBaru = data.user(client, UserRole.SISWA, "Siswa Baru");
        data.join(room, siswa1, MemberRole.SISWA);
        data.join(room, siswa2, MemberRole.SISWA);
        data.join(room, siswaBaru, MemberRole.SISWA);

        // Sesi dibuat langsung lewat ExamSessionRepository, bukan ExamSessionService.start,
        // dengan alasan yang sama seperti di atas: start() menolak Assignment yang sudah
        // kedaluwarsa, padahal skenario ini justru butuh sesi yang lahir SEBELUM keadaan
        // kedaluwarsa itu terjadi. effectiveDeadline diisi sama dengan expiresAt karena Timer
        // (TIMER_TAK_MENGIKAT) sengaja tidak mengikat di sini.
        List<ExamSessionEntity> lama = List.of(siswa1, siswa2).stream()
                .map(siswa -> sessions.save(new ExamSessionEntity(
                        client.getId(), assignment.getId(), siswa.getId(), 1,
                        pastExpiry.minusHours(1), pastExpiry)))
                .toList();
        for (ExamSessionEntity session : lama) {
            finalizer.finalizeIfExpired(session.getId(), client.getId());
        }
        for (ExamSessionEntity session : lama) {
            ExamSessionEntity reread = sessions.findByIdAndClientId(session.getId(), client.getId())
                    .orElseThrow();
            assertThat(reread.getStatus()).isEqualTo(SessionStatus.EXPIRED);
        }

        lifecycle.extend(assignment.getId(), OffsetDateTime.now().plusDays(2), data.principal(guru));

        // BR-T06: sesi yang sudah terminal adalah fakta sejarah. Memperpanjang batas akhir tidak
        // boleh membuatnya seolah masih bisa dilanjutkan atau membuat Result-nya lenyap.
        for (ExamSessionEntity session : lama) {
            ExamSessionEntity reread = sessions.findByIdAndClientId(session.getId(), client.getId())
                    .orElseThrow();
            assertThat(reread.getStatus()).isEqualTo(SessionStatus.EXPIRED);
            assertThat(results.findBySessionId(session.getId())).isPresent();
        }

        // Siswa yang belum pernah menekan Mulai sama sekali harus tetap bisa memulai begitu
        // batas akhirnya sudah diperpanjang ke masa depan — perpanjangan itu efeknya nyata bagi
        // yang belum mulai, bukan hanya kosmetik pada kolom expiresAt.
        ExamSessionEntity sesiBaru = examSessionService.start(assignment.getId(), data.principal(siswaBaru));
        assertThat(sesiBaru.isInProgress()).isTrue();
    }
}
