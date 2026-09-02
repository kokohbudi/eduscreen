package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AssignmentEntity;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExamSessionEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.ResultEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExamSessionService;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;
import com.eduscreen.app.modules.assessment.service.SessionFinalizer;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cakupan kriteria penerimaan bank soal: aturan Option pada MULTIPLE_CHOICE (AC-Q01),
 * kewajiban Topic (AC-Q04), penghapusan lunak yang tidak menembus Exercise dan sesi yang
 * sedang berjalan (AC-Q02), dan ketiadaan sekat privat per Guru dalam satu Client (AC-P03).
 */
class QuestionBankIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    QuestionService questionService;
    @Autowired
    ExerciseService exerciseService;
    @Autowired
    ExamSessionService examSessionService;
    @Autowired
    SessionFinalizer finalizer;
    @Autowired
    PaketService paketService;

    @Test
    @DisplayName("BR-M04 (BR-E01): panel perakit menyaring tipe soal dan menyembunyikan soal yang sudah terpasang")
    void brM04FiltersBuilderPanel() {
        ClientEntity client = data.client("SD Saring1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Pecahan");

        QuestionEntity pg1 = data.mcq(client, topic, "Saringunik PG 1", 4);
        QuestionEntity pg2 = data.mcq(client, topic, "Saringunik PG 2", 4);
        data.essay(client, topic, "Saringunik esai");

        // Tanpa saringan: ketiganya muncul.
        assertThat(questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), "saringunik", PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(3);

        // Practice hanya boleh memuat pilihan ganda (BR-M04): esai disingkirkan sebelum merakit.
        assertThat(questionService.searchForBuilder(
                client.getId(), null, null, QuestionType.MULTIPLE_CHOICE, List.of(), "saringunik",
                PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);

        // Yang sudah terpasang disembunyikan; pengecualian kosong tidak menyaring apa pun.
        ExerciseEntity exercise = exerciseService.create(client.getId(), "Ulangan", guru.getId());
        exerciseService.addQuestions(exercise.getId(), List.of(pg1.getId()), client.getId());
        assertThat(questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(pg1.getId()), "saringunik", PageRequest.of(0, 20)))
                .extracting(QuestionEntity::getId)
                .doesNotContain(pg1.getId())
                .contains(pg2.getId());
    }

    /**
     * Sengaja BUKAN pengenal BR-E01: yang dibuktikan di sini adalah query filter-nya sendiri
     * (soal Paket A muncul, soal Paket B tidak), bukan kelonggaran BR-E01 yang justru soal
     * penambahan lintas-Paket — itu diuji terpisah lewat
     * {@code ExerciseBuilderRenderTest#brE01TambahSoalDariPaketLainSaatPanelMenyaringPaketLain}
     * (temuan review Task 12). Business-rules.md belum punya kriteria untuk "panel penelusuran
     * menyaring per Paket", jadi AC-E05 ditambahkan langsung ke sana (bukan hanya diusulkan di
     * laporan), mengikuti pola yang sama seperti AC-B16/AC-B17 di Task 10.
     */
    @Test
    @DisplayName("AC-E05: panel perakit menyaring hasil pencarian ke Paket yang dipilih, dan Paket lain tidak ikut tampil")
    void builderFiltersByPaket() {
        ClientEntity client = data.client("SD Perakit Paket");
        PaketEntity paketA = data.paket(client, "Matematika Kelas 4 Perakit", "Paket A");
        PaketEntity paketB = data.paket(client, "Matematika Kelas 4 Perakit", "Paket B");
        TopicEntity topicA = paketService.topicsOf(paketA.getId()).get(0);
        TopicEntity topicB = paketService.topicsOf(paketB.getId()).get(0);
        data.mcq(client, topicA, "Soal di Paket A", 4);
        data.mcq(client, topicB, "Soal di Paket B", 4);

        Page<QuestionEntity> hasil = questionService.searchForBuilder(
                client.getId(), paketA.getId(), null, null, List.of(), null,
                PageRequest.of(0, 20));

        assertThat(hasil.getContent())
                .extracting(QuestionEntity::getBodyText)
                .contains("Soal di Paket A")
                .doesNotContain("Soal di Paket B");
    }

    /**
     * Klausa yang menutup jalan di sini adalah {@code q.clientId = :clientId} pada
     * {@code searchForBuilder} — bukan pemeriksaan kepemilikan {@code paketId} tersendiri, yang
     * memang sengaja tidak ada (lihat javadoc {@code QuestionRepository.searchForBuilder}).
     * Dibuktikan lumpuh-pulih di laporan Task 12: klausa {@code clientId} dilumpuhkan sementara,
     * tes ini gagal (soal Client lain ikut muncul), lalu dipulihkan.
     */
    @Test
    @DisplayName("TC-36: paketId milik Client lain di panel perakit menghasilkan nol hasil, bukan galat")
    void builderPaketMilikClientLainMenghasilkanNolHasil() {
        ClientEntity client = data.client("SD Perakit Sendiri");
        ClientEntity lain = data.client("SD Perakit Lain");
        PaketEntity paketLain = data.paket(lain, "Matematika Kelas 4 Perakit Lain", "Paket Lain");
        TopicEntity topicLain = paketService.topicsOf(paketLain.getId()).get(0);
        data.mcq(lain, topicLain, "Soal milik sekolah lain untuk perakit", 4);

        // paketId sah milik Client B disodorkan sambil clientId yang dipakai tetap Client A —
        // skenario paling dekat dengan penyerang yang menebak/menyalin id Paket orang lain.
        Page<QuestionEntity> hasil = questionService.searchForBuilder(
                client.getId(), paketLain.getId(), null, null, List.of(), null,
                PageRequest.of(0, 20));

        assertThat(hasil.getContent()).isEmpty();
    }

    @Test
    @DisplayName("BR-E01: menambah beberapa soal terpilih sekaligus menghormati urutan centang dan melewati soal milik Client lain")
    void brE01AddsSelectedQuestionsInOneAction() {
        ClientEntity client = data.client("SD Terpilih1");
        ClientEntity lain = data.client("SD Terpilih2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Pecahan");
        TopicEntity topicLain = data.topic(lain, "Matematika Kelas 4", "Pecahan");

        QuestionEntity a = data.mcq(client, topic, "Soal A", 4);
        QuestionEntity b = data.mcq(client, topic, "Soal B", 4);
        QuestionEntity c = data.mcq(client, topic, "Soal C", 4);
        QuestionEntity asing = data.mcq(lain, topicLain, "Soal sekolah lain", 4);

        ExerciseEntity exercise = exerciseService.create(client.getId(), "Ulangan", guru.getId());

        // Urutan item mengikuti urutan yang dikirim, bukan urutan tulis di bank soal.
        assertThat(exerciseService.addQuestions(exercise.getId(), List.of(c.getId(), a.getId()), client.getId()))
                .isEqualTo(2);
        assertThat(exerciseService.itemsOf(exercise.getId()))
                .extracting(ExerciseItemEntity::getQuestionId)
                .containsExactly(c.getId(), a.getId());

        // Soal yang sudah terpasang dilewati; soal milik Client lain tidak pernah masuk (TC-36).
        assertThat(exerciseService.addQuestions(
                exercise.getId(), List.of(a.getId(), b.getId(), asing.getId()), client.getId())).isEqualTo(1);
        assertThat(exerciseService.itemsOf(exercise.getId()))
                .extracting(ExerciseItemEntity::getQuestionId)
                .containsExactly(c.getId(), a.getId(), b.getId());

        assertThat(exerciseService.addQuestions(exercise.getId(), List.of(), client.getId())).isZero();
    }

    @Test
    @DisplayName("BR-E01: menambah satu Topic penuh memasukkan seluruh soalnya, melewati yang sudah terpasang dan yang milik Client lain")
    void brE01AddsAnEntireTopicAtOnce() {
        ClientEntity client = data.client("SD Topik1");
        ClientEntity lain = data.client("SD Topik2");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        TopicEntity pecahan = data.topic(client, "Matematika Kelas 4", "Pecahan");
        TopicEntity geometri = data.topic(client, "Matematika Kelas 4", "Geometri");
        TopicEntity pecahanLain = data.topic(lain, "Matematika Kelas 4", "Pecahan");

        QuestionEntity p1 = data.mcq(client, pecahan, "Pecahan 1", 4);
        QuestionEntity p2 = data.mcq(client, pecahan, "Pecahan 2", 4);
        data.mcq(client, geometri, "Geometri 1", 4);
        data.mcq(lain, pecahanLain, "Pecahan milik sekolah lain", 4);

        ExerciseEntity exercise = exerciseService.create(client.getId(), "Ulangan Pecahan", guru.getId());
        exerciseService.addQuestion(exercise.getId(), p1.getId(), client.getId());

        // p1 sudah terpasang, jadi hanya p2 yang benar-benar baru.
        assertThat(exerciseService.addTopic(exercise.getId(), pecahan.getId(), client.getId())).isEqualTo(1);
        assertThat(exerciseService.itemsOf(exercise.getId()))
                .extracting(ExerciseItemEntity::getQuestionId)
                .containsExactly(p1.getId(), p2.getId());

        // Menekan tombol dua kali tidak menggandakan apa pun.
        assertThat(exerciseService.addTopic(exercise.getId(), pecahan.getId(), client.getId())).isZero();
        assertThat(exerciseService.itemsOf(exercise.getId())).hasSize(2);

        // Topic milik Client lain tidak pernah menghasilkan satu pun soal (TC-36).
        assertThat(exerciseService.addTopic(exercise.getId(), pecahanLain.getId(), client.getId())).isZero();
        assertThat(exerciseService.itemsOf(exercise.getId())).hasSize(2);
    }

    @Test
    @DisplayName("AC-Q01 (BR-Q01): MCQ dengan dua Option benar ditolak, kurang dari dua Option ditolak, tepat satu Option benar diterima")
    void acQ01RejectsWrongOptionCounts() {
        ClientEntity client = data.client("SD BankSoal1");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Pecahan");
        UserPrincipal guruPrincipal = data.principal(guru);

        // Dua Option ditandai benar sekaligus membuat soal ambigu: sistem penilaian butuh
        // TEPAT SATU jawaban benar, bukan "salah satu dari dua boleh" — kalau tidak, is_correct
        // yang dihitung saat jawaban disimpan (BR-T09) tidak punya makna tunggal.
        QuestionService.QuestionDraft duaOpsiBenar = new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>1/2 + 1/2 = ?</p>", null,
                List.of(new QuestionService.OptionDraft("<p>1</p>", true),
                        new QuestionService.OptionDraft("<p>2</p>", true)));
        assertThatThrownBy(() -> questionService.create(duaOpsiBenar, guruPrincipal.clientId(), topic.getPaketId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tepat 1");

        // Satu Option saja tidak punya pembanding; pilihan ganda minimal butuh dua opsi supaya
        // ada sesuatu untuk dipilih selain jawaban benar itu sendiri.
        QuestionService.QuestionDraft satuOpsi = new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>1/2 + 1/2 = ?</p>", null,
                List.of(new QuestionService.OptionDraft("<p>1</p>", true)));
        assertThatThrownBy(() -> questionService.create(satuOpsi, guruPrincipal.clientId(), topic.getPaketId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimal 2");

        // Tepat satu Option benar di antara tiga adalah bentuk yang sah dan harus diterima.
        QuestionService.QuestionDraft sah = new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>1/2 + 1/2 = ?</p>", null,
                List.of(new QuestionService.OptionDraft("<p>1</p>", true),
                        new QuestionService.OptionDraft("<p>2</p>", false),
                        new QuestionService.OptionDraft("<p>3</p>", false)));
        QuestionEntity saved = questionService.create(sah, guruPrincipal.clientId(), topic.getPaketId());
        assertThat(saved.getId()).isNotNull();
        assertThat(questionService.optionsOf(saved.getId())).hasSize(3);
    }

    @Test
    @DisplayName("AC-Q04 (BR-Q02): Question tanpa topicId ditolak, dan topicId milik Client lain diperlakukan seolah tidak ada")
    void acQ04RejectsMissingOrForeignTopic() {
        ClientEntity clientX = data.client("SD BankSoal2");
        AppUserEntity guruX = data.user(clientX, UserRole.GURU, "Guru X");
        UserPrincipal guruXPrincipal = data.principal(guruX);

        // topicId null: soal tidak boleh menggantung di luar taksonomi (BR-Q02) — tanpa Topic,
        // soal itu tidak akan pernah muncul lewat navigasi Subject/Topic mana pun di bank soal.
        QuestionService.QuestionDraft tanpaTopic = new QuestionService.QuestionDraft(
                null, QuestionType.MULTIPLE_CHOICE, "<p>Soal tanpa topic</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false)));
        // Belum ada Topic tujuan, jadi belum ada Paket tujuan yang bisa diturunkan — nil dipakai
        // sekadar mengisi parameter; requireWritableTopic sudah menolak topicId null lebih dulu.
        assertThatThrownBy(() -> questionService.create(tanpaTopic, guruXPrincipal.clientId(), null))
                .isInstanceOf(IllegalArgumentException.class);

        // Topic milik Client Y — bukan GLOBAL — harus diperlakukan seolah tidak ada bagi
        // Client X (TC-09): membalas 404 alih-alih 403, supaya pengenal Topic yang sah milik
        // orang lain tidak menjadi oracle yang bisa dikonfirmasi penyerang.
        ClientEntity clientY = data.client("SD BankSoal3");
        TopicEntity topicMilikY = data.topic(clientY, "IPA Kelas 4", "Ekosistem");
        QuestionService.QuestionDraft topicAsing = new QuestionService.QuestionDraft(
                topicMilikY.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Soal topic asing</p>", null,
                List.of(new QuestionService.OptionDraft("<p>A</p>", true),
                        new QuestionService.OptionDraft("<p>B</p>", false)));
        // paketId nil di sini juga: Topic milik Client Y sudah ditolak sebelum paketId sempat
        // dibandingkan, sama seperti kasus topicId null di atas.
        assertThatThrownBy(() -> questionService.create(topicAsing, guruXPrincipal.clientId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC-Q02 (BR-Q04): Question yang dihapus lunak hilang dari pencarian, tapi Exercise dan sesi Siswa yang sedang berjalan tetap utuh")
    void acQ02SoftDeleteHidesFromSearchNotFromRunningSessions() {
        ClientEntity client = data.client("SD BankSoal4");
        AppUserEntity guru = data.user(client, UserRole.GURU, "Guru");
        AppUserEntity siswa = data.user(client, UserRole.SISWA, "Siswa");
        RuanganEntity room = data.ruangan(client, "Kelas 4F");
        data.join(room, guru, MemberRole.GURU);
        data.join(room, siswa, MemberRole.SISWA);
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Bilangan");

        QuestionEntity q1 = data.mcq(client, topic, "Soal Q1 dipakai banyak Exercise", 4);
        QuestionEntity pendamping = data.mcq(client, topic, "Soal pendamping", 4);

        // Q1 dipakai di lebih dari satu Exercise; hanya salah satunya yang diterbitkan sebagai
        // Assignment PUBLISHED dan sedang dikerjakan Siswa saat penghapusan terjadi.
        ExerciseEntity exerciseDiterbitkan = data.exercise(client, guru, "Ulangan Berjalan",
                List.of(q1, pendamping));
        ExerciseEntity exerciseLain = data.exercise(client, guru, "Latihan Lain", List.of(q1));

        // Timer sengaja dibuat tidak mengikat: yang diuji di sini adalah nasib Q1 setelah
        // dihapus, bukan perilaku batas waktu.
        AssignmentEntity assignment = data.publishedQuiz(client, exerciseDiterbitkan, room, guru,
                TestData.TIMER_TAK_MENGIKAT, OffsetDateTime.now().plusDays(1), 1);

        ExamSessionEntity session = examSessionService.start(assignment.getId(), data.principal(siswa));
        List<SessionQuestionEntity> snapshot = examSessionService.snapshotOf(session.getId());
        int posisiQ1 = snapshot.stream()
                .filter(sq -> sq.getQuestionId().equals(q1.getId()))
                .findFirst().orElseThrow().getPosition();

        // Guru menghapus Q1 dari bank soal SAAT Siswa sedang mengerjakannya.
        questionService.softDelete(q1.getId(), client.getId());

        // (a) Q1 hilang dari pencarian bank soal — soal yang dihapus hilang dari PENCARIAN,
        // bukan dari sesi yang sudah memakainya.
        // search() sudah dicabut (Task 14): searchForBuilder dengan paketId/type null dan
        // excluded kosong adalah pencarian bank soal Client biasa, satu-satunya yang tersisa.
        Page<QuestionEntity> hasilPencarian = questionService.searchForBuilder(
                client.getId(), null, topic.getId(), null, List.of(), "", PageRequest.of(0, 50));
        assertThat(hasilPencarian.getContent())
                .extracting(QuestionEntity::getId).doesNotContain(q1.getId());

        // (b) require() — jalur baca tunggal bank soal — juga tidak lagi menemukannya.
        assertThatThrownBy(() -> questionService.require(q1.getId(), client.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        // (c) ExerciseItem yang merujuk Q1 di KEDUA Exercise tetap ada; penghapusan soal tidak
        // pernah diam-diam mengubah susunan Exercise yang sudah dirakit Guru.
        assertThat(exerciseService.itemsOf(exerciseDiterbitkan.getId()))
                .extracting(ExerciseItemEntity::getQuestionId).contains(q1.getId());
        assertThat(exerciseService.itemsOf(exerciseLain.getId()))
                .extracting(ExerciseItemEntity::getQuestionId).contains(q1.getId());

        // (d) Siswa yang sedang mengerjakan TIDAK melihat perubahan apa pun: batang soal Q1
        // tetap terbaca lewat snapshot sesi (findAllForSnapshot menembus soft delete), bukan
        // lewat bank soal yang barusan kehilangan Q1.
        ExamSessionService.QuestionView view = examSessionService.view(session, posisiQ1, false);
        assertThat(view.question().getId()).isEqualTo(q1.getId());
        assertThat(view.question().getBodyText()).contains("Soal Q1 dipakai banyak Exercise");

        // Sesi tetap bisa difinalisasi dengan totalQuestions utuh (dua soal, bukan satu) —
        // Result-nya tidak boleh kehilangan soal hanya karena soal itu belakangan dihapus.
        ResultEntity result = finalizer.submit(session.getId(), client.getId());
        assertThat(result.getTotalQuestions()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-P03 (BR-P02): Question yang ditulis Guru A langsung terlihat dan bisa dipakai Guru B di Client yang sama")
    void acP03NoPrivateContentPerGuruWithinSameClient() {
        ClientEntity client = data.client("SD BankSoal5");
        AppUserEntity guruA = data.user(client, UserRole.GURU, "Guru A");
        AppUserEntity guruB = data.user(client, UserRole.GURU, "Guru B");
        TopicEntity topic = data.topic(client, "Bahasa Indonesia Kelas 4", "Puisi");

        QuestionService.QuestionDraft draft = new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.MULTIPLE_CHOICE, "<p>Soal tulisan Guru A</p>", null,
                List.of(new QuestionService.OptionDraft("<p>Benar</p>", true),
                        new QuestionService.OptionDraft("<p>Salah</p>", false)));
        QuestionEntity soalGuruA = questionService.create(draft, guruA.getClientId(), topic.getPaketId());

        // Bank soal adalah milik Client, bukan milik Guru perorangan — tidak ada sekat konten
        // privat per Guru di dalam satu Client (BR-P02); Guru B mencari tanpa menyaring topic.
        Page<QuestionEntity> hasilPencarianGuruB = questionService.searchForBuilder(
                client.getId(), null, null, null, List.of(), "", PageRequest.of(0, 50));
        assertThat(hasilPencarianGuruB.getContent())
                .extracting(QuestionEntity::getId).contains(soalGuruA.getId());

        // Guru B benar-benar bisa MEMAKAINYA, bukan sekadar melihatnya di daftar — dibuktikan
        // dengan merakitnya ke Exercise milik Guru B sendiri.
        ExerciseEntity exerciseGuruB = exerciseService.create(client.getId(), "Latihan Guru B", guruB.getId());
        exerciseService.addQuestion(exerciseGuruB.getId(), soalGuruA.getId(), client.getId());
        assertThat(exerciseService.itemsOf(exerciseGuruB.getId()))
                .extracting(ExerciseItemEntity::getQuestionId).contains(soalGuruA.getId());
    }

    @Test
    @DisplayName("TC-36 (AC-B02): Client tidak bisa menulis soal ke dalam Topic milik Paket master")
    void clientTidakBisaMenulisKeDalamPaketMaster() {
        ClientEntity client = data.client("SD Batas1");
        // Client sedang menulis di Paket-nya SENDIRI, tapi draft-nya menunjuk Topic milik Paket
        // master — skenario yang sebenarnya diadang: bukan sekadar paketId yang tidak diisi.
        PaketEntity paketSendiri = data.paket(client, "Matematika Kelas 4", "Paket milik sekolah");
        TopicEntity topicMaster = data.globalTopic("Matematika Kelas 4", "Pecahan");

        QuestionService.QuestionDraft titipan = new QuestionService.QuestionDraft(
                topicMaster.getId(), QuestionType.ESSAY, "<p>Titipan ke paket master</p>",
                null, List.of());

        // Paket master boleh DIBACA Client lewat katalog, tidak boleh ditulisi (ADR-0018).
        // Kalau lolos, adopsi per Paket akan menyalin soal sekolah ini ke sekolah ketiga.
        // Ketiadaan dan "bukan milikmu" sengaja sama-sama 404 (TC-09).
        assertThatThrownBy(() -> questionService.create(titipan, client.getId(), paketSendiri.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC-B02: Question menolak Topic yang bukan milik Paket-nya")
    void questionRejectsTopicFromAnotherPaket() {
        ClientEntity client = data.client("SD Silang Topic");
        PaketEntity paketA = data.paket(client, "Matematika Kelas 4 Silang", "Paket A");
        PaketEntity paketB = data.paket(client, "Matematika Kelas 4 Silang", "Paket B");
        TopicEntity topicB = paketService.topicsOf(paketB.getId()).get(0);

        // Topic-nya sah dan milik Client yang sama, hanya saja bukan Topic milik Paket A —
        // soal ini mau ditulis ke Paket A tapi ditunjukkan ke Topic Paket B (AC-B02).
        assertThatThrownBy(() -> questionService.create(
                new QuestionService.QuestionDraft(
                        topicB.getId(), QuestionType.ESSAY, "Soal", null, List.of()),
                client.getId(), paketA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic");
    }

    @Test
    @DisplayName("AC-B02: update menolak Topic yang bukan milik Paket-nya")
    void updateRejectsTopicFromAnotherPaket() {
        ClientEntity client = data.client("SD Silang Topic Update");
        PaketEntity paketA = data.paket(client, "Matematika Kelas 4 Silang Update", "Paket A");
        PaketEntity paketB = data.paket(client, "Matematika Kelas 4 Silang Update", "Paket B");
        TopicEntity topicA = paketService.topicsOf(paketA.getId()).get(0);
        TopicEntity topicB = paketService.topicsOf(paketB.getId()).get(0);

        QuestionEntity soal = questionService.create(
                new QuestionService.QuestionDraft(
                        topicA.getId(), QuestionType.ESSAY, "<p>Soal awal</p>", null, List.of()),
                client.getId(), paketA.getId());

        // Soal sudah sah tersimpan di Paket A. Mengubahnya sambil menunjuk Topic milik Paket B —
        // paketId tujuan tetap Paket A — harus ditolak sama seperti create() (AC-B02): kalau
        // requireTopicOf dicabut khusus dari update sambil dibiarkan di create, ini satu-satunya
        // tes yang menangkapnya.
        assertThatThrownBy(() -> questionService.update(soal.getId(),
                new QuestionService.QuestionDraft(
                        topicB.getId(), QuestionType.ESSAY, "<p>Soal diubah</p>", null, List.of()),
                client.getId(), paketA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic");
    }

    @Test
    @DisplayName("AC-B02: soal baru menempel pada Paket milik Topic-nya dan mendarat di ekor Topic itu")
    void soalBaruSewadahDenganTopicnyaDanBerurutan() {
        ClientEntity client = data.client("SD Urut1");
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Pecahan");

        QuestionEntity pertama = questionService.create(new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.ESSAY, "<p>Soal pertama</p>", null, List.of()),
                client.getId(), topic.getPaketId());
        QuestionEntity kedua = questionService.create(new QuestionService.QuestionDraft(
                topic.getId(), QuestionType.ESSAY, "<p>Soal kedua</p>", null, List.of()),
                client.getId(), topic.getPaketId());

        assertThat(pertama.getPaketId()).isEqualTo(topic.getPaketId());
        assertThat(kedua.getPaketId()).isEqualTo(topic.getPaketId());

        // Tanpa nextPosition keduanya mendarat di 0 dan urutan yang dilihat penulis
        // ditentukan kebetulan.
        assertThat(pertama.getPosition()).isZero();
        assertThat(kedua.getPosition()).isEqualTo(1);
    }
}
