package com.eduscreen.app.support;

import com.eduscreen.app.modules.assessment.domain.AssignmentMode;
import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.RevealAnswersAt;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionOptionRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganMemberEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganMemberRepository;
import com.eduscreen.app.modules.assessment.repository.RuanganRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.shared.security.UserPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Perakit data uji.
 *
 * <p>Ada supaya setiap kelas tes tidak menulis ulang tujuh belas baris penyiapan yang sama, dan
 * — lebih penting — supaya seluruh tes memakai bentuk data yang identik. Tes IDOR khususnya
 * bergantung pada adanya <b>dua</b> Client yang benar-benar terpisah; membangunnya ad hoc di
 * tiap kelas adalah cara paling mudah menghasilkan tes yang lulus karena datanya kebetulan
 * ramah, bukan karena kodenya benar.
 */
@Component
public class TestData {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * Timer yang begitu panjang sehingga {@code expiresAt} selalu yang mengikat.
     *
     * <p>BR-M03 mewajibkan setiap QUIZ punya Timer, dan database menegakkannya lewat
     * {@code assignment_quiz_needs_timer} — QUIZ terbit tanpa Timer adalah ujian tanpa batas
     * pengerjaan. Tes yang ingin membuktikan perilaku Global Expiration karena itu tidak boleh
     * mengosongkan Timer; ia memakai Timer yang pasti kalah di {@code min()}.
     */
    public static final int TIMER_TAK_MENGIKAT = 100_000;

    private final ClientRepository clients;
    private final AppUserRepository users;
    private final RuanganRepository ruangan;
    private final RuanganMemberRepository members;
    private final SubjectRepository subjects;
    private final PaketRepository pakets;
    private final TopicRepository topics;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final ExerciseRepository exercises;
    private final ExerciseItemRepository exerciseItems;
    private final AssignmentRepository assignments;

    public TestData(ClientRepository clients,
                    AppUserRepository users,
                    RuanganRepository ruangan,
                    RuanganMemberRepository members,
                    SubjectRepository subjects,
                    PaketRepository pakets,
                    TopicRepository topics,
                    QuestionRepository questions,
                    QuestionOptionRepository options,
                    ExerciseRepository exercises,
                    ExerciseItemRepository exerciseItems,
                    AssignmentRepository assignments) {
        this.clients = clients;
        this.users = users;
        this.ruangan = ruangan;
        this.members = members;
        this.subjects = subjects;
        this.pakets = pakets;
        this.topics = topics;
        this.questions = questions;
        this.options = options;
        this.exercises = exercises;
        this.exerciseItems = exerciseItems;
        this.assignments = assignments;
    }

    /** Email unik per pemanggilan; keunikan email bersifat global (V1). */
    public String uniqueEmail(String prefix) {
        return prefix + COUNTER.incrementAndGet() + "@uji.sch.id";
    }

    @Transactional
    public ClientEntity client(String name) {
        return clients.save(new ClientEntity(name + " " + COUNTER.incrementAndGet(),
                ZoneId.of("Asia/Jakarta")));
    }

    @Transactional
    public AppUserEntity user(ClientEntity client, UserRole role, String fullName) {
        AppUserEntity user = new AppUserEntity(
                role == UserRole.EDUSCREEN_ADMIN ? null : client.getId(),
                uniqueEmail(role.name().toLowerCase()), fullName, role);
        user.setStatus(UserStatus.ACTIVE);
        return users.save(user);
    }

    @Transactional
    public RuanganEntity ruangan(ClientEntity client, String name) {
        return ruangan.save(new RuanganEntity(client.getId(), name));
    }

    @Transactional
    public void join(RuanganEntity room, AppUserEntity user, MemberRole role) {
        members.save(new RuanganMemberEntity(room.getClientId(), room.getId(), user.getId(), role));
    }

    /**
     * Satu Topic milik Client, lengkap dengan Subject dan Paket yang menaunginya.
     *
     * <p>Sejak ADR-0018 Topic tidak berdiri sendiri: ia butuh induk Paket. Helper ini karena itu
     * membuat Paket sewadah bernama sama, sehingga tes lama yang hanya peduli pada satu Topic
     * tetap terbaca apa adanya.
     */
    @Transactional
    public TopicEntity topic(ClientEntity client, String subjectName, String topicName) {
        SubjectEntity subject = subjects.save(SubjectEntity.forClient(client.getId(), subjectName));
        PaketEntity paket = pakets.save(
                PaketEntity.forClient(client.getId(), subject.getId(), topicName, null));
        return topics.save(TopicEntity.of(paket.getId(), topicName, 0));
    }

    /** Subject yang menaungi sebuah Topic, diturunkan dari Paket induknya (ADR-0018). */
    public UUID subjectIdOf(TopicEntity topic) {
        return pakets.findById(topic.getPaketId()).orElseThrow().getSubjectId();
    }

