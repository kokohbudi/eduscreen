---

description: "Task list for Portal Latihan Siswa Eduscreen (v1)"
---

# Tasks: Portal Latihan Siswa Eduscreen (v1)

**Input**: Design documents from `/specs/001-student-exercise-portal/`

**Prerequisites**: plan.md, spec.md, business-rules.md, data-model.md, contracts/

**Tests**: Tugas tes **disertakan dan wajib**. Bukan pilihan gaya: `CONSTITUTION.md` mewajibkannya
— TC-38 (Testcontainers, H2 dilarang), TC-39 (nama tes merujuk `AC-*`), TC-40 (ArchUnit di CI),
dan TC-41 (endpoint bersasaran tidak boleh digabung tanpa tes `404` lintas-Siswa dan
lintas-Client). Tes di luar keempat aturan itu tidak ditambahkan.

**Organization**: Tugas dikelompokkan per cerita pengguna agar tiap cerita bisa dikerjakan,
diuji, dan dikirim sendiri-sendiri.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: bisa berjalan paralel (berkas berbeda, tanpa ketergantungan)
- **[Story]**: cerita pengguna asal (US1–US7)
- Setiap tugas menyebut jalur berkas yang tepat

## Path Conventions

Proyek Maven monolit (lihat `plan.md` → Structure Decision):

- Kode: `src/main/java/com/eduscreen/app/`
- Migrasi: `src/main/resources/db/migration/`
- Templat: `src/main/resources/templates/`
- Tes: `src/test/java/com/eduscreen/app/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Menyiapkan proyek, build, dan perkakas dasar

- [x] T001 Buat kerangka proyek Maven dan `pom.xml` dengan Java 25 serta Spring Boot 3.5+ (Web, Security, Data JPA, Validation, Mail, Thymeleaf) di `pom.xml`
- [x] T002 Buat kelas utama `EduscreenApplication` di `src/main/java/com/eduscreen/app/EduscreenApplication.java`
- [x] T003 [P] Konfigurasikan Tailwind CSS v4 lewat `frontend-maven-plugin` di `pom.xml`, `package.json`, dan `src/main/resources/static/css/input.css` (v4 memakai konfigurasi CSS-first; `tailwind.config.js` tidak dipakai)
- [x] T004 [P] Tambahkan aset klien HTMX, Alpine.js, dan KaTeX lewat `package.json`, disalin ke `static/vendor/` saat build oleh `maven-resources-plugin` di `pom.xml`
- [x] T005 [P] Buat `docker-compose.yml` berisi PostgreSQL 16 untuk pengembangan lokal di `docker-compose.yml`
- [x] T006 [P] Susun `application.yml` dengan profil `local` dan `demo`, `ddl-auto: validate`, dan pembacaan `EDUSCREEN_ENV` di `src/main/resources/application.yml`
- [x] T007 [P] Konfigurasikan Flyway (lokasi migrasi, penamaan berversi) di `src/main/resources/application.yml`
- [x] T008 [P] Buat kelas dasar tes Testcontainers PostgreSQL di `src/test/java/com/eduscreen/app/support/PostgresTestBase.java`
- [x] T009 [P] Buat `ArchUnitRulesTest` yang menegakkan TC-01 sampai TC-03 di `src/test/java/com/eduscreen/app/architecture/ArchUnitRulesTest.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastruktur inti yang WAJIB selesai sebelum cerita pengguna mana pun dimulai

**⚠️ CRITICAL**: Tidak ada pekerjaan cerita pengguna yang boleh dimulai sebelum fase ini selesai

