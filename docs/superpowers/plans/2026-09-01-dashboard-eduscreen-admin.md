# Dashboard Eduscreen Admin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengubah `/eduscreen` dari halaman onboarding menjadi dashboard yang memimpin dengan pekerjaan konten master yang macet, dengan navigasi tetap di seluruh `/eduscreen/**`.

**Architecture:** Satu service baca-saja (`EduscreenDashboardService`) merakit ringkasan dari tiga repository yang sudah ada; controller tidak menghitung apa pun. Seluruh query menyaring `client_id is null` — konten milik Eduscreen — kecuali `ClientRepository.count()` yang membaca tabel `client` itu sendiri. Nol tabel baru, nol kolom baru, nol migrasi.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA (Hibernate 6), Thymeleaf + HTMX, PostgreSQL 16 + Flyway, JUnit 5 + AssertJ + Testcontainers, Maven (`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-09-01-dashboard-eduscreen-admin-design.md`

## Global Constraints

Berlaku untuk **setiap** task di bawah:

- **TC-38** — Tes yang menyentuh database wajib turun dari `PostgresTestBase` (PostgreSQL sungguhan lewat Testcontainers). H2 dilarang.
- **TC-39** — Setiap `@DisplayName` wajib memuat pengenal `AC-*`, `TC-*`, atau `BR-*`. `AcceptanceCriteriaCoverageTest` gagal kalau tidak. Pengenal `FR-*` **tidak** dihitung.
- **TC-40** — `ArchUnitRulesTest` harus tetap hijau: tidak boleh ada port, adapter, atau nama vendor baru di modul `assessment`.
- **TC-41** — Endpoint bersasaran wajib punya tes yang membuktikan permintaan lintas-Client mendapat `404`/`403`.
- **TC-36** — Setiap pembacaan milik Client menyaring `clientId` secara eksplisit. Query dashboard menyaring `client_id is null`.
- **FR-080 / BR-P04** — Dashboard tidak boleh membaca satu baris pun milik Client, selain entitas `client` itu sendiri.
- **Tidak ada dependensi baru.** `pom.xml` tidak disentuh.
- **Bahasa domain** mengikuti `CONTEXT.md`: Subject, Topic, Question, Exercise (paket), Ruangan, Client. Bukan "mapel", "bank soal", "kelas".
- Perintah tes: `./mvnw test -Dtest=NamaKelas` untuk satu kelas, `./mvnw test` untuk seluruhnya.
- Database lokal untuk menjalankan aplikasi: `docker` container `eduscreen-db` di port 5433, profil `local`.

---

### Task 1: Hitungan tiga kartu

Kartu pintasan dashboard: jumlah Client, jumlah Question master, jumlah paket terbit. Task ini menurunkan seluruh jalur baca dari repository sampai service, dan mengunci batas FR-080 sejak angka pertama.

**Files:**
- Create: `src/main/java/com/eduscreen/app/modules/assessment/service/EduscreenDashboardService.java`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java` (tambah dua method di akhir antarmuka, sebelum `}` penutup)
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseRepository.java` (tambah satu method sebelum `}` penutup)
- Test: `src/test/java/com/eduscreen/app/modules/EduscreenDashboardIT.java`

**Interfaces:**
- Consumes: `ClientRepository` (sudah ada, kosong — mewarisi `count()` dari `JpaRepository`), `QuestionRepository`, `ExerciseRepository`.
- Produces:
  - `EduscreenDashboardService.KartuDashboard(long client, long questionMaster, long paketTerbit)` — record bersarang, publik.
  - `EduscreenDashboardService.kartu()` → `KartuDashboard`.
  - `QuestionRepository.countMaster()` → `long`
  - `QuestionRepository.countUnpublishedMaster()` → `long` (dipakai Task 2)
  - `ExerciseRepository.countPublishedMaster()` → `long`

- [ ] **Step 1: Tulis tes yang gagal**

Buat `src/test/java/com/eduscreen/app/modules/EduscreenDashboardIT.java`:

```java
package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard Eduscreen Admin: angka kartu dan antrean pekerjaan yang macet (BR-O05).
 *
 * <p>Database tes dipakai bersama seluruh kelas (lihat {@link PostgresTestBase}), jadi tidak ada
 * assertion yang boleh mengandaikan angka mutlak. Yang diukur selalu SELISIH sebelum dan sesudah.
 */
class EduscreenDashboardIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    EduscreenDashboardService dashboard;

    @Test
    @DisplayName("BR-O05: kartu menghitung Client, Question master, dan paket terbit")
    void kartuMenghitungMilikEduscreen() {
        var sebelum = dashboard.kartu();

        data.client("SD Dashboard1");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal terbit dashboard");
        data.masterMcq(topic, "Soal draf dashboard");
        data.masterExercise("Paket dashboard terbit", List.of(terbit));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
        // Dua Question master lahir: satu terbit, satu draf. Kartu menghitung keduanya.
        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster() + 2);
        // masterExercise() melahirkan paket DRAF, jadi kartu "paket terbit" tidak bergerak.
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): kartu tidak menghitung satu pun Question atau paket milik Client")
    void kartuTidakMenghitungMilikClient() {
        var sebelum = dashboard.kartu();

        ClientEntity client = data.client("SD Dashboard2");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah dashboard", 4);
        data.exercise(client, data.user(client, UserRole.GURU, "Guru Dashboard"),
                "Paket sekolah dashboard", List.of(soalClient));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster());
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
        // Client-nya sendiri MEMANG bertambah: entitas client dikelola Eduscreen, isinya tidak.
        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
    }
}
```

- [ ] **Step 2: Jalankan tes, pastikan gagal**

Run: `./mvnw test -Dtest=EduscreenDashboardIT`
Expected: FAIL saat kompilasi — `EduscreenDashboardService` belum ada (`cannot find symbol`).

- [ ] **Step 3: Tambah query hitung di repository**

Di `QuestionRepository.java`, sebelum `}` penutup antarmuka:

