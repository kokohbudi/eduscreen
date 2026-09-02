package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.ResultRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerRepository;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.SupportAccessGrantEntity;
import com.eduscreen.app.modules.assessment.repository.SupportAccessGrantRepository;
import com.eduscreen.app.modules.assessment.repository.SupportAccessReadEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AnswerService;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ReportService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.modules.assessment.service.SupportAccessService;
import com.eduscreen.app.modules.identity.service.UserManagementService;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cakupan kriteria penerimaan lintas Client dan riwayat: jendela dukungan break-glass
 * (AC-P05), akun yang dinonaktifkan tanpa kehilangan riwayat nilainya (AC-U03), dan Result
 * historis yang tidak pernah dihitung ulang saat dibaca (AC-T08).
 */
class TenancyAndAuditIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    SupportAccessService supportAccess;
    @Autowired
    SupportAccessGrantRepository grants;
    @Autowired
    UserManagementService userManagement;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    AnswerService answerService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    ReportService reportService;
    @Autowired
    ResultRepository results;
    @Autowired
    SessionAnswerRepository sessionAnswers;

    @Test
    @DisplayName("AC-P05 (BR-P05, TC-46): jendela dukungan bisa dibaca dan tercatat selama aktif, ditolak begitu dicabut, dan padam sendiri saat kedaluwarsa")
    void acP05SupportAccessWindowGrantReadRevokeExpire() {
        ClientEntity client = data.client("SD Dukungan1");
        AppUserEntity admin = data.user(client, UserRole.CLIENT_ADMIN, "Admin Client");
        AppUserEntity eduscreenAdmin = data.user(client, UserRole.EDUSCREEN_ADMIN, "Admin Eduscreen");

        SupportAccessGrantEntity grant = supportAccess.grant(client.getId(), admin.getId());

        // (a) jendela aktif segera terlihat lewat activeGrant, dan panjangnya persis 4 jam —
        // toleransi beberapa detik hanya menampung pembulatan presisi timestamptz saat nilainya
        // bolak-balik lewat database, bukan karena jendelanya memang boleh melenceng.
        assertThat(supportAccess.activeGrant(client.getId())).isPresent();
        long selisihDetik = Duration.between(grant.getGrantedAt(), grant.getExpiresAt()).toSeconds();
        assertThat(selisihDetik).isBetween(Duration.ofHours(4).toSeconds() - 5, Duration.ofHours(4).toSeconds() + 5);

        // (b) satu pembacaan tercatat dan bisa dibaca lewat trail — bukti "baca-saja berbatas
        // waktu" yang bisa ditunjukkan kepada Client, bukan sekadar janji di dokumentasi.
        supportAccess.recordRead(client.getId(), eduscreenAdmin.getId(), "bank soal");
        List<SupportAccessReadEntity> jejak = supportAccess.trail(client.getId());
        assertThat(jejak).hasSize(1);
        assertThat(jejak.get(0).getResource()).isEqualTo("bank soal");
        assertThat(jejak.get(0).getReadBy()).isEqualTo(eduscreenAdmin.getId());

        // (c) setelah dicabut, jendelanya padam dan permintaan baca berikutnya mendapat 404 —
        // tanpa jendela aktif, itu bukan "akses tanpa catatan" melainkan permintaan yang memang
        // ditolak (tanda "—" pada matriks izin berlaku penuh).
        supportAccess.revoke(client.getId());
        assertThat(supportAccess.activeGrant(client.getId())).isEmpty();
        assertThatThrownBy(() -> supportAccess.recordRead(client.getId(), eduscreenAdmin.getId(), "bank soal"))
                .isInstanceOf(ResourceNotFoundException.class);

        // (d) jejak yang sudah tertulis SEBELUM pencabutan tetap terbaca sesudahnya — tabelnya
        // hanya-sisip, dan mencabut akses tidak pernah berarti menghapus riwayat pembacaan.
        assertThat(supportAccess.trail(client.getId())).hasSize(1);

        // Kedaluwarsa otomatis: grant dibuat langsung lewat SupportAccessGrantRepository dengan
        // expiresAt yang sudah lewat, sengaja melewati SupportAccessService.grant() yang selalu
        // menghitung expiresAt dari "sekarang" — satu-satunya cara menaruh jam yang jendelanya
        // sudah habis tanpa benar-benar menunggu 4 jam.
        ClientEntity client2 = data.client("SD Dukungan2");
        AppUserEntity admin2 = data.user(client2, UserRole.CLIENT_ADMIN, "Admin Client 2");
        OffsetDateTime now = OffsetDateTime.now();
        grants.save(new SupportAccessGrantEntity(client2.getId(), admin2.getId(),
                now.minusHours(5), now.minusHours(1)));
        // Tidak ada satu baris kode pun yang "mematikan" grant ini secara eksplisit — activeGrant
        // kosong murni karena expiresAt sudah lewat, buktinya jendela ini padam tanpa tindakan siapa pun.
        assertThat(supportAccess.activeGrant(client2.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-U03 (BR-U03): akun Siswa yang dinonaktifkan tetap meninggalkan Result dan sesinya utuh di laporan")
    void acU03DeactivatedAccountKeepsResultsInReport() {
        ClientEntity client = data.client("SD Akun1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa Pindah Sekolah");
        RuanganEntity room = data.ruangan(client, "Kelas 5A");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "IPS Kelas 5", "Sejarah");
        List<QuestionEntity> content = List.of(data.mcq(client, topic, "Soal Sejarah", 4));
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Sejarah", content);
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                TestData.TIMER_TAK_MENGIKAT, OffsetDateTime.now().plusDays(1), 1);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        ResultEntity result = finalizer.submit(session.getId(), client.getId());

        userManagement.deactivate(siswa.getId(), client.getId());

        // Statusnya yang berubah, bukan barisnya yang hilang: nilai seorang Siswa yang pindah
        // sekolah tetap harus bisa ditunjukkan tahun berikutnya, jadi yang hilang hanya
        // kemampuan login-nya.
        AppUserEntity siswaSetelahNonaktif = userManagement.require(siswa.getId(), client.getId());
        assertThat(siswaSetelahNonaktif.getStatus()).isEqualTo(UserStatus.DEACTIVATED);

        assertThat(results.findBySessionId(session.getId())).isPresent();
        assertThat(results.findBySessionId(session.getId()).orElseThrow().getId()).isEqualTo(result.getId());

        // Rekap dibangun dari daftar anggota Ruangan (BR-L01), dan keanggotaan tidak ikut
        // hilang saat akun dinonaktifkan — Siswa itu harus tetap muncul lengkap dengan skornya.
        List<ReportService.Row> rekap = reportService.recap(assignment.getId(), data.principal(guru));
        assertThat(rekap).anySatisfy(row -> {
            assertThat(row.student().getId()).isEqualTo(siswa.getId());
            assertThat(row.officialScore()).isEqualByComparingTo(result.getScore());
        });
    }

    @Test
    @DisplayName("AC-T08 (BR-T09): skor Result yang sudah tersimpan tidak dihitung ulang saat data pendukungnya berubah belakangan")
    void acT08StoredScoreNeverRecalculatedOnRead() {
        ClientEntity client = data.client("SD SkorHistoris1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 6A");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "Matematika Kelas 6", "Geometri");
        QuestionEntity soal = data.mcq(client, topic, "Soal Geometri", 4);
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Geometri", List.of(soal));
        AssignmentEntity assignment = data.publishedQuiz(client, exercise, room, guru,
                TestData.TIMER_TAK_MENGIKAT, OffsetDateTime.now().plusDays(1), 1);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        SessionQuestionEntity satuSatunyaSoal = snapshot.get(0);
        UUID opsiBenar = data.correctOptionOf(soal);
        answerService.save(session.getId(), satuSatunyaSoal.getId(), opsiBenar, null, data.principal(siswa));

        ResultEntity result = finalizer.submit(session.getId(), client.getId());
        BigDecimal skorAwal = result.getScore(); // 1,0000 — satu-satunya soal dijawab benar.

        // Data pendukung diubah SETELAH Result lahir: is_correct jawaban ini dibalik langsung
        // lewat repository, memotong seluruh jalur bisnis (bukan lewat AnswerService, yang
        // memang menolak perubahan pada jawaban yang sudah terkunci di Practice — di sini kita
        // sengaja menembusnya untuk membuktikan Result tidak bergantung padanya). Bila skor
        // pernah dihitung ulang saat dibaca, angka ini akan ikut anjlok — dan itu persis yang
        // tidak boleh terjadi (BR-T09): angka historis tidak boleh bergeser bila aturan atau
        // data pendukungnya berubah di kemudian hari.
        SessionAnswerEntity jawaban = sessionAnswers.findBySessionQuestionId(satuSatunyaSoal.getId())
                .orElseThrow();
        jawaban.recordChoice(jawaban.getSelectedOptionId(), false, OffsetDateTime.now());
        sessionAnswers.save(jawaban);

        ResultEntity dibacaUlang = results.findBySessionId(session.getId()).orElseThrow();
        assertThat(dibacaUlang.getId()).isEqualTo(result.getId());
        assertThat(dibacaUlang.getScore()).isEqualByComparingTo(skorAwal);
    }
}