- [x] T010 Implementasikan generator UUID v7 dan tipe pengenal bersama di `src/main/java/com/eduscreen/app/shared/domain/UuidV7.java`
- [x] T011 Tulis migrasi skema ketenanan inti (`client`, `app_user`, `ruangan`, `ruangan_member`, `support_access_grant`, `user_invitation`) di `src/main/resources/db/migration/V1__core_tenancy.sql`
- [x] T012 [P] Buat entity dan repository `Client` di `src/main/java/com/eduscreen/app/modules/assessment/repository/ClientEntity.java` dan `ClientRepository.java`
- [x] T013 [P] Buat entity dan repository `AppUser` dengan `client_id` eksplisit di setiap method di `src/main/java/com/eduscreen/app/modules/assessment/repository/AppUserEntity.java` dan `AppUserRepository.java`
- [x] T014 [P] Buat entity dan repository `Ruangan` serta `RuanganMember` di `src/main/java/com/eduscreen/app/modules/assessment/repository/RuanganEntity.java`, `RuanganMemberEntity.java`, dan `RuanganRepository.java`
- [x] T015 Definisikan `IdentityProviderPort` di `src/main/java/com/eduscreen/app/modules/identity/port/out/IdentityProviderPort.java`
- [x] T016 Implementasikan `DummyIdentityProviderAdapter` dengan `@Profile({"local","demo"})` dan pemeriksaan gagal-cepat `EDUSCREEN_ENV` (TC-04) di `src/main/java/com/eduscreen/app/modules/identity/adapter/out/DummyIdentityProviderAdapter.java`
- [x] T017 Tambahkan pemeriksaan saat start yang menggagalkan aplikasi bila `IdentityProviderPort` aktif tidak tepat satu (TC-05) di `src/main/java/com/eduscreen/app/config/IdentityProviderStartupCheck.java`
- [x] T018 Susun Spring Security: form login, cookie `HttpOnly; Secure; SameSite=Lax`, perlindungan session fixation, CSRF untuk HTMX (TC-29) di `src/main/java/com/eduscreen/app/config/SecurityConfig.java`
- [x] T019 Buat `UserPrincipal` yang membawa `userId`, `clientId`, dan peran di `src/main/java/com/eduscreen/app/shared/security/UserPrincipal.java`
- [x] T020 Implementasikan pembatas laju login per akun dan per IP dengan penundaan menaik (TC-33) di `src/main/java/com/eduscreen/app/shared/security/LoginRateLimiter.java`
- [x] T021 Buat `GlobalExceptionAdvice` yang merender galat sebagai fragmen dan menjawab `401` + `HX-Redirect` untuk HTMX tak terautentikasi (TC-30, TC-31) di `src/main/java/com/eduscreen/app/shared/web/GlobalExceptionAdvice.java`
- [x] T022 [P] Definisikan `NotificationPort` dan implementasikan `SmtpNotificationAdapter` serta `NoOpNotificationAdapter` untuk profil `demo` (TC-49) di `src/main/java/com/eduscreen/app/modules/notification/`
- [x] T023 [P] Definisikan `FileStoragePort` dan implementasikan `LocalFileStorageAdapter` (TC-28) di `src/main/java/com/eduscreen/app/modules/storage/`
- [x] T024 [P] Susun log terstruktur yang membawa `clientId`, `userId`, `sessionId` dan menyaring password, jawaban, isi soal, serta email (TC-44) di `src/main/java/com/eduscreen/app/shared/web/LoggingContextFilter.java` dan `src/main/resources/logback-spring.xml`
- [x] T025 [P] Buat pembantu waktu: penyimpanan UTC, konversi ke zona Client hanya saat render (BR-T01, BR-T02) di `src/main/java/com/eduscreen/app/shared/domain/ClientClock.java`
- [x] T026 Buat tata letak dasar Thymeleaf beserta spanduk permanen environment `demo` (TC-47) di `src/main/resources/templates/layout/base.html`

**Checkpoint**: Fondasi siap — cerita pengguna boleh dimulai

---

## Phase 3: User Story 1 - Menyiapkan Ruangan dan penggunanya (Priority: P1) 🎯 MVP

**Goal**: Client Admin bisa membuat Ruangan, mendaftarkan Guru dan Siswa, menempatkan mereka, dan ketiga peran bisa masuk ke portalnya masing-masing.

**Independent Test**: Client Admin membuat satu Ruangan, satu Guru, dan lima Siswa; ketiga peran berhasil masuk dan masing-masing melihat portal yang sesuai — tanpa satu pun Exercise atau Assignment dibuat.

### Tests for User Story 1 (wajib per TC-41)

- [ ] T027 [P] [US1] Tulis tes IDOR lintas-Client untuk endpoint pengguna dan Ruangan yang membuktikan `404` seragam di `src/test/java/com/eduscreen/app/web/AccountIdorTest.java`
- [ ] T028 [P] [US1] Tulis tes integrasi `AC-U01`, `AC-U02`, `AC-U04`, `AC-P01` di `src/test/java/com/eduscreen/app/modules/RuanganAndAccountsIT.java`

### Implementation for User Story 1

- [ ] T029 [P] [US1] Implementasikan layanan undangan dan reset password lewat `NotificationPort` di `src/main/java/com/eduscreen/app/modules/identity/service/InvitationService.java`
- [ ] T030 [US1] Implementasikan `UserManagementService` (buat, undang ulang, nonaktifkan tanpa menghapus riwayat) di `src/main/java/com/eduscreen/app/modules/identity/service/UserManagementService.java`
- [ ] T031 [US1] Implementasikan `RuanganService` (buat, kelola anggota many-to-many, arsipkan, tolak perubahan pada Ruangan terarsip) di `src/main/java/com/eduscreen/app/modules/assessment/service/RuanganService.java`
- [ ] T032 [US1] Implementasikan `AuthController` untuk `/login`, `/logout`, `/undangan/{token}`, `/lupa-password`, `/reset/{token}` dengan kegagalan seragam di `src/main/java/com/eduscreen/app/modules/identity/controller/AuthController.java`
- [ ] T033 [US1] Implementasikan `UserAdminController` untuk `/admin/pengguna` di `src/main/java/com/eduscreen/app/modules/identity/controller/UserAdminController.java`
- [ ] T034 [US1] Implementasikan `RuanganAdminController` untuk `/admin/ruangan` di `src/main/java/com/eduscreen/app/modules/assessment/controller/RuanganAdminController.java`
- [ ] T035 [US1] Implementasikan pengalihan portal berbasis peran untuk `/` di `src/main/java/com/eduscreen/app/shared/web/PortalRoutingController.java`
- [ ] T036 [US1] Bangun kerangka portal Siswa yang menggabungkan seluruh Ruangan keanggotaannya di `src/main/resources/templates/siswa/portal.html`
- [ ] T037 [P] [US1] Bangun templat admin untuk pengguna dan Ruangan di `src/main/resources/templates/admin/pengguna.html` dan `src/main/resources/templates/admin/ruangan.html`
- [ ] T038 [US1] Implementasikan `SupportAccessService` dan pengendalinya: aktifkan 4 jam, baca-saja, catat setiap pembacaan (FR-006) di `src/main/java/com/eduscreen/app/modules/assessment/service/SupportAccessService.java`