```java
    /** Kartu dashboard: seluruh Question master, draf maupun terbit (FR-060). */
    @Query("select count(q) from QuestionEntity q where q.clientId is null")
    long countMaster();

    /** Antrean dashboard: Question master yang masih digarap (BR-O05, FR-066). */
    @Query("select count(q) from QuestionEntity q where q.clientId is null and q.publishedAt is null")
    long countUnpublishedMaster();
```

Di `ExerciseRepository.java`, sebelum `}` penutup:

```java
    /** Kartu dashboard: paket master yang sudah terbit dan karena itu bisa diadopsi (FR-067). */
    @Query("select count(e) from ExerciseEntity e where e.clientId is null and e.publishedAt is not null")
    long countPublishedMaster();
```

- [ ] **Step 4: Tulis service**

Buat `src/main/java/com/eduscreen/app/modules/assessment/service/EduscreenDashboardService.java`:

```java
package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.modules.assessment.repository.ExerciseRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ringkasan yang dibaca dashboard Eduscreen Admin.
 *
 * <p>Setiap query di sini menyaring {@code client_id is null} — konten milik Eduscreen — kecuali
 * {@link ClientRepository#count()} yang membaca tabel {@code client} itu sendiri, yaitu entitas
 * yang memang Eduscreen kelola. Tidak ada satu pun jalur yang menyentuh Question, Exercise,
 * Ruangan, atau Session milik sebuah sekolah (FR-080, BR-P04). Batas itu dikunci
 * {@code EduscreenDashboardIT}, bukan sekadar diperiksa mata.
 */
@Service
public class EduscreenDashboardService {

    private final ClientRepository clients;
    private final QuestionRepository questions;
    private final ExerciseRepository exercises;

    public EduscreenDashboardService(ClientRepository clients,
                                     QuestionRepository questions,
                                     ExerciseRepository exercises) {
        this.clients = clients;
        this.questions = questions;
        this.exercises = exercises;
    }

    /** Tiga angka pintasan di kaki dashboard. */
    @Transactional(readOnly = true)
    public KartuDashboard kartu() {
        return new KartuDashboard(
                clients.count(),
                questions.countMaster(),
                exercises.countPublishedMaster());
    }

    public record KartuDashboard(long client, long questionMaster, long paketTerbit) {
    }
}
```

- [ ] **Step 5: Jalankan tes, pastikan hijau**

Run: `./mvnw test -Dtest=EduscreenDashboardIT`
Expected: PASS, 2 tes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/eduscreen/app/modules/assessment/service/EduscreenDashboardService.java \
        src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java \
        src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseRepository.java \
        src/test/java/com/eduscreen/app/modules/EduscreenDashboardIT.java
git commit -m "feat: hitungan kartu dashboard Eduscreen Admin"
```

---

### Task 2: Antrean pekerjaan yang macet

Empat baris: Question draf, paket macet di gerbang FR-069, paket siap terbit, dan Subject global tanpa Topic. Aturan bisnisnya belum pernah ditulis, jadi task ini mendaftarkannya lebih dulu supaya nama tes punya pengenal yang sah (TC-39).

**Files:**
- Modify: `specs/001-student-exercise-portal/business-rules.md` (tambah `BR-O05` di bagian 6.2, di bawah `BR-O04`)
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseRepository.java`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/repository/SubjectRepository.java`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/service/EduscreenDashboardService.java`
- Test: `src/test/java/com/eduscreen/app/modules/EduscreenDashboardIT.java` (tambah kasus)

**Interfaces:**
- Consumes: `EduscreenDashboardService` dan `QuestionRepository.countUnpublishedMaster()` dari Task 1.
- Produces:
  - `EduscreenDashboardService.Baris<T>(List<T> tampil, long total)` dengan `sisa()` dan `ada()`.
  - `EduscreenDashboardService.Antrean(long questionDraf, Baris<ExerciseEntity> paketMacet, Baris<ExerciseEntity> paketSiapTerbit, Baris<SubjectEntity> subjectBuntu)` dengan `kosong()`.
  - `EduscreenDashboardService.antrean()` → `Antrean`.
  - `ExerciseRepository.findMasterBlocked()` → `List<ExerciseEntity>`
  - `ExerciseRepository.findMasterReadyToPublish()` → `List<ExerciseEntity>`
  - `SubjectRepository.findGlobalWithoutTopic()` → `List<SubjectEntity>`

- [ ] **Step 1: Daftarkan BR-O05**

Di `specs/001-student-exercise-portal/business-rules.md`, tepat setelah baris `BR-O04`, sisipkan:

```markdown
- **BR-O05** — Pekerjaan konten master yang macet karena aturan penerbitan harus terlihat tanpa dicari. Paket yang isinya belum terbit (FR-069), paket kosong (FR-072), dan Subject global tanpa Topic adalah jalan buntu yang tidak menjelaskan dirinya sendiri di layar tempat ia dibuat.
```

- [ ] **Step 2: Tulis tes yang gagal**

Tambahkan di `EduscreenDashboardIT`, sebelum `}` penutup kelas:

