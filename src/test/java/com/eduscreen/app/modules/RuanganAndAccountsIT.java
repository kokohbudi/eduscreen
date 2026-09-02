package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.InvitationPurpose;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.UserInvitationRepository;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService.PublishRequest;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.modules.identity.service.InvitationService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.modules.notification.port.out.NotificationPort;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T028 — Ruangan dan akun (AC-U01, AC-U02, AC-U04, AC-P01).
 */
class RuanganAndAccountsIT extends PostgresTestBase {

    @Autowired
    private TestData testData;
    @Autowired
    private ExamSessionService examSessionService;
    @Autowired
    private RuanganService ruanganService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private SessionFinalizer sessionFinalizer;
    @Autowired
    private AssignmentPublishingService publishingService;
    @Autowired
    private UserManagementService userManagementService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @MockitoBean
    private NotificationPort notificationPort;

    @Test
    @DisplayName("AC-U01: Siswa anggota dua Ruangan melihat Assignment aktif keduanya dalam satu daftar")
    void ac_u01_assignmentAktifDariSeluruhRuanganTampilSatuDaftar() {
        ClientEntity client = testData.client("SD Multi Ruangan");
        AppUserEntity guru = testData.user(client, UserRole.GURU, "Guru Multi");
        AppUserEntity siswa = testData.user(client, UserRole.SISWA, "Siswa Multi");

        // Kelas reguler dan grup bimbel adalah dua Ruangan yang sama sekali berbeda; satu-satunya
        // yang menyatukan keduanya di mata Siswa adalah keanggotaannya.
        RuanganEntity kelasReguler = testData.ruangan(client, "Kelas 4B");
        RuanganEntity grupBimbel = testData.ruangan(client, "Bimbel Intensif SBMPTN Group B");
        testData.join(kelasReguler, guru, MemberRole.GURU);
        testData.join(kelasReguler, siswa, MemberRole.SISWA);
        testData.join(grupBimbel, guru, MemberRole.GURU);
        testData.join(grupBimbel, siswa, MemberRole.SISWA);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        ExerciseEntity exerciseKelas = testData.exercise(client, guru, "Ulangan Kelas",
                List.of(testData.mcq(client, topic, "Soal kelas", 4)));
        ExerciseEntity exerciseBimbel = testData.exercise(client, guru, "Ulangan Bimbel",
                List.of(testData.mcq(client, topic, "Soal bimbel", 4)));

        AssignmentEntity assignmentKelas = testData.publishedQuiz(client, exerciseKelas, kelasReguler, guru,
                60, OffsetDateTime.now().plusDays(1), 3);
        AssignmentEntity assignmentBimbel = testData.publishedQuiz(client, exerciseBimbel, grupBimbel, guru,
                60, OffsetDateTime.now().plusDays(1), 3);

        UserPrincipal siswaPrincipal = testData.principal(siswa);
        List<AssignmentEntity> aktif = examSessionService.activeAssignments(siswaPrincipal);

        // Siswa tidak boleh harus berpindah konteks (buka menu kelas, lalu buka menu bimbel
        // terpisah) untuk tahu apa yang harus dikerjakan malam ini; satu daftar harus cukup.
        assertThat(aktif).extracting(AssignmentEntity::getId)
                .containsExactlyInAnyOrder(assignmentKelas.getId(), assignmentBimbel.getId());
    }