**Checkpoint**: US1 berfungsi penuh dan bisa diuji sendiri

---

## Phase 4: User Story 2 - Menerbitkan Quiz dan mengerjakannya sampai keluar nilai (Priority: P1) 🎯 MVP

**Goal**: Guru meracik Exercise dari bank soal lintas Subject, menerbitkannya sebagai Quiz, Siswa mengerjakan dengan urutan teracak yang membeku dan tersimpan otomatis, nilai pilihan ganda keluar sendiri, dan Guru melihat rekap satu Ruangan.

**Independent Test**: Dengan satu Ruangan berisi lima Siswa, Guru menyusun Exercise sepuluh soal pilihan ganda, menerbitkannya, kelima Siswa mengerjakannya, dan Guru melihat rekap lengkap tanpa memeriksa satu lembar pun secara manual.

### Tests for User Story 2 (wajib per TC-41)

- [ ] T039 [P] [US2] Tulis tes IDOR endpoint sesi yang membuktikan `404` identik untuk sesi milik Siswa lain dan sesi tidak ada di `src/test/java/com/eduscreen/app/web/ExamSessionIdorTest.java`
- [ ] T040 [P] [US2] Tulis tes IDOR endpoint gambar dan bank soal lintas-Client di `src/test/java/com/eduscreen/app/web/ContentIdorTest.java`
- [ ] T041 [P] [US2] Tulis tes integrasi `AC-S01`, `AC-S02`, `AC-S03`, `AC-E02` di `src/test/java/com/eduscreen/app/modules/ExamSessionIT.java`
- [ ] T042 [P] [US2] Tulis tes integrasi waktu `AC-T01`, `AC-T03`, `AC-T04`, `AC-T06` di `src/test/java/com/eduscreen/app/modules/SessionTimingIT.java`
- [ ] T043 [P] [US2] Tulis tes balapan finalisasi `AC-T05` yang membuktikan tepat satu Result terbentuk di `src/test/java/com/eduscreen/app/modules/SessionFinalizationConcurrencyIT.java`
- [ ] T044 [P] [US2] Tulis tes integrasi rekap `AC-L01`, `AC-C01`, `AC-C04` di `src/test/java/com/eduscreen/app/modules/AssignmentReportIT.java`

### Implementation for User Story 2 — konten

- [ ] T045 [US2] Tulis migrasi taksonomi dan bank soal (`subject`, `topic`, `question`, `question_option`, `exercise`, `exercise_item`) beserta index pencarian di `src/main/resources/db/migration/V2__content.sql`
- [ ] T046 [P] [US2] Implementasikan `ContentSanitizer` berbasis allowlist OWASP yang menolak `<script>`, `<style>`, `<iframe>`, `on*`, dan `javascript:` (TC-22, TC-23) di `src/main/java/com/eduscreen/app/modules/assessment/service/ContentSanitizer.java`
- [ ] T047 [P] [US2] Implementasikan pengekstrak teks polos untuk kolom `*_text` turunan (TC-25) di `src/main/java/com/eduscreen/app/modules/assessment/service/PlainTextExtractor.java`
- [ ] T048 [P] [US2] Buat entity dan repository `Subject` serta `Topic` dengan penanda asal `GLOBAL`/`CLIENT` di `src/main/java/com/eduscreen/app/modules/assessment/repository/SubjectEntity.java`, `TopicEntity.java`, dan repositorinya
- [ ] T049 [P] [US2] Buat entity dan repository `Question` serta `QuestionOption` dengan `@SQLRestriction` untuk soft delete (TC-35) di `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionEntity.java`, `QuestionOptionEntity.java`, dan `QuestionRepository.java`
- [ ] T050 [P] [US2] Buat entity dan repository `Exercise` serta `ExerciseItem` di `src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseEntity.java`, `ExerciseItemEntity.java`, dan `ExerciseRepository.java`
- [ ] T051 [US2] Implementasikan `TaxonomyService` yang menggabungkan Subject global dan lokal serta membolehkan Topic lokal di bawah Subject global di `src/main/java/com/eduscreen/app/modules/assessment/service/TaxonomyService.java`
- [ ] T052 [US2] Implementasikan `QuestionService`: sanitasi saat tulis, validasi tepat satu Option benar, pencarian atas kolom teks polos, soft delete di `src/main/java/com/eduscreen/app/modules/assessment/service/QuestionService.java`
- [ ] T053 [US2] Implementasikan `ImageService`: validasi magic bytes, batas ukuran, encode ulang saat simpan (TC-27) di `src/main/java/com/eduscreen/app/modules/assessment/service/ImageService.java`
- [ ] T054 [US2] Implementasikan `ImageController` yang melayani `/gambar/{id}` dengan pemeriksaan `client_id` dan `Cache-Control: private` (TC-26) di `src/main/java/com/eduscreen/app/modules/assessment/controller/ImageController.java`
- [ ] T055 [US2] Implementasikan `ExerciseService`: perakitan lintas Subject, pengurutan, penguncian `locked_at`, duplikasi di `src/main/java/com/eduscreen/app/modules/assessment/service/ExerciseService.java`
- [ ] T056 [US2] Implementasikan `QuestionBankController` untuk `/soal` dan `/subject` di `src/main/java/com/eduscreen/app/modules/assessment/controller/QuestionBankController.java`
- [ ] T057 [US2] Implementasikan `ExerciseController` untuk `/exercise` di `src/main/java/com/eduscreen/app/modules/assessment/controller/ExerciseController.java`
- [ ] T058 [P] [US2] Bangun templat editor soal dengan KaTeX dan unggah gambar di `src/main/resources/templates/soal/editor.html`
- [ ] T059 [P] [US2] Bangun templat perakit Exercise dengan penelusuran bank soal berbasis HTMX di `src/main/resources/templates/exercise/builder.html`

