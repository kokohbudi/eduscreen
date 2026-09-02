package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService;
import com.eduscreen.app.modules.assessment.service.AssignmentPublishingService.PublishRequest;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.shared.web.UnprocessableException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Menutup lubang cakupan gerbang penerbitan (BR-M01 sampai BR-M07), penguncian Exercise pada
 * penerbitan pertama (BR-E02 sampai BR-E04), dan interaksinya dengan Global Expiration Practice
 * (BR-T05).
 */
class AssignmentPublishingIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    AssignmentPublishingService publishing;
    @Autowired
    ExerciseService exerciseService;
    @Autowired
    ExerciseRepository exercises;
    @Autowired
    RuanganService ruanganService;
    @Autowired
    ExamSessionService examSessionService;

    @Test
    @DisplayName("AC-M02: publishBulk ke tiga Ruangan menghasilkan tiga Assignment terpisah dengan id berbeda")
    void publishBulkCreatesOneAssignmentPerRuangan() {
        ClientEntity client = data.client("SD Publish1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room4A = data.ruangan(client, "Kelas 4A");
        RuanganEntity room4B = data.ruangan(client, "Kelas 4B");
        RuanganEntity room4C = data.ruangan(client, "Kelas 4C");
        data.join(room4A, guru, MemberRole.GURU);
        data.join(room4B, guru, MemberRole.GURU);
        data.join(room4C, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Matematika", "Bilangan");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Bilangan",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        PublishRequest template = new PublishRequest(
                exercise.getId(), null, "Ulangan Bilangan", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 3, false, false, RevealAnswersAt.AFTER_SUBMIT);

        List<AssignmentEntity> published = publishing.publishBulk(
                template, List.of(room4A.getId(), room4B.getId(), room4C.getId()), data.principal(guru));

        // Satu Exercise, tiga Ruangan tujuan: BR-M02 mensyaratkan tiga Assignment BERDIRI SENDIRI
        // (id berbeda, expiresAt/penutupan/rekap masing-masing terpisah), bukan satu Assignment
        // yang menyasar banyak Ruangan sekaligus.
        assertThat(published).hasSize(3);
        assertThat(published).allMatch(AssignmentEntity::isPublished);
        Set<UUID> ruanganIds = published.stream().map(AssignmentEntity::getRuanganId).collect(Collectors.toSet());
        assertThat(ruanganIds).containsExactlyInAnyOrder(room4A.getId(), room4B.getId(), room4C.getId());
        Set<UUID> assignmentIds = published.stream().map(AssignmentEntity::getId).collect(Collectors.toSet());
        assertThat(assignmentIds).hasSize(3);
    }

    @Test
    @DisplayName("AC-M03: Guru hanya bisa menerbitkan ke Ruangan ACTIVE tempat ia ditugaskan; ARCHIVED dan milik Guru lain sama-sama 404")
    void publishTargetLimitedToOwnActiveRuangan() {
        ClientEntity client = data.client("SD Publish2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room4A = data.ruangan(client, "4A");
        RuanganEntity room3C = data.ruangan(client, "3C");
        RuanganEntity room4B = data.ruangan(client, "4B");
        data.join(room4A, guru, MemberRole.GURU);
        data.join(room3C, guru, MemberRole.GURU);
        ruanganService.archive(room3C.getId(), client.getId());
        // room4B sengaja TIDAK diikuti guru: mensimulasikan Ruangan Guru lain di Client yang sama.

        TopicEntity topic = data.topic(client, "IPA", "Tata Surya");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Tata Surya",
                List.of(data.mcq(client, topic, "Soal 1", 4)));
        UserPrincipal guruPrincipal = data.principal(guru);

        PublishRequest toArchived = new PublishRequest(
                exercise.getId(), room3C.getId(), "Ulangan", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT);
        PublishRequest toForeign = new PublishRequest(
                exercise.getId(), room4B.getId(), "Ulangan", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT);
        PublishRequest toOwn = new PublishRequest(
                exercise.getId(), room4A.getId(), "Ulangan", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT);

        // 404, bukan 403, untuk KEDUANYA: Ruangan terarsip dan Ruangan Guru lain harus tampak
        // identik dengan Ruangan yang tidak ada sama sekali (TC-09). Membalas 403 pada Ruangan
        // 4B akan membocorkan bahwa "4B itu ada, hanya saja bukan milikmu" — celah enumerasi
        // yang justru dicegah requireOwnRuangan().
        assertThatThrownBy(() -> publishing.createDraft(toArchived, guruPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> publishing.createDraft(toForeign, guruPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);

        AssignmentEntity draft = publishing.createDraft(toOwn, guruPrincipal);
        assertThat(draft.getRuanganId()).isEqualTo(room4A.getId());
    }

    @Test
    @DisplayName("AC-M04: QUIZ tanpa Timer dan expiresAt lampau ditolak; PRACTICE tanpa Timer diterima; maxAttempts=0 dijaga sebelum sampai ke gerbang")
    void publishGateRejectsQuizWithoutTimerAndPastExpiryButAcceptsPracticeWithoutTimer() {
        ClientEntity client = data.client("SD Publish3");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 5A");
        data.join(room, guru, MemberRole.GURU);
        UserPrincipal guruPrincipal = data.principal(guru);

        TopicEntity topic = data.topic(client, "Bahasa", "Pantun");
        // mcqWithExplanation, bukan mcq biasa: Exercise yang sama dipakai lagi di bawah untuk
        // menerbitkan PRACTICE, dan BR-Q03 menolak Practice yang soalnya tanpa pembahasan. Timer
        // dan Practice adalah dua gerbang yang berbeda (BR-M03 vs BR-Q03) — memakai mcq() biasa
        // akan membuat penolakan Practice tanpa Timer gagal karena alasan yang SALAH.
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Pantun",
                List.of(data.mcqWithExplanation(client, topic, "Soal 1")));

        // BR-M03: Quiz tanpa Timer adalah ujian tanpa batas pengerjaan.
        AssignmentEntity quizNoTimer = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Quiz tanpa timer", AssignmentMode.QUIZ,
                null, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        assertThatThrownBy(() -> publishing.publish(quizNoTimer.getId(), guruPrincipal))
                .isInstanceOf(UnprocessableException.class);

        // BR-M05: batas akhir di masa lalu tidak masuk akal sebagai jadwal baru.
        AssignmentEntity pastExpiry = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Quiz kedaluwarsa", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().minusHours(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        assertThatThrownBy(() -> publishing.publish(pastExpiry.getId(), guruPrincipal))
                .isInstanceOf(UnprocessableException.class);

        // BR-M03 hanya mewajibkan Timer untuk QUIZ; PRACTICE lolos gerbang yang sama tanpa Timer.
        AssignmentEntity practiceNoTimer = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Practice tanpa timer", AssignmentMode.PRACTICE,
                null, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        AssignmentEntity publishedPractice = publishing.publish(practiceNoTimer.getId(), guruPrincipal);
        assertThat(publishedPractice.isPublished()).isTrue();

        // BR-M06: maxAttempts=0 TIDAK sampai ke gerbang publish() sebagai penolakan — ia sudah
        // dijinakkan lebih awal oleh createDraft() lewat Math.max(request.maxAttempts(), 1)
        // (AssignmentPublishingService baris ~95). Tes yang menuntut UnprocessableException di
        // sini akan gagal karena bukan itu yang terjadi; kejujuran pada perilaku sebenarnya lebih
        // berguna daripada tes yang lulus karena mengasumsikan perilaku yang tidak ada. Lapisan
        // terakhirnya tetap database: constraint assignment_max_attempts_positive (check
        // max_attempts >= 1 di V3__assessment.sql) membuat 0 mustahil tersimpan sama sekali,
        // seandainya suatu saat jalur lain melewati Math.max ini.
        AssignmentEntity zeroAttempts = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Attempts nol", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 0, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        assertThat(zeroAttempts.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-M05: Exercise terkunci pada saat yang sama dengan penerbitan pertama Assignment-nya")
    void exerciseLocksAtSameMomentAsFirstPublish() {
        ClientEntity client = data.client("SD Publish4");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 5B");
        data.join(room, guru, MemberRole.GURU);
        UserPrincipal guruPrincipal = data.principal(guru);

        TopicEntity topic = data.topic(client, "Bahasa", "Cerpen");
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Cerpen",
                List.of(data.mcq(client, topic, "Soal 1", 4)));
        assertThat(exercise.getLockedAt()).isNull();

        AssignmentEntity draft = publishing.createDraft(new PublishRequest(
                exercise.getId(), room.getId(), "Ulangan Cerpen", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);
        AssignmentEntity published = publishing.publish(draft.getId(), guruPrincipal);
        ExerciseEntity relocked = exercises.findByIdAndClientId(exercise.getId(), client.getId()).orElseThrow();

        assertThat(relocked.getLockedAt()).isNotNull();
        assertThat(published.getPublishedAt()).isNotNull();
        // publish() memanggil clock.now() sekali dan memakainya untuk exercise.lock(now) maupun
        // assignment.publish(now) (AssignmentPublishingService baris ~120-123) — keduanya SATU
        // instant yang sama. Membandingkan pada presisi milidetik penuh berisiko rapuh karena
        // pembulatan kolom timestamptz saat pulang-pergi ke PostgreSQL; selisih < 1 detik sudah
        // cukup membuktikan keduanya lahir dari panggilan clock yang sama, bukan dua momen
        // terpisah yang kebetulan berdekatan.
        Duration selisih = Duration.between(relocked.getLockedAt(), published.getPublishedAt()).abs();
        assertThat(selisih.toMillis()).isLessThan(1000L);
    }

    @Test
    @DisplayName("AC-E01: menambah soal ke Exercise terkunci ditolak dengan tawaran duplikasi; duplikatnya baru dan tidak terkunci")
    void addQuestionToLockedExerciseRejectedButDuplicateStaysEditable() {
        ClientEntity client = data.client("SD Publish5");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 5C");
        data.join(room, guru, MemberRole.GURU);

        TopicEntity topic = data.topic(client, "Matematika", "Statistika");
        QuestionEntity q1 = data.mcq(client, topic, "Soal 1", 4);
        QuestionEntity q2 = data.mcq(client, topic, "Soal 2", 4);
        ExerciseEntity exercise = data.exercise(client, guru, "Ulangan Statistika", List.of(q1, q2));
        // Diterbitkan lewat service sungguhan, bukan lewat TestData.publishedQuiz: yang mengunci
        // Exercise adalah langkah penerbitan itu sendiri (BR-M07), dan fixture yang menulis
        // langsung ke repository melewatinya.
        UUID draftId = publishing.createDraft(new AssignmentPublishingService.PublishRequest(
                exercise.getId(), room.getId(), "Ulangan Statistika", AssignmentMode.QUIZ, 30,
                OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                data.principal(guru)).getId();
        publishing.publish(draftId, data.principal(guru));

        QuestionEntity q3 = data.mcq(client, topic, "Soal 3", 4);
        // BR-E04: Exercise yang sudah punya Assignment terbit tidak boleh berubah isinya diam-diam
        // — Siswa mungkin sedang mengerjakan ujian yang menunjuknya. Pesannya harus menawarkan
        // jalan keluar (duplikasi), bukan sekadar melarang.
        assertThatThrownBy(() -> exerciseService.addQuestion(exercise.getId(), q3.getId(), client.getId()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("duplikasi"));

        ExerciseEntity duplicate = exerciseService.duplicate(exercise.getId(), client.getId(), guru.getId());
        assertThat(duplicate.getId()).isNotEqualTo(exercise.getId());
        assertThat(duplicate.getLockedAt()).isNull();
        List<UUID> originalQuestionIds = exerciseService.itemsOf(exercise.getId()).stream()
                .map(ExerciseItemEntity::getQuestionId).toList();
        List<UUID> duplicateQuestionIds = exerciseService.itemsOf(duplicate.getId()).stream()
                .map(ExerciseItemEntity::getQuestionId).toList();
        assertThat(duplicateQuestionIds).containsExactlyElementsOf(originalQuestionIds);
    }

    @Test
    @DisplayName("AC-E03: Exercise Guru A terlihat dan bisa diduplikasi oleh Guru B di Client yang sama")
    void exerciseVisibleAndDuplicableByOtherGuruInSameClient() {
        ClientEntity client = data.client("SD Publish6");
        AppUserEntity guruA = data.user(client, UserRole.GURU, "Guru A");
        AppUserEntity guruB = data.user(client, UserRole.GURU, "Guru B");

        TopicEntity topic = data.topic(client, "Matematika", "Aljabar");
        ExerciseEntity exercise = data.exercise(client, guruA, "Ulangan Harian Aljabar Unik",
                List.of(data.mcq(client, topic, "Soal 1", 4)));

        // BR-E02: tidak ada konten privat per Guru di dalam satu Client — Exercise adalah milik
        // Client, bukan milik pembuatnya, sehingga Guru B harus melihatnya di daftar biasa tanpa
        // filter tambahan apa pun.
        Pageable pageable = PageRequest.of(0, 50);
        var page = exerciseService.list(client.getId(), "Aljabar Unik", pageable);
        assertThat(page.getContent()).extracting(ExerciseEntity::getId).contains(exercise.getId());

        ExerciseEntity duplicate = exerciseService.duplicate(exercise.getId(), client.getId(), guruB.getId());
        assertThat(duplicate.getCreatedBy()).isEqualTo(guruB.getId());
        assertThat(exerciseService.itemsOf(duplicate.getId())).hasSize(1);
    }

    @Test
    @DisplayName("AC-E04: Exercise tanpa satu pun Question ditolak saat publish dengan pesan yang menyebut kekosongannya")
    void publishRejectsExerciseWithoutAnyQuestion() {
        ClientEntity client = data.client("SD Publish7");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        RuanganEntity room = data.ruangan(client, "Kelas 5D");
        data.join(room, guru, MemberRole.GURU);
        UserPrincipal guruPrincipal = data.principal(guru);

        ExerciseEntity kosong = exerciseService.create(client.getId(), "Exercise Kosong", guru.getId());
        assertThat(exerciseService.itemsOf(kosong.getId())).isEmpty();

        AssignmentEntity draft = publishing.createDraft(new PublishRequest(
                kosong.getId(), room.getId(), "Ulangan Kosong", AssignmentMode.QUIZ,
                30, OffsetDateTime.now().plusDays(1), 1, false, false, RevealAnswersAt.AFTER_SUBMIT),
                guruPrincipal);

        // BR-E03: Exercise kosong tidak punya apa pun untuk dikerjakan Siswa; gerbang publish
        // menolaknya sebelum Assignment terbit dengan nol soal.
        assertThatThrownBy(() -> publishing.publish(draft.getId(), guruPrincipal))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("belum berisi satu soal pun"));
    }

    @Test
    @DisplayName("AC-T07: Practice tanpa Timer memberi effectiveDeadline persis sama dengan expiresAt, tanpa pemangkasan durasi")
    void practiceWithoutTimerUsesExpiresAtAsEffectiveDeadlineExactly() {
        ClientEntity client = data.client("SD Publish8");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 5E");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);

        TopicEntity topic = data.topic(client, "Bahasa", "Dongeng");
        ExerciseEntity exercise = data.exercise(client, guru, "Latihan Dongeng",
                List.of(data.mcqWithExplanation(client, topic, "Soal 1")));

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(4);
        AssignmentEntity assignment = data.publishedPractice(client, exercise, room, guru, expiresAt);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));

        // BR-T05: tanpa Timer, expiresAt Assignment adalah SATU-SATUNYA batas — effectiveDeadline
        // harus persis sama dengan expiresAt (isEqual, bukan sekadar "dekat"), karena setiap
        // selisih berarti ada pemangkasan durasi tersembunyi yang tidak diminta siapa pun.
        assertThat(session.getEffectiveDeadline().isEqual(assignment.getExpiresAt())).isTrue();
        assertThat(session.getEffectiveDeadline().isEqual(expiresAt)).isTrue();
    }
}