    /** Paket yang menaungi sebuah Topic. */
    public PaketEntity paketOf(TopicEntity topic) {
        return pakets.findById(topic.getPaketId()).orElseThrow();
    }

    /**
     * Soal pilihan ganda dengan {@code optionCount} pilihan; pilihan pertama yang benar.
     *
     * <p>Kolom {@code *_text} diisi apa adanya: fixture ini tidak melewati jalur sanitasi karena
     * yang diuji tes-tes ini bukan sanitasinya.
     */
    @Transactional
    public QuestionEntity mcq(ClientEntity client, TopicEntity topic, String body, int optionCount) {
        QuestionEntity question = questions.save(new QuestionEntity(
                client.getId(), topic.getPaketId(), topic.getId(), QuestionType.MULTIPLE_CHOICE,
                "<p>" + body + "</p>", body));
        for (int i = 0; i < optionCount; i++) {
            QuestionOptionEntity option = new QuestionOptionEntity(
                    question.getId(), "<p>Pilihan " + i + "</p>", "Pilihan " + i, i == 0, i);
            options.save(option);
        }
        return question;
    }

    /** Varian bersama pembahasan; Practice mewajibkannya (BR-Q03). */
    @Transactional
    public QuestionEntity mcqWithExplanation(ClientEntity client, TopicEntity topic, String body) {
        QuestionEntity question = mcq(client, topic, body, 4);
        question.setExplanationHtml("<p>Pembahasan " + body + "</p>");
        question.setExplanationText("Pembahasan " + body);
        return questions.save(question);
    }

    @Transactional
    public QuestionEntity essay(ClientEntity client, TopicEntity topic, String body) {
        return questions.save(new QuestionEntity(
                client.getId(), topic.getPaketId(), topic.getId(), QuestionType.ESSAY,
                "<p>" + body + "</p>", body));
    }

    public UUID correctOptionOf(QuestionEntity question) {
        return options.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                .filter(QuestionOptionEntity::isCorrect)
                .findFirst().orElseThrow().getId();
    }

    public UUID wrongOptionOf(QuestionEntity question) {
        return options.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                .filter(option -> !option.isCorrect())
                .findFirst().orElseThrow().getId();
    }

    @Transactional
    public ExerciseEntity exercise(ClientEntity client, AppUserEntity author,
                                   String title, List<QuestionEntity> content) {
        ExerciseEntity exercise = exercises.save(new ExerciseEntity(client.getId(), title, author.getId()));
        int position = 0;
        for (QuestionEntity question : content) {
            exerciseItems.save(new ExerciseItemEntity(exercise.getId(), question.getId(), position++));
        }
        return exercise;
    }

    /**
     * Assignment yang sudah {@code PUBLISHED}, dibuat langsung lewat repository.
     *
     * <p>Melewati {@code AssignmentPublishingService} dengan sengaja: tes yang menyiapkan
     * keadaan awal tidak boleh ikut menguji gerbang validasi penerbitan, dan beberapa skenario
     * justru butuh keadaan yang gerbang itu tolak — misalnya batas akhir yang sudah lewat.
     */
    @Transactional
    public AssignmentEntity publishedQuiz(ClientEntity client,
                                          ExerciseEntity exercise,
                                          RuanganEntity room,
                                          AppUserEntity guru,
                                          Integer timerMinutes,
                                          OffsetDateTime expiresAt,
                                          int maxAttempts) {
        AssignmentEntity assignment = new AssignmentEntity(
                client.getId(), exercise.getId(), room.getId(), guru.getId(),
                AssignmentMode.QUIZ, "Ulangan " + COUNTER.incrementAndGet(), timerMinutes,
                expiresAt, maxAttempts, false, false, RevealAnswersAt.AFTER_SUBMIT);
        assignment.publish(OffsetDateTime.now());
        return assignments.save(assignment);
    }

    @Transactional
    public AssignmentEntity publishedPractice(ClientEntity client,
                                              ExerciseEntity exercise,
                                              RuanganEntity room,
                                              AppUserEntity guru,
                                              OffsetDateTime expiresAt) {
        AssignmentEntity assignment = new AssignmentEntity(
                client.getId(), exercise.getId(), room.getId(), guru.getId(),
                AssignmentMode.PRACTICE, "Latihan " + COUNTER.incrementAndGet(), null,
                expiresAt, 1, false, false, RevealAnswersAt.AFTER_SUBMIT);
        assignment.publish(OffsetDateTime.now());
        return assignments.save(assignment);
    }

    // ------------------------------------------------------------ konten master
    //
    // Konten master hidup di baris ber-clientId null. Fixture-nya sengaja terpisah dari fixture
    // Client: satu-satunya perbedaan struktural adalah kepemilikan dan keadaan terbit, dan
    // menyatukannya lewat parameter boolean akan menyembunyikan justru yang sedang diuji.