### Implementation for User Story 2 — penerbitan dan pengerjaan

- [ ] T060 [US2] Tulis migrasi asesmen (`assignment`, `exam_session`, `session_question`, `session_answer`, `result`) beserta unique constraint `result.session_id` dan index yang disebut `data-model.md` di `src/main/resources/db/migration/V3__assessment.sql`
- [ ] T061 [P] [US2] Buat entity dan repository `Assignment` di `src/main/java/com/eduscreen/app/modules/assessment/repository/AssignmentEntity.java` dan `AssignmentRepository.java`
- [ ] T062 [P] [US2] Buat entity dan repository `ExamSession`, `SessionQuestion`, `SessionAnswer`, `Result` dengan method `findByIdForUpdate` dan penyaringan `client_id` eksplisit (TC-18, TC-36) di `src/main/java/com/eduscreen/app/modules/assessment/repository/`
- [ ] T063 [US2] Implementasikan `AssignmentPublishingService`: gerbang validasi saat terbit, penguncian Exercise, penerbitan borongan menjadi N Assignment di `src/main/java/com/eduscreen/app/modules/assessment/service/AssignmentPublishingService.java`
- [ ] T064 [US2] Implementasikan siklus hidup Assignment: perpanjang saja, tutup lebih awal yang memfinalisasi sesi berjalan, hapus hanya `DRAFT` di `src/main/java/com/eduscreen/app/modules/assessment/service/AssignmentLifecycleService.java`
- [ ] T065 [US2] Implementasikan `ExamSessionService.start`: pembuatan malas, pengembalian sesi berjalan, pengacakan, pembekuan snapshot dan `effective_deadline` di `src/main/java/com/eduscreen/app/modules/assessment/service/ExamSessionService.java`
- [ ] T066 [US2] Implementasikan `AnswerService` sebagai upsert idempoten berkunci `session_question_id`, menolak kiriman lewat waktu dengan `410` (TC-20) di `src/main/java/com/eduscreen/app/modules/assessment/service/AnswerService.java`
- [ ] T067 [US2] Implementasikan `SessionFinalizer` dengan kunci pesimistis, pemeriksaan status setelah kunci, tiga `terminal_reason`, dan idempotensi (TC-18, TC-19, TC-21) di `src/main/java/com/eduscreen/app/modules/assessment/service/SessionFinalizer.java`
- [ ] T068 [US2] Implementasikan `ScoringService` untuk pilihan ganda dengan bobot seragam dan soal kosong dihitung salah di `src/main/java/com/eduscreen/app/modules/assessment/service/ScoringService.java`
- [ ] T069 [US2] Implementasikan `AssignmentController` untuk `/guru/assignment` di `src/main/java/com/eduscreen/app/modules/assessment/controller/AssignmentController.java`
- [ ] T070 [US2] Implementasikan `ExamSessionController` untuk `/siswa/assignment/{id}/mulai`, `/siswa/sesi/**`, termasuk auto-save yang mengembalikan fragmen (TC-14) di `src/main/java/com/eduscreen/app/modules/assessment/controller/ExamSessionController.java`
- [ ] T071 [US2] Implementasikan endpoint sisa waktu dan heartbeat yang tidak memperpanjang batas pengerjaan (TC-32) di `src/main/java/com/eduscreen/app/modules/assessment/controller/SessionTimeController.java`
- [ ] T072 [P] [US2] Bangun halaman pengerjaan Quiz: peta soal, navigasi bebas, indikator koneksi, antrean kirim ulang di `src/main/resources/templates/siswa/pengerjaan.html`
- [ ] T073 [P] [US2] Bangun komponen Alpine hitung mundur yang hanya menampilkan sisa waktu dari server (TC-15) di `src/main/resources/static/js/countdown.js`
- [ ] T074 [P] [US2] Bangun templat formulir penerbitan Assignment di `src/main/resources/templates/guru/terbit.html`
- [ ] T075 [US2] Implementasikan `ReportService`: rekap dibangun dari anggota Ruangan, finalisasi saat dibaca satu transaksi per sesi, `NOT_STARTED` tanpa membuat baris sesi di `src/main/java/com/eduscreen/app/modules/assessment/service/ReportService.java`
- [ ] T076 [US2] Implementasikan `ReportController` dan templat rekap Ruangan di `src/main/java/com/eduscreen/app/modules/assessment/controller/ReportController.java` dan `src/main/resources/templates/guru/rekap.html`