```java
    @Test
    @DisplayName("BR-O05 (FR-069): paket master yang memuat Question belum terbit masuk antrean, dan keluar begitu isinya diterbitkan")
    void paketMacetMasukAntreanLaluKeluar() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Isi paket macet");
        var paket = data.masterExercise("Paket macet dashboard", List.of(draf));

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).contains(paket.getId());

        publishing.publishQuestion(draf.getId());

        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("BR-O05 (FR-072): paket berisi yang seluruh isinya sudah terbit masuk antrean siap terbit, lalu keluar setelah diterbitkan")
    void paketSiapTerbitMasukAntreanLaluKeluar() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Isi paket siap");
        var paket = data.masterExercise("Paket siap dashboard", List.of(terbit));

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).contains(paket.getId());

        publishing.publishExercise(paket.getId());

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paket.getId());
    }

    @Test
    @DisplayName("BR-O05 (FR-072): paket master kosong tidak pernah disebut siap terbit")
    void paketKosongBukanSiapTerbit() {
        var kosong = data.masterExercise("Paket kosong dashboard", List.of());

        assertThat(dashboard.antrean().paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(kosong.getId());
        assertThat(dashboard.antrean().paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(kosong.getId());
    }

    @Test
    @DisplayName("BR-O05: Subject global tanpa Topic masuk antrean, lalu keluar begitu Topic pertama lahir")
    void subjectBuntuMasukAntreanLaluKeluar() {
        var subject = taxonomy.createGlobalSubject("Kimia Kelas 11 buntu dashboard");

        assertThat(dashboard.antrean().subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).contains(subject.getId());

        taxonomy.createGlobalTopic(subject.getId(), "Asam Basa");

        assertThat(dashboard.antrean().subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).doesNotContain(subject.getId());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): pekerjaan macet milik sebuah Client tidak pernah masuk antrean Eduscreen")
    void antreanTidakMemuatPekerjaanClient() {
        ClientEntity client = data.client("SD Dashboard3");
        var guru = data.user(client, UserRole.GURU, "Guru Antrean");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah antrean", 4);
        var paketClient = data.exercise(client, guru, "Paket sekolah antrean", List.of(soalClient));
        var subjectClient = subjects.save(SubjectEntity.forClient(client.getId(), "Bahasa Sunda Kelas 5 buntu"));

        var antrean = dashboard.antrean();

        assertThat(antrean.paketMacet().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paketClient.getId());
        assertThat(antrean.paketSiapTerbit().tampil())
                .extracting(ExerciseEntity::getId).doesNotContain(paketClient.getId());
        assertThat(antrean.subjectBuntu().tampil())
                .extracting(SubjectEntity::getId).doesNotContain(subjectClient.getId());
    }

    @Test
    @DisplayName("BR-O05: antrean tanpa satu pun baris menyatakan dirinya kosong, sehingga bloknya tidak dirender")
    void antreanTanpaBarisMenyatakanDirinyaKosong() {
        var nihil = new EduscreenDashboardService.Baris<ExerciseEntity>(List.of(), 0);
        var kosong = new EduscreenDashboardService.Antrean(0, nihil, nihil,
                new EduscreenDashboardService.Baris<SubjectEntity>(List.of(), 0));

        assertThat(kosong.kosong()).isTrue();

        var adaDraf = new EduscreenDashboardService.Antrean(1, nihil, nihil,
                new EduscreenDashboardService.Baris<SubjectEntity>(List.of(), 0));

        assertThat(adaDraf.kosong()).isFalse();
    }

    @Test
    @DisplayName("BR-O05: satu baris antrean menampilkan paling banyak lima nama dan menghitung sisanya")
    void barisAntreanDipotongLimaNama() {
        var tujuh = List.of("a", "b", "c", "d", "e", "f", "g");

        var baris = EduscreenDashboardService.Baris.dari(tujuh);

        assertThat(baris.tampil()).hasSize(5);
        assertThat(baris.total()).isEqualTo(7);
        assertThat(baris.sisa()).isEqualTo(2);
        assertThat(baris.ada()).isTrue();
    }
```

Dua kasus terakhir sengaja tidak menyentuh database. `kosong()` bergantung pada keadaan
seluruh tabel, dan database tes dipakai bersama seluruh kelas — memaksanya lewat query berarti
menulis tes yang hijau atau merah tergantung kelas mana yang kebetulan jalan lebih dulu.

Tambahkan field dan import yang dibutuhkan kasus di atas, di bagian atas kelas:

```java
    @Autowired
    MasterPublishingService publishing;
    @Autowired
    TaxonomyService taxonomy;
    @Autowired
    SubjectRepository subjects;
```

```java
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
```

`MasterPublishingService.publishQuestion(UUID)` dan `publishExercise(UUID)` sudah ada dan bertanda tangan persis seperti pemakaian di atas.

- [ ] **Step 3: Jalankan tes, pastikan gagal**

Run: `./mvnw test -Dtest=EduscreenDashboardIT`
Expected: FAIL saat kompilasi — `antrean()` belum ada di `EduscreenDashboardService`.

- [ ] **Step 4: Tambah query antrean**

Di `ExerciseRepository.java`, sebelum `}` penutup:

```java
    /**
     * Antrean dashboard: paket master yang macet di gerbang FR-069 — masih draf, tapi memuat
     * Question yang belum terbit sehingga penerbitannya pasti ditolak (BR-O05).
     *
     * <p>Satu query untuk seluruh paket, bukan perulangan
     * {@code QuestionRepository.findUnpublishedInExercise} per paket yang N+1.
     */
    @Query("select e from ExerciseEntity e where e.clientId is null and e.publishedAt is null "
            + "and exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id "
            + "and i.questionId in (select q.id from QuestionEntity q where q.publishedAt is null)) "
            + "order by e.updatedAt desc")
    List<ExerciseEntity> findMasterBlocked();

    /**
     * Antrean dashboard: paket master yang tinggal diklik — draf, berisi (FR-072), dan seluruh
     * isinya sudah terbit (BR-O05).
     */
    @Query("select e from ExerciseEntity e where e.clientId is null and e.publishedAt is null "
            + "and exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id) "
            + "and not exists (select i.id from ExerciseItemEntity i where i.exerciseId = e.id "
            + "and i.questionId in (select q.id from QuestionEntity q where q.publishedAt is null)) "
            + "order by e.updatedAt desc")
    List<ExerciseEntity> findMasterReadyToPublish();
```

Di `SubjectRepository.java`, sebelum `}` penutup:

```java
    /**
     * Antrean dashboard: Subject GLOBAL yang belum punya satu pun Topic (BR-O05). Di ruang kerja
     * master, tombol "+ Soal baru" hanya muncul setelah Topic dipilih — Subject tanpa Topic
     * adalah jalan buntu yang tidak menjelaskan dirinya sendiri.
     */
    @Query("select s from SubjectEntity s "
            + "where s.origin = com.eduscreen.app.modules.assessment.domain.ContentOrigin.GLOBAL "
            + "and not exists (select t.id from TopicEntity t where t.subjectId = s.id "
            + "and t.origin = com.eduscreen.app.modules.assessment.domain.ContentOrigin.GLOBAL) "
            + "order by s.name asc")
    List<SubjectEntity> findGlobalWithoutTopic();
```

- [ ] **Step 5: Tambah `antrean()` di service**

Di `EduscreenDashboardService.java` — tambah dua import, satu field, satu konstanta, satu method, dan dua record:

```java
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;

import java.util.List;
```

Field baru beserta parameter konstruktornya (tambahkan `SubjectRepository subjects` sebagai argumen keempat, dan `this.subjects = subjects;` di badannya):

```java
    private final SubjectRepository subjects;
```

Konstanta dan method:

```java
    /** ponytail: batas nama yang ditampilkan per baris antrean; hitungannya tetap utuh. */
    private static final int BATAS_NAMA = 5;

    /**
     * Pekerjaan konten master yang macet (BR-O05).
     *
     * <p>Daftarnya dipotong di Java, bukan di query: katalog master berukuran ratusan baris, dan
     * tiga {@code Pageable} demi memotong daftar sependek ini lebih mahal dibaca daripada
     * dampaknya. Kalau katalog master tumbuh sampai puluhan ribu, pindahkan pemotongan ke query.
     */
    @Transactional(readOnly = true)
    public Antrean antrean() {
        return new Antrean(
                questions.countUnpublishedMaster(),
                Baris.dari(exercises.findMasterBlocked()),
                Baris.dari(exercises.findMasterReadyToPublish()),
                Baris.dari(subjects.findGlobalWithoutTopic()));
    }

    /** Satu baris antrean: sampai lima nama untuk ditampilkan, plus jumlah seluruhnya. */
    public record Baris<T>(List<T> tampil, long total) {

        public static <T> Baris<T> dari(List<T> semua) {
            return new Baris<>(semua.stream().limit(BATAS_NAMA).toList(), semua.size());
        }

        /** Yang tidak muat ditampilkan; dirender sebagai "…dan N lainnya". */
        public long sisa() {
            return total - tampil.size();
        }

        public boolean ada() {
            return total > 0;
        }
    }

    /**
     * Seluruh antrean. {@link #kosong()} menentukan apakah blok "Butuh perhatian" dirender sama
     * sekali: antrean kosong berarti bloknya hilang dan kartu naik jadi isi utama.
     */
    public record Antrean(long questionDraf,
                          Baris<ExerciseEntity> paketMacet,
                          Baris<ExerciseEntity> paketSiapTerbit,
                          Baris<SubjectEntity> subjectBuntu) {

        public boolean kosong() {
            return questionDraf == 0
                    && !paketMacet.ada()
                    && !paketSiapTerbit.ada()
                    && !subjectBuntu.ada();
        }
    }
```

- [ ] **Step 6: Jalankan tes, pastikan hijau**

Run: `./mvnw test -Dtest=EduscreenDashboardIT`
Expected: PASS, 9 tes.

- [ ] **Step 7: Commit**

```bash
git add specs/001-student-exercise-portal/business-rules.md \
        src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseRepository.java \
        src/main/java/com/eduscreen/app/modules/assessment/repository/SubjectRepository.java \
        src/main/java/com/eduscreen/app/modules/assessment/service/EduscreenDashboardService.java \
        src/test/java/com/eduscreen/app/modules/EduscreenDashboardIT.java
git commit -m "feat: antrean pekerjaan macet untuk dashboard Eduscreen (BR-O05)"
```

---

### Task 3: Halaman dashboard dan pemisahan `/eduscreen/client`

`/eduscreen` berhenti jadi alias dan menjadi halaman sendiri; daftar Client beserta form onboarding pindah utuh ke `/eduscreen/client`.

**Files:**
- Create: `src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenDashboardController.java`
- Create: `src/main/resources/templates/eduscreen/dashboard.html`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenAdminController.java:39` (anotasi `@GetMapping`)
- Modify: `src/main/resources/templates/eduscreen/client.html:13-21` (buang blok nav dua tombol)
- Test: `src/test/java/com/eduscreen/app/web/EduscreenDashboardRenderTest.java`
- Test: `src/test/java/com/eduscreen/app/web/ContentIdorTest.java` (tambah satu kasus)

**Interfaces:**
- Consumes: `EduscreenDashboardService.kartu()` dan `.antrean()` dari Task 1 dan 2.
- Produces: rute `GET /eduscreen` yang merender `eduscreen/dashboard`, dengan atribut model `kartu`, `antrean`, dan `menuAktif = "dashboard"`.

- [ ] **Step 1: Tulis tes render yang gagal**

Buat `src/test/java/com/eduscreen/app/web/EduscreenDashboardRenderTest.java`:

```java
package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TC-14: dashboard dan halaman Client berdiri sebagai dua halaman terpisah. */
@AutoConfigureMockMvc
class EduscreenDashboardRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;

    @Test
    @DisplayName("TC-14 (BR-O05): dashboard merender antrean berisi paket yang macet")
    void dashboardMerenderAntrean() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Isi paket macet render");
        data.masterExercise("Paket macet render", List.of(draf));

        mockMvc.perform(get("/eduscreen").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Butuh perhatian")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket macet render")))
                // Isi halaman Client sudah pindah; dashboard tidak lagi memuat form onboarding.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan"))));
    }

    @Test
    @DisplayName("TC-14: /eduscreen/client memuat daftar Client dan form onboarding, bukan antrean")
    void halamanClientMemuatOnboarding() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        data.client("SD Render Dashboard");

        mockMvc.perform(get("/eduscreen/client").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Buat Client dan kirim undangan")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Butuh perhatian"))));
    }
}
```

- [ ] **Step 2: Jalankan tes, pastikan gagal**

Run: `./mvnw test -Dtest=EduscreenDashboardRenderTest`
Expected: FAIL — `/eduscreen` masih merender `eduscreen/client`, jadi assertion "Butuh perhatian" tidak ketemu dan "Buat Client dan kirim undangan" justru ketemu.

- [ ] **Step 3: Pecah rute di `EduscreenAdminController`**

Ganti baris 39:

```java
    @GetMapping({"/eduscreen", "/eduscreen/client"})