    @Test
    @DisplayName("AC-U02: Ruangan ARCHIVED tidak menerima anggota atau Assignment baru, tetapi Result lamanya tetap terbaca")
    void ac_u02_ruanganArsipReadOnlyTetapiRiwayatTetapTerbaca() {
        ClientEntity client = testData.client("SD Arsip");
        AppUserEntity guru = testData.user(client, UserRole.GURU, "Guru Arsip");
        AppUserEntity siswa = testData.user(client, UserRole.SISWA, "Siswa Arsip");
        AppUserEntity siswaBaru = testData.user(client, UserRole.SISWA, "Siswa Baru Arsip");
        RuanganEntity ruangan = testData.ruangan(client, "Kelas 4B 2025/2026");
        testData.join(ruangan, guru, MemberRole.GURU);
        testData.join(ruangan, siswa, MemberRole.SISWA);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        ExerciseEntity exercise = testData.exercise(client, guru, "Ulangan Arsip",
                List.of(testData.mcq(client, topic, "Soal arsip", 4)));
        AssignmentEntity assignment = testData.publishedQuiz(client, exercise, ruangan, guru,
                60, OffsetDateTime.now().plusDays(1), 3);

        // Result lahir SEBELUM Ruangan diarsipkan — riwayat yang sudah ada tidak boleh ikut
        // terkunci hanya karena wadahnya sekarang read-only.
        UserPrincipal siswaPrincipal = testData.principal(siswa);
        ExamSessionEntity session = examSessionService.start(assignment.getId(), siswaPrincipal);
        sessionFinalizer.submit(session.getId(), client.getId());

        ruanganService.archive(ruangan.getId(), client.getId());

        // (a) Ruangan terarsip tidak muncul di daftar tujuan aktif.
        assertThat(ruanganService.listActive(client.getId()))
                .extracting(RuanganEntity::getId)
                .doesNotContain(ruangan.getId());

        // (b) Menambah anggota ke Ruangan terarsip ditolak eksplisit, bukan diam-diam diabaikan.
        assertThrows(IllegalStateException.class, () -> ruanganService.addMembers(
                ruangan.getId(), client.getId(), List.of(siswaBaru.getId()), MemberRole.SISWA));

        // (c) Rekap Assignment lama di Ruangan yang sudah terarsip tetap bisa dibuka Guru.
        UserPrincipal guruPrincipal = testData.principal(guru);
        List<ReportService.Row> rekap = reportService.recap(assignment.getId(), guruPrincipal);
        assertThat(rekap).hasSize(1);
        assertThat(rekap.get(0).attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-U04: akun baru lahir INVITED dengan undangan tercatat, dan penetapan password mengaktifkannya sekali pakai")
    void ac_u04_undanganDanPenetapanPasswordSekaliPakai() {
        ClientEntity client = testData.client("SD Undangan");
        String email = testData.uniqueEmail("guru.baru");

        AppUserEntity guruBaru = userManagementService.create(client.getId(), email, "Guru Baru", UserRole.GURU);
        assertThat(guruBaru.getStatus()).isEqualTo(UserStatus.INVITED);
        assertFalse(userInvitationRepository
                .findByUserIdAndPurpose(guruBaru.getId(), InvitationPurpose.INVITATION)
                .isEmpty());

        // Token mentah tidak pernah tersimpan di database (hanya hash-nya) — satu-satunya
        // tempat ia ada adalah tautan yang dikirim lewat NotificationPort, jadi tangkap di sana.
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).sendInvitation(anyString(), anyString(), urlCaptor.capture());
        String activationUrl = urlCaptor.getValue();
        String token = activationUrl.substring(activationUrl.lastIndexOf('/') + 1);

        boolean redeemed = invitationService.redeem(token, InvitationPurpose.INVITATION, "passwordbaru123");
        assertTrue(redeemed);
        AppUserEntity setelahRedeem = userManagementService.require(guruBaru.getId(), client.getId());
        assertThat(setelahRedeem.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Token sekali pakai: pemakaian kedua harus gagal, bukan diam-diam berhasil lagi.
        boolean redeemedLagi = invitationService.redeem(token, InvitationPurpose.INVITATION, "passwordlain456");
        assertFalse(redeemedLagi);

        // Endpoint reset password tidak boleh jadi alat memeriksa keberadaan sebuah email:
        // memintanya untuk alamat yang tidak ada harus diam saja, bukan melempar galat.
        invitationService.requestPasswordReset("tidak-ada-" + testData.uniqueEmail("hantu"));
        verify(notificationPort, never()).sendPasswordReset(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("AC-P01: Guru yang hanya ditugaskan di Ruangan 4A tidak bisa membuat draf ke Ruangan 4B milik Client yang sama (404, bukan 403)")
    void ac_p01_guruHanyaBisaMenyasarRuanganTempatIaDitugaskan() {
        ClientEntity client = testData.client("SD Penugasan");
        AppUserEntity guruA = testData.user(client, UserRole.GURU, "Guru A");
        RuanganEntity ruangan4A = testData.ruangan(client, "4A");
        RuanganEntity ruangan4B = testData.ruangan(client, "4B");
        // Guru A HANYA ditugaskan di 4A; 4B adalah Ruangan Client yang sama tetapi bukan miliknya.
        testData.join(ruangan4A, guruA, MemberRole.GURU);

        TopicEntity topic = testData.topic(client, "Matematika Kelas 4", "Aljabar");
        ExerciseEntity exercise = testData.exercise(client, guruA, "Ulangan Penugasan",
                List.of(testData.mcq(client, topic, "Soal penugasan", 4)));

        UserPrincipal guruAPrincipal = testData.principal(guruA);
        PublishRequest request = new PublishRequest(exercise.getId(), ruangan4B.getId(), "Ulangan ke 4B",
                AssignmentMode.QUIZ, 60, OffsetDateTime.now().plusDays(1), 3, false, false,
                RevealAnswersAt.AFTER_SUBMIT);

        // Ruangan yang bukan miliknya harus tidak bisa dibedakan dari Ruangan yang tidak ada
        // sama sekali (TC-09) — karena itu 404, bukan 403.
        assertThrows(ResourceNotFoundException.class,
                () -> publishingService.createDraft(request, guruAPrincipal));
    }
}