**Checkpoint**: US1 dan US2 berfungsi sendiri-sendiri — ini lingkup MVP

---

## Phase 5: User Story 3 - Berlatih dengan pembahasan seketika (Priority: P2)

**Goal**: Guru menerbitkan Exercise sebagai Practice; Siswa mendapat benar/salah dan pembahasan seketika per soal, maju satu arah, boleh mengulang tanpa batas, dan aktivitasnya terpisah dari rekap nilai.

**Independent Test**: Guru menerbitkan satu Exercise pilihan ganda sebagai Practice; Siswa mengerjakannya, melihat pembahasan tiap soal, mengulang dua kali, dan aktivitasnya terlihat Guru terpisah dari rekap nilai.

### Tests for User Story 3

- [ ] T077 [P] [US3] Tulis tes integrasi `AC-S04`, `AC-M01`, `AC-Q05`, `AC-C03`, `AC-C05` di `src/test/java/com/eduscreen/app/modules/PracticeModeIT.java`
- [ ] T078 [P] [US3] Tulis tes yang membuktikan kiriman ulang jawaban identik pada soal terkunci tetap sukses, dan jawaban berbeda ditolak `409` di `src/test/java/com/eduscreen/app/modules/PracticeIdempotencyIT.java`

### Implementation for User Story 3

- [ ] T079 [US3] Tambahkan gerbang validasi Practice saat terbit: tolak Exercise beressay atau bersoal tanpa pembahasan, sebutkan soal penyebabnya di `src/main/java/com/eduscreen/app/modules/assessment/service/AssignmentPublishingService.java`
- [ ] T080 [US3] Implementasikan penguncian jawaban per soal dan penyingkapan pembahasan seketika pada mode Practice di `src/main/java/com/eduscreen/app/modules/assessment/service/AnswerService.java`
- [ ] T081 [US3] Terapkan navigasi maju satu arah untuk Practice dan tolak lompatan dengan `409` di `src/main/java/com/eduscreen/app/modules/assessment/controller/ExamSessionController.java`
- [ ] T082 [US3] Bebaskan batas pengulangan untuk Practice dan tandai Result sebagai `kind = PRACTICE` yang langsung `FINAL` di `src/main/java/com/eduscreen/app/modules/assessment/service/ExamSessionService.java`
- [ ] T083 [P] [US3] Bangun halaman pengerjaan Practice dengan pembahasan sebaris di `src/main/resources/templates/siswa/practice.html`
- [ ] T084 [US3] Implementasikan halaman aktivitas latihan yang terpisah dari rekap nilai di `src/main/java/com/eduscreen/app/modules/assessment/controller/ReportController.java` dan `src/main/resources/templates/guru/latihan.html`

**Checkpoint**: US1, US2, dan US3 berfungsi sendiri-sendiri

---

## Phase 6: User Story 4 - Menilai jawaban essay (Priority: P2)

**Goal**: Siswa menjawab essay; nilai pilihan ganda tampil sementara; Guru menilai 0–100; hasil menjadi final; setiap perubahan nilai meninggalkan jejak permanen.

**Independent Test**: Guru menerbitkan Exercise berisi sembilan soal pilihan ganda dan satu essay; Siswa mengerjakannya; Guru menilai essaynya; nilai akhir berubah sesuai penilaian.

### Tests for User Story 4

- [ ] T085 [P] [US4] Tulis tes integrasi `AC-C02`, `AC-G01`, `AC-G02` di `src/test/java/com/eduscreen/app/modules/EssayGradingIT.java`
- [ ] T086 [P] [US4] Tulis tes IDOR penilaian yang membuktikan Guru di luar Ruangan mendapat `404` di `src/test/java/com/eduscreen/app/web/GradingIdorTest.java`

### Implementation for User Story 4