```

menjadi:

```java
    @GetMapping("/eduscreen/client")
```

lalu di badan method `clients(Model model)`, tepat sebelum `return "eduscreen/client";`, tambahkan:

```java
        model.addAttribute("menuAktif", "client");
```

- [ ] **Step 4: Tulis controller dashboard**

Buat `src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenDashboardController.java`:

```java
package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Halaman pendaratan Eduscreen Admin.
 *
 * <p>Controller sendiri, bukan menumpang {@code EduscreenAdminController}: yang ini membaca dari
 * tiga tempat sekaligus lewat {@link EduscreenDashboardService}, sementara controller itu
 * urusannya onboarding Client. Menumpuk keduanya memberi satu kelas dua alasan untuk berubah.
 *
 * <p>Rutenya di bawah {@code /eduscreen/**} yang sudah dipagari {@code hasRole("EDUSCREEN_ADMIN")}
 * di {@code SecurityConfig}.
 */
@Controller
public class EduscreenDashboardController {

    private final EduscreenDashboardService dashboard;

    public EduscreenDashboardController(EduscreenDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/eduscreen")
    public String dashboard(Model model) {
        model.addAttribute("kartu", dashboard.kartu());
        model.addAttribute("antrean", dashboard.antrean());
        model.addAttribute("menuAktif", "dashboard");
        return "eduscreen/dashboard";
    }
}
```

- [ ] **Step 5: Tulis templat dashboard**

Buat `src/main/resources/templates/eduscreen/dashboard.html`:

```html
<!DOCTYPE html>
<html lang="id" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout/base :: head('Dashboard')}"></head>
<body>
<div th:replace="~{layout/base :: page('Dashboard', ~{::content})}">
  <div th:fragment="content" th:remove="tag">

    <!-- Antrean memimpin halaman (BR-O05). Saat kosong bloknya TIDAK dirender sama sekali dan
         kartu naik jadi isi utama — satu th:if di pembungkus, bukan cabang tata letak terpisah. -->
    <section th:unless="${antrean.kosong()}"
             class="mb-8 rounded-lg border border-slate-200 bg-white p-6">
      <h2 class="text-lg font-semibold">Butuh perhatian</h2>
      <ul class="mt-4 divide-y divide-slate-100 text-sm">

        <li th:if="${antrean.questionDraf() > 0}" class="flex items-center justify-between py-2">
          <a th:href="@{/eduscreen/soal(status='DRAF')}" class="text-slate-700 underline"
             th:text="|${antrean.questionDraf()} Question master masih draf|">Question draf</a>
          <span class="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-800">draf</span>
        </li>

        <li th:each="p : ${antrean.paketMacet().tampil()}" class="flex items-center justify-between py-2">
          <a th:href="@{/eduscreen/paket/{id}(id=${p.id})}" class="text-slate-700 underline"
             th:text="|${p.title} memuat soal belum terbit|">Paket macet</a>
          <span class="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-bold text-rose-800">macet</span>
        </li>
        <li th:if="${antrean.paketMacet().sisa() > 0}" class="py-2 text-xs text-slate-500">
          <a th:href="@{/eduscreen/paket}" class="underline"
             th:text="|…dan ${antrean.paketMacet().sisa()} paket macet lainnya|">lainnya</a>
        </li>

        <li th:each="p : ${antrean.paketSiapTerbit().tampil()}" class="flex items-center justify-between py-2">
          <a th:href="@{/eduscreen/paket/{id}(id=${p.id})}" class="text-slate-700 underline"
             th:text="|${p.title} siap diterbitkan|">Paket siap</a>
          <span class="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold text-emerald-800">siap</span>
        </li>
        <li th:if="${antrean.paketSiapTerbit().sisa() > 0}" class="py-2 text-xs text-slate-500">
          <a th:href="@{/eduscreen/paket}" class="underline"
             th:text="|…dan ${antrean.paketSiapTerbit().sisa()} paket siap terbit lainnya|">lainnya</a>
        </li>

        <li th:each="s : ${antrean.subjectBuntu().tampil()}" class="flex items-center justify-between py-2">
          <a th:href="@{/eduscreen/soal(subjectId=${s.id})}" class="text-slate-700 underline"
             th:text="|Subject &quot;${s.name}&quot; belum punya Topic|">Subject buntu</a>
          <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-600">buntu</span>
        </li>
        <li th:if="${antrean.subjectBuntu().sisa() > 0}" class="py-2 text-xs text-slate-500">
          <a th:href="@{/eduscreen/soal}" class="underline"
             th:text="|…dan ${antrean.subjectBuntu().sisa()} Subject buntu lainnya|">lainnya</a>
        </li>

      </ul>
    </section>

    <div class="grid gap-4 sm:grid-cols-3">
      <a th:href="@{/eduscreen/client}"
         class="rounded-lg border border-slate-200 bg-white p-5 hover:border-slate-400">
        <div class="text-xs font-bold uppercase tracking-wide text-slate-500">Client</div>
        <div class="mt-2 text-sm text-slate-700" th:text="|${kartu.client()} sekolah &rarr;|">Client</div>
      </a>
      <a th:href="@{/eduscreen/soal}"
         class="rounded-lg border border-slate-200 bg-white p-5 hover:border-slate-400">
        <div class="text-xs font-bold uppercase tracking-wide text-slate-500">Konten master</div>
        <div class="mt-2 text-sm text-slate-700" th:text="|${kartu.questionMaster()} Question &rarr;|">Question</div>
      </a>
      <a th:href="@{/eduscreen/paket}"
         class="rounded-lg border border-slate-200 bg-white p-5 hover:border-slate-400">
        <div class="text-xs font-bold uppercase tracking-wide text-slate-500">Paket</div>
        <div class="mt-2 text-sm text-slate-700" th:text="|${kartu.paketTerbit()} terbit &rarr;|">Paket</div>
      </a>
    </div>

  </div>
</div>
</body>
</html>
```

- [ ] **Step 6: Buang blok nav lama dari `client.html`**

Hapus seluruh elemen `<nav>` di `src/main/resources/templates/eduscreen/client.html` baris 13-21 (dua tautan "Konten master → Question" dan "Konten master → Paket"). Nav-nya pindah ke header di Task 4.

- [ ] **Step 7: Jalankan tes, pastikan hijau**

Run: `./mvnw test -Dtest=EduscreenDashboardRenderTest`
Expected: PASS, 2 tes.

- [ ] **Step 8: Buktikan pagarnya masih berdiri**

Tambahkan di `src/test/java/com/eduscreen/app/web/ContentIdorTest.java`, sebelum `}` penutup kelas:

```java
    @Test
    @DisplayName("TC-41 (FR-081): dashboard Eduscreen tetap tertutup bagi seluruh peran Client")
    void peranClientDitolakDiDashboard() throws Exception {
        Tenants tenants = data.twoTenants();

        for (var pengguna : java.util.List.of(
                tenants.a().guru(), tenants.a().siswa(), tenants.a().admin())) {
            mockMvc.perform(get("/eduscreen").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/eduscreen/client").with(user(data.principal(pengguna))))
                    .andExpect(status().isForbidden());
        }
    }
```

Run: `./mvnw test -Dtest=ContentIdorTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenDashboardController.java \
        src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenAdminController.java \
        src/main/resources/templates/eduscreen/dashboard.html \
        src/main/resources/templates/eduscreen/client.html \
        src/test/java/com/eduscreen/app/web/EduscreenDashboardRenderTest.java \
        src/test/java/com/eduscreen/app/web/ContentIdorTest.java
git commit -m "feat: halaman dashboard Eduscreen, daftar Client pindah ke /eduscreen/client"
```

---

### Task 4: Nav tetap di header

Empat butir menu yang berlaku di seluruh halaman `/eduscreen/**`, dan hanya terlihat oleh `EDUSCREEN_ADMIN`.

**Files:**
- Modify: `src/main/resources/templates/layout/base.html:37-51` (blok `<header>`)
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/controller/MasterContentController.java` (isi `menuAktif` di lima handler halaman penuh)
- Test: `src/test/java/com/eduscreen/app/web/EduscreenDashboardRenderTest.java` (tambah dua kasus)

**Interfaces:**
- Consumes: atribut model `menuAktif` yang diisi Task 3 (`"dashboard"`, `"client"`).
- Produces: `menuAktif` bernilai `"soal"` di halaman `/eduscreen/soal**` dan `"paket"` di `/eduscreen/paket**`.

- [ ] **Step 1: Tulis tes yang gagal**

Tambahkan di `EduscreenDashboardRenderTest`, sebelum `}` penutup kelas:

```java
    @Test
    @DisplayName("TC-14: nav Eduscreen muncul di seluruh halaman /eduscreen/**")
    void navMunculDiSeluruhHalamanEduscreen() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));

        for (String jalur : List.of("/eduscreen", "/eduscreen/client", "/eduscreen/soal", "/eduscreen/paket")) {
            mockMvc.perform(get(jalur).with(admin))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("id=\"nav-eduscreen\"")));
        }
    }

    @Test
    @DisplayName("BR-P04: Guru, Siswa, dan Client Admin tidak pernah melihat nav Eduscreen di portalnya")
    void navEduscreenTidakBocorKePeranClient() throws Exception {
        var tenants = data.twoTenants();

        mockMvc.perform(get("/guru").with(user(data.principal(tenants.a().guru()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
        mockMvc.perform(get("/siswa").with(user(data.principal(tenants.a().siswa()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
        mockMvc.perform(get("/admin").with(user(data.principal(tenants.a().admin()))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nav-eduscreen"))));
    }
```

Rute portal ketiga peran itu sudah dipastikan di `PortalRoutingController:19-24`: `/siswa`, `/guru`, `/admin`.

- [ ] **Step 2: Jalankan tes, pastikan gagal**

Run: `./mvnw test -Dtest=EduscreenDashboardRenderTest`
Expected: FAIL — `id="nav-eduscreen"` belum ada di HTML mana pun.

- [ ] **Step 3: Pasang nav di header**

Di `src/main/resources/templates/layout/base.html`, di dalam `<header>`, sisipkan blok berikut tepat **sesudah** tautan merek (`<a th:href="@{/}" …>Eduscreen</a>`) dan **sebelum** `<div sec:authorize="isAuthenticated()" …>`:

```html
      <!-- Nav khusus Eduscreen Admin. Templat bukan pagar keamanan — SecurityConfig yang menjaga
           /eduscreen/** — tapi menampilkan pintu yang tidak boleh dibuka adalah cacat tersendiri,
           jadi sec:authorize di sini menutupnya di lapis tampilan (BR-P04). -->
      <nav id="nav-eduscreen" sec:authorize="hasRole('EDUSCREEN_ADMIN')"
           class="flex items-center gap-4 text-sm"
           xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
        <a th:href="@{/eduscreen}" th:classappend="${menuAktif == 'dashboard'} ? 'font-bold text-slate-900' : 'text-slate-600'"
           class="hover:text-slate-900">Dashboard</a>
        <a th:href="@{/eduscreen/client}" th:classappend="${menuAktif == 'client'} ? 'font-bold text-slate-900' : 'text-slate-600'"
           class="hover:text-slate-900">Client</a>
        <a th:href="@{/eduscreen/soal}" th:classappend="${menuAktif == 'soal'} ? 'font-bold text-slate-900' : 'text-slate-600'"
           class="hover:text-slate-900">Konten master</a>
        <a th:href="@{/eduscreen/paket}" th:classappend="${menuAktif == 'paket'} ? 'font-bold text-slate-900' : 'text-slate-600'"
           class="hover:text-slate-900">Paket</a>
      </nav>
```

- [ ] **Step 4: Isi `menuAktif` di halaman konten master**

Di `MasterContentController.java`, tambahkan `model.addAttribute("menuAktif", …)` di lima handler yang merender halaman penuh (bukan fragmen HTMX):

- `search(...)` — tepat sebelum `return "soal/daftar";` → `"soal"`
- `baru(...)` — sebelum `return` templat editor → `"soal"`
- handler `GET /eduscreen/soal/{id}` — sebelum `return` templat editor → `"soal"`
- handler `GET /eduscreen/paket` — sebelum `return` templat daftar paket → `"paket"`
- handler `GET /eduscreen/paket/{id}` — sebelum `return` templat perakit → `"paket"`

Contoh, di `search(...)`:

```java
        model.addAttribute("subjects", taxonomy.visibleSubjects(MASTER));
        model.addAttribute("topics", subjectId != null ? taxonomy.visibleTopics(subjectId, MASTER) : List.of());
        model.addAttribute("menuAktif", "soal");
        return "soal/daftar";
```

Jangan menaruhnya di `isiJalur(model)` — method itu juga dipakai jalur fragmen HTMX yang tidak pernah merender header.

- [ ] **Step 5: Jalankan tes, pastikan hijau**

Run: `./mvnw test -Dtest=EduscreenDashboardRenderTest`
Expected: PASS, 4 tes.

- [ ] **Step 6: Pastikan halaman peran lain tidak rusak**

Run: `./mvnw test -Dtest='MasterContentRenderTest,EduscreenDashboardRenderTest'`
Expected: PASS. Hanya dua kelas render yang ada di repo ini; `base.html` dipakai seluruh peran, jadi kerusakannya akan muncul di sini.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/layout/base.html \
        src/main/java/com/eduscreen/app/modules/assessment/controller/MasterContentController.java \
        src/test/java/com/eduscreen/app/web/EduscreenDashboardRenderTest.java
git commit -m "feat: nav tetap Eduscreen Admin di header"
```

---

### Task 5: Penyaring status di ruang kerja konten master

Baris antrean "Question master masih draf" menautkan ke `/eduscreen/soal?status=DRAF`. Task ini membuat tautan itu benar-benar menyaring.

**Files:**
- Create: `src/main/java/com/eduscreen/app/modules/assessment/domain/StatusTerbit.java`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java`
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/service/QuestionService.java` (di dekat `searchMaster`, baris 93)
- Modify: `src/main/java/com/eduscreen/app/modules/assessment/controller/MasterContentController.java` (handler `search`)
- Modify: `src/main/resources/templates/soal/daftar.html` (form filter, di dekat baris 32)
- Test: `src/test/java/com/eduscreen/app/modules/MasterContentIT.java` (tambah satu kasus)

**Interfaces:**
- Consumes: `QuestionRepository.searchMaster` dan `searchPublishedMaster` yang sudah ada.
- Produces:
  - enum `StatusTerbit { DRAF, TERBIT }`
  - `QuestionRepository.searchUnpublishedMaster(UUID subjectId, UUID topicId, String pattern, Pageable pageable)` → `Page<QuestionEntity>`
  - `QuestionService.searchMaster(UUID subjectId, UUID topicId, String q, StatusTerbit status, Pageable pageable)` → `Page<QuestionEntity>`
  - Rute `GET /eduscreen/soal` menerima parameter `status` bernilai `DRAF` atau `TERBIT`; kosong berarti semua.

- [ ] **Step 1: Tulis tes yang gagal**

Tambahkan di `src/test/java/com/eduscreen/app/modules/MasterContentIT.java`, sebelum `}` penutup kelas:

```java
    @Test
    @DisplayName("BR-O05: penyaring status DRAF hanya memunculkan Question master yang belum terbit")
    void penyaringStatusDrafHanyaMemunculkanDraf() {
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity draf = data.masterMcq(topic, "Soal draf penyaring unik");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal terbit penyaring unik");

        var hasilDraf = questionService.searchMaster(null, topic.getId(), "penyaring unik",
                com.eduscreen.app.modules.assessment.domain.StatusTerbit.DRAF,
                PageRequest.of(0, 20));

        assertThat(hasilDraf.getContent()).extracting(QuestionEntity::getId)
                .contains(draf.getId())
                .doesNotContain(terbit.getId());

        var hasilSemua = questionService.searchMaster(null, topic.getId(), "penyaring unik",
                null, PageRequest.of(0, 20));

        assertThat(hasilSemua.getContent()).extracting(QuestionEntity::getId)
                .contains(draf.getId(), terbit.getId());
    }
```

- [ ] **Step 2: Jalankan tes, pastikan gagal**

Run: `./mvnw test -Dtest=MasterContentIT`
Expected: FAIL saat kompilasi — `StatusTerbit` belum ada dan `searchMaster` belum punya lima parameter.

- [ ] **Step 3: Tulis enum dan query**

Buat `src/main/java/com/eduscreen/app/modules/assessment/domain/StatusTerbit.java`:

```java
package com.eduscreen.app.modules.assessment.domain;

/**
 * Penyaring keadaan terbit di ruang kerja konten master. Bukan kolom database — {@code published_at}
 * yang menyimpan keadaannya; ini hanya bahasa penyaring di permukaan HTTP.
 */
public enum StatusTerbit {
    DRAF,
    TERBIT
}
```

Di `QuestionRepository.java`, sebelum `}` penutup:

```java
    /**
     * Ruang kerja master, disaring pada yang masih digarap (BR-O05).
     *
     * <p>Query terpisah, bukan parameter boolean pada {@link #searchMaster} — pola yang sama
     * dipakai {@link #searchPublishedMaster}: yang membedakan ketiganya adalah siapa yang boleh
     * melihat draf, dan perbedaan sepenting itu tidak pantas disembunyikan di balik argumen.
     */
    @Query("select q from QuestionEntity q where q.clientId is null and q.publishedAt is null "
            + "and (:subjectId is null or q.topicId in (select t.id from TopicEntity t where t.subjectId = :subjectId)) "
            + "and (:topicId is null or q.topicId = :topicId) "
            + "and lower(q.bodyText) like :pattern "
            + "order by q.createdAt desc")
    Page<QuestionEntity> searchUnpublishedMaster(
            @Param("subjectId") UUID subjectId,
            @Param("topicId") UUID topicId,
            @Param("pattern") String pattern,
            Pageable pageable);