    /** Eduscreen Admin: satu-satunya peran tanpa Client (V1 app_user_tenant_boundary). */
    @Transactional
    public AppUserEntity eduscreenAdmin() {
        AppUserEntity user = new AppUserEntity(null, uniqueEmail("eduscreen"),
                "Eduscreen Admin", UserRole.EDUSCREEN_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return users.save(user);
    }

    /** Subject GLOBAL, Paket master, dan satu Topic di dalamnya (BR-O02, ADR-0018). */
    @Transactional
    public TopicEntity globalTopic(String subjectName, String topicName) {
        SubjectEntity subject = subjects.save(SubjectEntity.global(
                subjectName + " " + COUNTER.incrementAndGet()));
        PaketEntity paket = pakets.save(PaketEntity.master(subject.getId(), topicName, null));
        return topics.save(TopicEntity.of(paket.getId(), topicName, 0));
    }

    /** Question master yang masih digarap: {@code publishedAt} kosong (FR-066). */
    @Transactional
    public QuestionEntity masterMcq(TopicEntity topic, String body) {
        QuestionEntity question = questions.save(new QuestionEntity(
                null, topic.getPaketId(), topic.getId(), QuestionType.MULTIPLE_CHOICE,
                "<p>" + body + "</p>", body));
        for (int i = 0; i < 4; i++) {
            options.save(new QuestionOptionEntity(question.getId(),
                    "<p>Pilihan " + i + "</p>", "Pilihan " + i, i == 0, i));
        }
        return question;
    }

    /** Question master yang sudah terbit dan karena itu terlihat di katalog seluruh Client. */
    @Transactional
    public QuestionEntity publishedMasterMcq(TopicEntity topic, String body) {
        QuestionEntity question = masterMcq(topic, body);
        question.publish(OffsetDateTime.now());
        return questions.save(question);
    }

    /** Paket master: Exercise ber-clientId null beserta itemnya. */
    @Transactional
    public ExerciseEntity masterExercise(String title, List<QuestionEntity> content) {
        ExerciseEntity exercise = exercises.save(new ExerciseEntity(null, title, null));
        int position = 0;
        for (QuestionEntity question : content) {
            exerciseItems.save(new ExerciseItemEntity(exercise.getId(), question.getId(), position++));
        }
        return exercise;
    }

    public UserPrincipal principal(AppUserEntity user) {
        return new UserPrincipal(user.getId(), user.getClientId(), user.getEmail(),
                user.getFullName(), user.getRole());
    }

    /**
     * Dua Client yang benar-benar terpisah, lengkap dengan Guru, Siswa, Ruangan, dan satu
     * Assignment masing-masing. Dasar seluruh tes IDOR (TC-41).
     */
    @Transactional
    public Tenants twoTenants() {
        List<Tenant> built = new ArrayList<>();
        for (String name : List.of("SD Alfa", "SD Beta")) {
            ClientEntity client = client(name);
            AppUserEntity guru = user(client, UserRole.GURU, "Guru " + name);
            AppUserEntity siswa = user(client, UserRole.SISWA, "Siswa " + name);
            AppUserEntity siswaLain = user(client, UserRole.SISWA, "Siswa lain " + name);
            AppUserEntity admin = user(client, UserRole.CLIENT_ADMIN, "Admin " + name);
            RuanganEntity room = ruangan(client, "Kelas 4B " + name);
            join(room, guru, MemberRole.GURU);
            join(room, siswa, MemberRole.SISWA);
            join(room, siswaLain, MemberRole.SISWA);

            TopicEntity topic = topic(client, "Matematika Kelas 4", "Aljabar");
            List<QuestionEntity> content = List.of(
                    mcq(client, topic, "Soal 1 " + name, 4),
                    mcq(client, topic, "Soal 2 " + name, 4));
            ExerciseEntity exercise = exercise(client, guru, "Ulangan " + name, content);
            AssignmentEntity assignment = publishedQuiz(client, exercise, room, guru,
                    60, OffsetDateTime.now().plusDays(1), 3);

            built.add(new Tenant(client, admin, guru, siswa, siswaLain, room, topic, content,
                    exercise, assignment));
        }
        return new Tenants(built.get(0), built.get(1));
    }

    /** Satu Client lengkap beserta pemerannya. */
    public record Tenant(ClientEntity client,
                         AppUserEntity admin,
                         AppUserEntity guru,
                         AppUserEntity siswa,
                         AppUserEntity siswaLain,
                         RuanganEntity ruangan,
                         TopicEntity topic,
                         List<QuestionEntity> questions,
                         ExerciseEntity exercise,
                         AssignmentEntity assignment) {
    }

    public record Tenants(Tenant a, Tenant b) {
    }
}