- [ ] T087 [US4] Tulis migrasi `score_audit` hanya-sisip (TC-37) di `src/main/resources/db/migration/V4__score_audit.sql`
- [ ] T088 [P] [US4] Buat entity dan repository `ScoreAudit` di `src/main/java/com/eduscreen/app/modules/assessment/repository/ScoreAuditEntity.java` dan `ScoreAuditRepository.java`
- [ ] T089 [US4] Tambahkan penangkapan jawaban essay dan penandaan Result `PENDING_REVIEW` saat finalisasi di `src/main/java/com/eduscreen/app/modules/assessment/service/SessionFinalizer.java`
- [ ] T090 [US4] Implementasikan `GradingService`: nilai 0–100 menjadi pecahan poin, perhitungan ulang Result seketika, transisi ke `FINAL`, penulisan `score_audit` di `src/main/java/com/eduscreen/app/modules/assessment/service/GradingService.java`
- [ ] T091 [US4] Implementasikan `GradingController` untuk antrean penilaian dan pemberian nilai di `src/main/java/com/eduscreen/app/modules/assessment/controller/GradingController.java`
- [ ] T092 [P] [US4] Bangun templat antrean penilaian essay di `src/main/resources/templates/guru/penilaian.html`
- [ ] T093 [US4] Tampilkan penanda nilai sementara pada hasil `PENDING_REVIEW` di halaman hasil Siswa di `src/main/resources/templates/siswa/hasil.html`

**Checkpoint**: Empat cerita pertama berfungsi sendiri-sendiri

---

## Phase 7: User Story 5 - Membuka Client baru dengan bank soal terisi (Priority: P3)

**Goal**: Eduscreen Admin mendaftarkan Client baru beserta akun admin pertamanya dan menyalin paket konten master, sehingga sekolah punya materi siap pakai di hari pertama.

**Independent Test**: Eduscreen Admin membuat satu Client dengan satu paket master berisi 20 soal; Client Admin masuk dan menemukan 20 soal itu sudah ada di bank soalnya.

### Tests for User Story 5

- [ ] T094 [P] [US5] Tulis tes integrasi `AC-O01` dan `AC-O02` yang membuktikan salinan terpisah dan Subject global tidak diduplikasi di `src/test/java/com/eduscreen/app/modules/ClientOnboardingIT.java`

### Implementation for User Story 5

- [ ] T095 [US5] Implementasikan `ContentAdoptionService` yang menyalin Topic, Question, Option, Exercise, dan ExerciseItem beserta `source_question_id` di `src/main/java/com/eduscreen/app/modules/assessment/service/ContentAdoptionService.java`
- [ ] T096 [US5] Implementasikan `ClientOnboardingService`: buat Client dengan zona waktu, akun Client Admin pertama beserta undangannya, lalu salin paket terpilih di `src/main/java/com/eduscreen/app/modules/assessment/service/ClientOnboardingService.java`
- [ ] T097 [US5] Implementasikan `EduscreenAdminController` untuk `/eduscreen/client` dan `/eduscreen/subject` di `src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenAdminController.java`
- [ ] T098 [US5] Implementasikan `CatalogController` untuk `/katalog` dan `/katalog/adopsi` di `src/main/java/com/eduscreen/app/modules/assessment/controller/CatalogController.java`
- [ ] T099 [P] [US5] Bangun templat pendaftaran Client dan penelusuran katalog master di `src/main/resources/templates/eduscreen/client.html` dan `src/main/resources/templates/katalog/index.html`

**Checkpoint**: Lima cerita pertama berfungsi sendiri-sendiri

---

## Phase 8: User Story 6 - Memindahkan bank soal warisan (Priority: P3)

**Goal**: Client Admin mengunggah berkas soal, memeriksa pratinjau, membaca kegagalan per baris, lalu menyimpan yang valid.

**Independent Test**: Client Admin mengunggah berkas 500 baris berisi tujuh baris cacat; sistem menampilkan 493 baris valid dan tujuh kegagalan bernomor baris; penyimpanan memasukkan 493 soal.

### Tests for User Story 6

- [ ] T100 [P] [US6] Tulis tes integrasi `AC-Q03` dan `AC-Q06` yang mencakup penolakan berkas 2.000 baris di `src/test/java/com/eduscreen/app/modules/QuestionImportIT.java`

### Implementation for User Story 6

- [ ] T101 [US6] Implementasikan pengurai Excel/CSV dengan validasi per baris dan pesan galat bernomor baris di `src/main/java/com/eduscreen/app/modules/assessment/service/QuestionImportParser.java`
- [ ] T102 [US6] Implementasikan `QuestionImportService`: batas 500 baris ditolak sebelum diproses, pratinjau bertoken, penyimpanan sinkron hanya baris valid, sanitasi lewat jalur yang sama dengan editor di `src/main/java/com/eduscreen/app/modules/assessment/service/QuestionImportService.java`
- [ ] T103 [US6] Implementasikan `ImportController` untuk `/admin/impor` di `src/main/java/com/eduscreen/app/modules/assessment/controller/ImportController.java`
- [ ] T104 [P] [US6] Bangun templat unggah, pratinjau, dan laporan kegagalan, beserta berkas templat unduhan di `src/main/resources/templates/admin/impor.html` dan `src/main/resources/static/templat-impor.csv`

**Checkpoint**: Enam cerita pertama berfungsi sendiri-sendiri

---

## Phase 9: User Story 7 - Mengulang untuk memperbaiki nilai (Priority: P3)

**Goal**: Quiz boleh dikerjakan beberapa kali dengan snapshot baru tiap kali; nilai resmi adalah yang tertinggi; seluruh pengerjaan tetap bisa dibuka Guru.