```

- [ ] **Step 4: Perluas service**

Di `QuestionService.java`, ganti method `searchMaster` (baris 93) menjadi:

```java
    /**
     * Ruang kerja Eduscreen Admin. {@code status} null berarti draf dan terbit ditampilkan
     * berdampingan — keadaan bawaan ruang kerja, yang memang harus melihat keduanya.
     */
    @Transactional(readOnly = true)
    public Page<QuestionEntity> searchMaster(UUID subjectId, UUID topicId, String q,
                                             StatusTerbit status, Pageable pageable) {
        String pattern = ExerciseService.likePattern(q);
        return switch (status) {
            case null -> questions.searchMaster(subjectId, topicId, pattern, pageable);
            case DRAF -> questions.searchUnpublishedMaster(subjectId, topicId, pattern, pageable);
            case TERBIT -> questions.searchPublishedMaster(subjectId, topicId, pattern, pageable);
        };
    }
```

Tambahkan importnya: `import com.eduscreen.app.modules.assessment.domain.StatusTerbit;`. Kalau anotasi `@Transactional(readOnly = true)` sudah menempel di method lama, jangan digandakan.

- [ ] **Step 5: Teruskan parameter dari controller**

Di `MasterContentController.search(...)`, tambahkan parameter dan teruskan:

```java
    @GetMapping("/eduscreen/soal")
    public String search(@RequestParam(required = false) UUID subjectId,
                         @RequestParam(required = false) UUID topicId,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) StatusTerbit status,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) UUID exerciseId,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         Model model) {
        model.addAttribute("hasil",
                questions.searchMaster(subjectId, topicId, q, status, PageRequest.of(page, UKURAN_HALAMAN)));
        model.addAttribute("status", status);
```

Sisa badan method tidak berubah. Tambahkan import `StatusTerbit`.

Cari juga pemanggil `searchMaster` lain yang kini kurang satu argumen dan kirimkan `null`:
`grep -rn "searchMaster(" src/main/java/ src/test/java/`

- [ ] **Step 6: Tambah dropdown di templat**

Di `src/main/resources/templates/soal/daftar.html`, di dalam form filter, sisipkan blok berikut tepat sebelum `<div class="flex-1">` yang memuat kotak "Cari":

```html
        <div th:if="${master ?: false}">
          <label for="status" class="block font-medium text-slate-700">Status</label>
          <select id="status" name="status" onchange="this.form.submit()"
                  class="mt-1 rounded border border-slate-300 px-2 py-1.5">
            <option value="">Semua</option>
            <option value="DRAF" th:selected="${status != null and status.name() == 'DRAF'}">Draf</option>
            <option value="TERBIT" th:selected="${status != null and status.name() == 'TERBIT'}">Terbit</option>
          </select>
        </div>
```

Penyaring ini hanya untuk ruang kerja master: bank soal Client tidak mengenal keadaan terbit sama sekali (`question_publish_master_only` di `V5`), jadi ia dipagari `${master ?: false}` seperti kolom Status yang sudah ada di baris 127.

- [ ] **Step 7: Jalankan tes, pastikan hijau**

Run: `./mvnw test -Dtest='MasterContentIT,MasterContentRenderTest'`
Expected: PASS.

- [ ] **Step 8: Verifikasi menyeluruh**

Run: `./mvnw test`
Expected: BUILD SUCCESS, nol Failures dan nol Errors. Kalau `AcceptanceCriteriaCoverageTest` gagal, ada `@DisplayName` baru yang belum menyebut `AC-*`/`TC-*`/`BR-*` — perbaiki namanya, jangan tesnya.

- [ ] **Step 9: Verifikasi manual**

```bash
docker start eduscreen-db
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Masuk sebagai `admin@eduscreen.id`, lalu buktikan berurutan:

1. `/eduscreen` menampilkan blok "Butuh perhatian" di atas dan tiga kartu di bawahnya.
2. Klik baris Question draf → mendarat di `/eduscreen/soal?status=DRAF`, dan daftarnya hanya memuat soal draf.
3. Nav header memuat empat butir di keempat halaman; butir halaman aktif tercetak tebal.
4. `/eduscreen/client` memuat daftar Client dan form onboarding; `/eduscreen` tidak.
5. Terbitkan seluruh isi paket yang macet → baris "macet" hilang dan berganti "siap".
6. Bereskan seluruh antrean → blok "Butuh perhatian" hilang sepenuhnya dan kartu naik jadi isi utama.
7. Keluar, masuk sebagai `guru@contoh.sch.id` → header tidak memuat satu pun tautan `/eduscreen`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/eduscreen/app/modules/assessment/domain/StatusTerbit.java \
        src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java \
        src/main/java/com/eduscreen/app/modules/assessment/service/QuestionService.java \
        src/main/java/com/eduscreen/app/modules/assessment/controller/MasterContentController.java \
        src/main/resources/templates/soal/daftar.html \
        src/test/java/com/eduscreen/app/modules/MasterContentIT.java
git commit -m "feat: penyaring status draf/terbit di ruang kerja konten master"
```