**Independent Test**: Guru menerbitkan Quiz dengan batas tiga kali pengerjaan; satu Siswa memperoleh 60, 85, lalu 70; rekap Guru menampilkan 85 dan ketiga pengerjaan tetap bisa dibuka.

### Tests for User Story 7

- [ ] T105 [P] [US7] Tulis tes integrasi `AC-S05` dan `AC-L02` di `src/test/java/com/eduscreen/app/modules/MultiAttemptIT.java`

### Implementation for User Story 7

- [ ] T106 [US7] Terapkan penomoran dan penegakan `max_attempts` untuk Quiz, dengan snapshot baru tiap pengerjaan di `src/main/java/com/eduscreen/app/modules/assessment/service/ExamSessionService.java`
- [ ] T107 [US7] Hitung nilai resmi sebagai skor tertinggi di antara seluruh pengerjaan di `src/main/java/com/eduscreen/app/modules/assessment/service/ReportService.java`
- [ ] T108 [US7] Implementasikan halaman riwayat pengerjaan per Siswa untuk Guru di `src/main/java/com/eduscreen/app/modules/assessment/controller/ReportController.java` dan `src/main/resources/templates/guru/riwayat-siswa.html`
- [ ] T109 [P] [US7] Tampilkan riwayat pengerjaan dan nilai terbaik di portal Siswa di `src/main/resources/templates/siswa/portal.html`

**Checkpoint**: Seluruh cerita pengguna berfungsi sendiri-sendiri

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Penyelesaian yang menyentuh banyak cerita sekaligus

- [ ] T110 [P] Susun konfigurasi profil `demo`: spanduk permanen, email dimatikan, larangan pemulihan dari cadangan produksi (TC-47, TC-48, TC-49) di `src/main/resources/application-demo.yml` dan `docs/runbook-demo.md`
- [ ] T111 [P] Tulis runbook pencadangan harian, arsip WAL, dan uji pemulihan terjadwal (TC-43) di `docs/runbook-backup.md`
- [ ] T112 [P] Verifikasi seluruh index yang disebut `data-model.md` sudah ada dan terpakai lewat `EXPLAIN` pada query rekap dan penelusuran bank soal di `src/main/resources/db/migration/`
- [ ] T113 Jalankan uji beban terhadap sasaran SC-006 dan catat hasilnya sebagai bukti atau bantahan hipotesis di `docs/load-test-report.md`
- [ ] T114 [P] Rapikan tampilan tanggap di seluruh halaman pengerjaan dan admin di `src/main/resources/templates/`
- [ ] T115 [P] Pastikan setiap nama tes merujuk pengenal `AC-*` dan jalankan pemeriksaan cakupannya (TC-39) di `src/test/java/com/eduscreen/app/`
- [ ] T116 Jalankan seluruh skenario validasi `quickstart.md` dari V1 sampai V7 beserta tabel kasus tepinya

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: tanpa ketergantungan — bisa langsung dimulai
- **Foundational (Phase 2)**: menunggu Setup — MEMBLOKIR seluruh cerita pengguna
- **User Stories (Phase 3+)**: seluruhnya menunggu Foundational selesai
- **Polish (Phase 10)**: menunggu cerita yang diinginkan selesai

### User Story Dependencies

- **US1 (P1)**: bisa dimulai setelah Foundational. Tanpa ketergantungan pada cerita lain.
- **US2 (P1)**: bisa dimulai setelah Foundational. Menghadirkan seluruh entitas kontennya sendiri; untuk diuji ia butuh Ruangan berisi Siswa, yang bisa disiapkan lewat data awal profil `local` sehingga tetap bisa diuji tanpa antarmuka US1.
- **US3 (P2)**: memperluas mesin pengerjaan US2 — tumpang tindih berkas pada `AnswerService`, `ExamSessionService`, `AssignmentPublishingService`. Kerjakan **setelah** US2, bukan paralel dengannya.
- **US4 (P2)**: memperluas `SessionFinalizer` dan menambah `GradingService`. Menyentuh `SessionFinalizer` yang sama dengan US3; kerjakan setelah US2, dan koordinasikan bila paralel dengan US3.
- **US5 (P3)**: hanya bergantung pada entitas konten US2. Bisa paralel dengan US3 dan US4.
- **US6 (P3)**: hanya bergantung pada `QuestionService` dan `ContentSanitizer` US2. Bisa paralel dengan US3, US4, dan US5.
- **US7 (P3)**: memperluas `ExamSessionService` dan `ReportService` US2. Menyentuh berkas yang sama dengan US3; jangan dijalankan paralel dengan US3.

### Within Each User Story

- Tes ditulis lebih dulu dan harus GAGAL sebelum implementasi
- Migrasi sebelum entity
- Entity sebelum service
- Service sebelum controller
- Controller sebelum templat
- Cerita selesai sebelum berpindah ke prioritas berikutnya

### Parallel Opportunities

- Seluruh tugas Setup bertanda [P] bisa berjalan bersamaan
- Di Foundational, T012–T014 dan T022–T026 bertanda [P] bisa berjalan bersamaan setelah T010 dan T011 selesai
- Setelah Foundational selesai, **US1 dan US2 bisa dikerjakan dua orang secara paralel** — keduanya menyentuh berkas yang berbeda sepenuhnya
- Seluruh tugas tes dalam satu cerita bisa ditulis bersamaan
- Entity dalam satu cerita bertanda [P] bisa dibuat bersamaan
- **US5 dan US6 bisa paralel** satu sama lain dan dengan US3/US4
- **US3 dan US7 TIDAK boleh paralel** — keduanya mengubah `ExamSessionService` dan `AnswerService`

---

## Parallel Example: User Story 2

```bash
# Tulis seluruh tes US2 bersamaan (semuanya berkas berbeda):
Task: "Tes IDOR endpoint sesi di src/test/java/com/eduscreen/app/web/ExamSessionIdorTest.java"
Task: "Tes IDOR gambar dan bank soal di src/test/java/com/eduscreen/app/web/ContentIdorTest.java"
Task: "Tes integrasi AC-S01..S03 di src/test/java/com/eduscreen/app/modules/ExamSessionIT.java"
Task: "Tes waktu AC-T01..T06 di src/test/java/com/eduscreen/app/modules/SessionTimingIT.java"
Task: "Tes balapan finalisasi AC-T05 di src/test/java/com/eduscreen/app/modules/SessionFinalizationConcurrencyIT.java"
Task: "Tes rekap AC-L01, AC-C01, AC-C04 di src/test/java/com/eduscreen/app/modules/AssignmentReportIT.java"

# Setelah migrasi V2 (T045) selesai, buat seluruh entity konten bersamaan:
Task: "Entity Subject dan Topic di src/main/java/com/eduscreen/app/modules/assessment/repository/"
Task: "Entity Question dan QuestionOption di src/main/java/com/eduscreen/app/modules/assessment/repository/"
Task: "Entity Exercise dan ExerciseItem di src/main/java/com/eduscreen/app/modules/assessment/repository/"

# Utilitas konten juga paralel:
Task: "ContentSanitizer di src/main/java/com/eduscreen/app/modules/assessment/service/ContentSanitizer.java"
Task: "PlainTextExtractor di src/main/java/com/eduscreen/app/modules/assessment/service/PlainTextExtractor.java"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Selesaikan Phase 1: Setup
2. Selesaikan Phase 2: Foundational (KRITIS — memblokir seluruh cerita)
3. Selesaikan Phase 3: US1 dan Phase 4: US2
4. **BERHENTI dan VALIDASI**: jalankan skenario V1 dan V2 di `quickstart.md`
5. Peragakan ke calon Client di environment `demo` berdata karangan

MVP di sini adalah **dua** cerita P1, bukan satu. US2 sendirian tidak bisa diperagakan tanpa
Ruangan berisi orang, dan US1 sendirian belum menghadirkan nilai apa pun bagi Guru maupun Siswa.

### Incremental Delivery

1. Setup + Foundational → fondasi siap
2. US1 + US2 → uji sendiri → peragakan (MVP)
3. US3 → mode Practice → uji sendiri → peragakan (nilai jual bimbel)
4. US4 → essay → uji sendiri → peragakan (mata pelajaran non-pilihan-ganda)
5. US5 + US6 → onboarding dan migrasi bank soal → mempercepat Client baru
6. US7 → pengulangan
7. Phase 10 → pemolesan dan bukti beban

### Parallel Team Strategy

Dengan tiga pengembang:

1. Seluruh tim menyelesaikan Setup + Foundational bersama
2. Setelah Foundational selesai:
   - Pengembang A: US1 (identitas, akun, Ruangan)
   - Pengembang B dan C: US2 — B mengambil jalur konten (T045–T059), C mengambil jalur pengerjaan (T060–T076); keduanya bertemu di T060
3. Gelombang berikutnya: A mengambil US5, B mengambil US6, C mengambil US3 lalu US4
4. US7 dikerjakan siapa pun yang bebas **setelah** US3 selesai

---

## Gerbang rilis di luar daftar tugas ini

Bukan tugas implementasi fitur, tetapi memblokir rilis kepada Client berdata nyata:

- Adapter dummy tidak boleh menyentuh data siswa sungguhan (TC-34). Client pertama yang membawa
  data nyata menuntut Keycloak terpasang, atau adapter lokal berbasis BCrypt beserta kebijakan
  passwordnya — dan kebijakan itu harus ada **sebelum** akun sungguhan pertama dibuat (ADR-0016).

---

## Notes

- Tugas [P] = berkas berbeda, tanpa ketergantungan
- Label [Story] memetakan tugas ke cerita pengguna untuk telusur
- Pastikan tes gagal sebelum implementasi
- Commit setelah tiap tugas atau kelompok logis
- Berhenti di tiap checkpoint untuk memvalidasi cerita secara mandiri
- Setiap endpoint bersasaran baru wajib membawa tes `404` lintas-Siswa dan lintas-Client sebelum
  digabung (TC-41) — ini bukan tugas terpisah melainkan syarat penggabungan
