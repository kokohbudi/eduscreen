---

description: "Task list for Question Bank Master Eduscreen (v1)"
---

# Tasks: Question Bank Master Eduscreen (v1)

**Input**: Design documents from `/specs/002-master-question-bank/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tugas tes **disertakan dan wajib**, sama seperti spesifikasi 001. Bukan pilihan gaya:
`CONSTITUTION.md` mewajibkannya — TC-38 (Testcontainers, H2 dilarang), TC-40 (ArchUnit di CI), dan
TC-41 (endpoint bersasaran tidak boleh digabung tanpa tes `404` lintas-Client). Tes di luar aturan
itu tidak ditambahkan.

**Organization**: Tugas dikelompokkan per cerita pengguna agar tiap cerita bisa dikerjakan, diuji,
dan dikirim sendiri-sendiri.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: bisa berjalan paralel (berkas berbeda, tanpa ketergantungan)
- **[Story]**: cerita pengguna asal (US1–US5)
- Setiap tugas menyebut jalur berkas yang tepat

## Path Conventions

Proyek Maven monolit yang sudah berdiri (lihat `plan.md` → Structure Decision):

- Kode: `src/main/java/com/eduscreen/app/`
- Migrasi: `src/main/resources/db/migration/`
- Templat: `src/main/resources/templates/`
- Tes: `src/test/java/com/eduscreen/app/`

Tidak ada penyiapan proyek, build, maupun dependensi baru: fitur ini menumpang seluruh perkakas yang
sudah dipasang spesifikasi 001. `pom.xml` tidak berubah.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Memastikan titik berangkat bersih sebelum apa pun disentuh

- [X] T001 Jalankan `./mvnw test` dan pastikan seluruh tes hijau sebelum perubahan pertama; catat kegagalan yang sudah ada sebagai baseline agar tidak tertukar dengan regresi fitur ini

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Skema dan entitas yang dibutuhkan seluruh cerita

**⚠️ CRITICAL**: Tidak ada pekerjaan cerita pengguna yang boleh dimulai sebelum fase ini selesai

- [X] T002 Buat migrasi `src/main/resources/db/migration/V5__master_publishing.sql`: kolom `published_at timestamptz` pada `question` dan `exercise`, check constraint `question_publish_master_only` dan `exercise_publish_master_only` (`published_at is null or client_id is null`), indeks parsial `question_master_published` dan `question_adopted_source` — persis seperti `data-model.md`
- [X] T003 [P] Tambahkan field `publishedAt` beserta `publish(OffsetDateTime)`, `unpublish()`, dan `isPublished()` di `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionEntity.java`
- [X] T004 [P] Tambahkan field `publishedAt` beserta `publish(OffsetDateTime)`, `unpublish()`, dan `isPublished()` di `src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseEntity.java`
- [X] T005 Jalankan `./mvnw test -Dtest=PostgresSmokeTest` untuk membuktikan migrasi `V5` jalan bersih di PostgreSQL sungguhan (TC-38)

**Checkpoint**: Skema siap — seluruh cerita pengguna bisa dimulai

---

## Phase 3: User Story 1 - Eduscreen Admin menulis Question master (Priority: P1) 🎯 MVP

**Goal**: Memberi Eduscreen Admin tempat membuat Topic Eduscreen, menulis Question master, dan
menemukannya kembali lewat pencarian.

**Independent Test**: Eduscreen Admin membuat satu Topic, menulis lima Question di bawahnya, lalu
menemukan kelimanya lewat pencarian kata pada isi soal — seluruhnya tanpa menyentuh Client mana pun.

### Tests for User Story 1 (wajib per TC-41)

- [X] T006 [P] [US1] Buat `src/test/java/com/eduscreen/app/modules/MasterContentIT.java` dengan kasus authoring: Topic `GLOBAL` lahir tanpa pemilik Client, Question `MULTIPLE_CHOICE` dan `ESSAY` tersimpan sebagai konten master, pencarian menemukan keduanya, dan Question pilihan ganda dengan dua kunci benar ditolak `400` bukan `500`
- [X] T007 [P] [US1] Tambahkan di `src/test/java/com/eduscreen/app/web/ContentIdorTest.java`: Guru, Siswa, dan Client Admin ditolak di `/eduscreen/soal`; Eduscreen Admin menerima `404` saat meminta Question milik sebuah Client lewat rute master, tak terbedakan dari pengenal yang tidak ada

### Implementation for User Story 1

- [X] T008 [US1] Tambahkan `createGlobalTopic(UUID subjectId, String name)` dan `requireWritableTopic(UUID id, UUID clientId)` di `src/main/java/com/eduscreen/app/modules/assessment/service/TaxonomyService.java`; `clientId` null berarti Topic wajib ber-`origin = GLOBAL`, dan Subject induk wajib `GLOBAL` juga
- [X] T009 [US1] Ubah tanda tangan `create` dan `update` di `src/main/java/com/eduscreen/app/modules/assessment/service/QuestionService.java` dari `UserPrincipal author` menjadi `UUID clientId`, buang panggilan `author.requireClientId()`, dan ganti `requireVisibleTopic` menjadi `requireWritableTopic` (depends on T008)
- [X] T010 [US1] Sesuaikan pemanggilan di `src/main/java/com/eduscreen/app/modules/assessment/controller/QuestionBankController.java` baris 129 dan 158 agar mengirim `principal.requireClientId()` (depends on T009)
- [X] T011 [US1] Tambahkan penyaringan `subjectId` pada `searchMaster` di `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java` lewat subquery ke `TopicEntity`, karena `question` tidak membawa `subject_id` sendiri
- [X] T012 [US1] Buat `src/main/java/com/eduscreen/app/modules/assessment/controller/MasterContentController.java` dengan rute Topic `GET`/`POST /eduscreen/subject/{id}/topic` sesuai `contracts/master-authoring.md` (depends on T008)
- [X] T013 [US1] Tambahkan rute Question master di `MasterContentController`: `GET /eduscreen/soal`, `GET /eduscreen/soal/baru`, `POST /eduscreen/soal`, `GET`/`PUT`/`DELETE /eduscreen/soal/{id}` — seluruhnya memanggil `QuestionService` dengan `clientId` null (depends on T009, T011, T012)
- [X] T014 [P] [US1] Parameterkan `src/main/resources/templates/soal/daftar.html` dan `src/main/resources/templates/soal/editor.html` dengan atribut model `basePath` (`/soal` atau `/eduscreen/soal`) agar satu pasang templat melayani dua tempat pasang (D6)
- [X] T015 [US1] Tambahkan tautan ke ruang kerja konten master di `src/main/resources/templates/eduscreen/client.html`
- [X] T042 [US1] Longgarkan matcher `POST /gambar` di `src/main/java/com/eduscreen/app/config/SecurityConfig.java` agar juga menerima `EDUSCREEN_ADMIN`; Eduscreen Admin wajib bisa menyisipkan gambar ke Question master (FR-063)
- [X] T043 [US1] Ganti `user.requireClientId()` menjadi `user.clientId()` di `src/main/java/com/eduscreen/app/modules/assessment/controller/ImageController.java` baris 40; `ImageService.require` sudah membolehkan gambar master (`clientId` null) dibaca semua pemanggil, yang melempar hanyalah pagar di controller (FR-063, TC-26)

**Checkpoint**: Eduscreen Admin sudah bisa menulis dan menemukan Question master. Lubang inti tertutup.

---

## Phase 4: User Story 2 - Menerbitkan dan menarik konten master (Priority: P1) 🎯 MVP

**Goal**: Konten master hidup sebagai draf sampai diterbitkan, bisa ditarik kembali, dan tidak
pernah merambat ke Client yang sudah mengadopsi.

**Independent Test**: Buat dua Question master, terbitkan satu, lalu buka katalog dari sebuah Client:
hanya satu yang terlihat dan hanya satu yang bisa diadopsi.

### Tests for User Story 2 (wajib per TC-41)

- [X] T016 [P] [US2] Tambahkan di `MasterContentIT`: Question belum terbit tidak muncul di katalog dan tidak bisa diadopsi meski pengenalnya ditebak (`404`); menarik Question yang sudah diadopsi 40 Client tidak mengubah satu pun salinan; mengubah master yang sudah terbit tidak mengubah salinan Client
- [X] T017 [P] [US2] Tambahkan di `MasterContentIT`: menerbitkan paket yang masih memuat Question belum terbit ditolak dengan pesan yang menyebut Question penyebabnya. Diuji lewat `MasterPublishingService` langsung, bukan lewat rute — rute paket baru lahir di US3, dan US2 harus tetap bisa diuji sendiri

### Implementation for User Story 2

- [X] T018 [US2] Tambahkan `searchPublishedMaster(subjectId, topicId, pattern, pageable)`, `findPublishedMasterById(id)`, dan `findUnpublishedInExercise(exerciseId)` di `src/main/java/com/eduscreen/app/modules/assessment/repository/QuestionRepository.java` sebagai `@Query` terpisah, bukan parameter boolean pada query yang sudah ada (D4)
- [X] T019 [P] [US2] Tambahkan `searchPublishedMaster(pattern, pageable)` dan `findPublishedMasterById(id)` di `src/main/java/com/eduscreen/app/modules/assessment/repository/ExerciseRepository.java`
- [X] T020 [US2] Buat `src/main/java/com/eduscreen/app/modules/assessment/service/MasterPublishingService.java`: `publishQuestion`, `unpublishQuestion`, `publishExercise`, `unpublishExercise`; waktu terbit dari `ClientClock`, dan `publishExercise` menolak selama `findUnpublishedInExercise` tidak kosong (depends on T018, T019)
- [X] T021 [US2] Tambahkan rute `POST /eduscreen/soal/{id}/terbit`, `POST /eduscreen/soal/{id}/tarik`, `POST /eduscreen/paket/{id}/terbit`, dan `POST /eduscreen/paket/{id}/tarik` di `MasterContentController` (depends on T020)
- [X] T022 [US2] Ubah `requireMasterQuestion` dan pencarian Exercise master di `src/main/java/com/eduscreen/app/modules/assessment/service/ContentAdoptionService.java` agar hanya menerima konten terbit (depends on T018, T019)
- [X] T023 [US2] Ubah `catalog()` di `src/main/java/com/eduscreen/app/modules/assessment/controller/CatalogController.java` agar memakai varian terbit, bukan `exercises.list(null, ...)` (depends on T019)
- [X] T024 [US2] Batasi pilihan paket onboarding di `src/main/java/com/eduscreen/app/modules/assessment/controller/EduscreenAdminController.java` baris 49 agar hanya memuat paket terbit (depends on T019)
- [X] T044 [P] [US2] Tambahkan di `MasterContentIT`: menghapus Question master menghilangkannya dari ruang kerja dan katalog, sementara salinan yang sudah diadopsi Client tetap utuh dan tetap bisa dipakai (FR-065)
- [X] T025 [P] [US2] Tampilkan penanda terbit/belum-terbit beserta tombol terbit dan tarik di daftar soal master (`src/main/resources/templates/soal/daftar.html`, di balik `basePath`)

**Checkpoint**: Draf tidak pernah bocor ke sekolah, dan adopsi yang sudah terjadi kebal terhadap
perubahan master. Bersama US1 ini sudah MVP yang utuh.

---

## Phase 5: User Story 3 - Merakit Exercise master sebagai paket kurasi (Priority: P2)

**Goal**: Eduscreen Admin menyusun Question master lintas Subject dan Topic menjadi paket bernama
yang bisa dipilih saat onboarding dan diadopsi utuh dari katalog.

**Independent Test**: Rakit satu paket berisi 20 Question dari dua Subject berbeda, terbitkan, lalu
daftarkan Client baru dengan paket itu; Client Admin menemukan 20 Question dan satu Exercise pada
login pertama.

### Tests for User Story 3

- [X] T026 [P] [US3] Tambahkan di `MasterContentIT`: paket memuat Question lintas dua Subject tanpa peringatan; paket kosong ditolak saat diterbitkan; paket master tetap bisa diubah setelah diadopsi banyak Client — `locked_at` tidak pernah terisi (FR-073)

### Implementation for User Story 3

- [X] T027 [US3] Tambahkan rute paket di `MasterContentController`: `GET`/`POST /eduscreen/paket`, `GET /eduscreen/paket/{id}`, `POST /eduscreen/paket/{id}/item`, `DELETE /eduscreen/paket/{id}/item/{questionId}`, `PUT /eduscreen/paket/{id}/urutan` — memanggil `ExerciseService` yang sudah ada dengan `clientId` null
- [X] T028 [US3] Buat `src/main/resources/templates/eduscreen/paket.html` sebagai perakit paket, memuat status terbit dan pesan gerbang FR-069
- [X] T029 [US3] Tambahkan penolakan paket kosong di `MasterPublishingService.publishExercise` (FR-072) (depends on T020)

**Checkpoint**: Paket master bisa dirakit, diterbitkan, dan dipakai onboarding.

---

## Phase 6: User Story 4 - Client Admin menelusuri katalog per Question (Priority: P2)

**Goal**: Katalog menampilkan Question master satu per satu dengan filter dan pencarian, menandai
yang sudah pernah diadopsi, dan mengadopsi beberapa sekaligus.

**Independent Test**: Katalog berisi 50 Question master terbit di tiga Topic; Client Admin menyaring
ke satu Topic, mencentang sepuluh, mengadopsi, lalu menemukan tepat sepuluh Question baru di Question
Bank sekolahnya.

### Tests for User Story 4

- [X] T030 [P] [US4] Buat `src/test/java/com/eduscreen/app/modules/CatalogAdoptionIT.java`: katalog menampilkan Question satu per satu dengan filter Subject dan Topic serta pencarian teks; adopsi sepuluh Question menghasilkan sepuluh salinan milik Client; ringkasan menyebut jumlah Question dan Topic; muat ulang katalog menandai kesepuluhnya sudah diadopsi; tidak ada Subject baru dibuat untuk Client itu (AC-O02)

### Implementation for User Story 4

- [X] T031 [US4] Tambahkan `findAdoptedSourceIds(UUID clientId, Collection<UUID> ids)` di `QuestionRepository`, dibatasi pada pengenal yang tampil di satu halaman katalog (D5)
- [X] T032 [US4] Tambahkan daftar Question terbit berhalaman beserta filter `subjectId`, `topicId`, `q`, dan `page` di `CatalogController`, plus rute fragmen `GET /katalog/soal` untuk HTMX (depends on T018, T023)
- [X] T033 [US4] Sisipkan penanda "sudah diadopsi" pada tiap baris katalog dan peringatan sebelum adopsi berulang di `CatalogController` (depends on T031)
- [X] T034 [US4] Tulis ulang `src/main/resources/templates/katalog/index.html`: daftar Question tercentang berdampingan dengan daftar paket, filter Subject dan Topic, kotak cari, penanda adopsi, dan fragmen `ringkasan` yang sudah ada (depends on T032, T033)

**Checkpoint**: Adopsi granular yang selama ini hanya ada di service akhirnya bisa dijangkau
pengguna.

---

## Phase 7: User Story 5 - Guru meracik Exercise dari hasil adopsi (Priority: P3)

**Goal**: Membuktikan sambungan hulu-ke-hilir utuh: soal hasil adopsi berperilaku persis seperti
soal buatan sekolah.

**Independent Test**: Setelah Client Admin mengadopsi sepuluh Question, Guru menyusun satu Exercise
berisi lima soal adopsi dan tiga soal buatan sekolah tanpa langkah tambahan apa pun.

### Tests for User Story 5

- [X] T035 [P] [US5] Tambahkan di `CatalogAdoptionIT`: Question hasil adopsi muncul di bank soal Client berdampingan dengan soal buatan sendiri dan bisa ditambahkan ke Exercise; Guru ditolak di `/katalog` dan `/eduscreen/**` (FR-081)

### Implementation for User Story 5

- [X] T036 [US5] Jalankan `./mvnw test -Dtest='QuestionBankIT,QuestionImportIT,ClientOnboardingIT'` dan perbaiki regresi yang muncul dari perubahan tanda tangan `QuestionService` di T009; cerita ini tidak menuntut kode fitur baru — kalau ada yang perlu ditulis, itu tanda ada perilaku 001 yang rusak

**Checkpoint**: Seluruh jalur Eduscreen → Client → Guru bekerja ujung ke ujung.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Gerbang mutu yang menyentuh lebih dari satu cerita

- [X] T037 Jalankan `./mvnw test -Dtest=ArchUnitRulesTest` untuk membuktikan tidak ada port, adapter, maupun nama vendor yang menyusup ke kode baru (TC-01, TC-40)
- [X] T038 [P] Verifikasi indeks `question_master_published` dan `question_adopted_source` benar-benar terpakai lewat `explain analyze` pada katalog berisi 5.000 Question master, dan catat hasilnya di `docs/index-verification.md` (SC-015)
- [X] T039 [P] Tambahkan contoh konten master di `src/main/resources/db/seed-local/V901__local_seed_content.sql`: beberapa Question terbit dan setidaknya satu yang masih digarap, agar langkah 2 `quickstart.md` bisa dijalankan tanpa menyiapkan data manual
- [X] T040 Jalankan seluruh validasi manual `quickstart.md` langkah 1 sampai 7, termasuk matriks batas kewenangan
- [X] T041 Jalankan `./mvnw test` penuh dan pastikan hijau, termasuk seluruh tes spesifikasi 001

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: tanpa ketergantungan
- **Foundational (Phase 2)**: setelah Setup — **memblokir seluruh cerita pengguna**
- **US1 (Phase 3)**: setelah Foundational
- **US2 (Phase 4)**: setelah Foundational; tesnya lebih mudah dibaca bila US1 sudah ada, tetapi
  datanya bisa disiapkan langsung lewat repository sehingga tetap bisa dikerjakan paralel
- **US3 (Phase 5)**: setelah Foundational; T029 menunggu T020 dari US2
- **US4 (Phase 6)**: setelah US2 — katalog hanya boleh menampilkan konten terbit, jadi keadaan
  terbit harus ada lebih dulu
- **US5 (Phase 7)**: setelah US4
- **Polish (Phase 8)**: setelah seluruh cerita yang hendak dikirim selesai

### User Story Dependencies

- **US1 (P1)**: mandiri penuh
- **US2 (P1)**: mandiri; satu tugasnya (T017) menguji gerbang paket yang wujud UI-nya baru ada di US3
- **US3 (P2)**: bergantung pada `MasterPublishingService` dari US2
- **US4 (P2)**: bergantung pada varian query terbit dari US2
- **US5 (P3)**: bergantung pada US4; nyaris tanpa kode, murni pembuktian

### Within Each User Story

- Tes ditulis lebih dulu dan harus **gagal** sebelum implementasi
- Entity sebelum repository, repository sebelum service, service sebelum controller, controller
  sebelum templat
- Cerita selesai sebelum berpindah ke prioritas berikutnya

### Parallel Opportunities

- T003 dan T004 paralel (dua entity berbeda)
- T006 dan T007 paralel (dua berkas tes berbeda)
- T014 paralel dengan T012 dan T013 (templat versus controller)
- T018 dan T019 paralel (dua repository berbeda)
- T038 dan T039 paralel (dokumen versus seed)
- US1 dan US2 bisa dikerjakan dua orang sekaligus setelah Phase 2 selesai

---

## Parallel Example: User Story 1

```text
# Setelah Phase 2 selesai, jalankan bersamaan:
T006  MasterContentIT — kasus authoring
T007  ContentIdorTest — batas /eduscreen/**

# Lalu implementasi, dua jalur:
Jalur A: T008 → T009 → T010 → T012 → T013     (service dan controller)
Jalur B: T014 → T015                           (templat)
```

---

## Implementation Strategy

### MVP First (US1 + US2)

Kedua cerita P1 adalah MVP dan tidak bisa dipisah: US1 tanpa US2 berarti setiap penekanan simpan
langsung menayangkan pekerjaan setengah jadi ke seluruh sekolah, dan US2 tanpa US1 tidak punya apa
pun untuk diterbitkan. Setelah keduanya jalan, Eduscreen sudah bisa mengisi bank soal masternya dan
Client sudah bisa mengadopsi paket seperti hari ini — hanya belum per Question.

### Incremental Delivery

1. **Phase 1–2** — skema siap, belum ada perubahan perilaku yang terlihat
2. **+ US1** — Eduscreen Admin bisa menulis konten master; belum terlihat Client
3. **+ US2** — konten master bisa diterbitkan; katalog dan onboarding mulai menyaring yang terbit
4. **+ US3** — paket kurasi bisa dirakit dari ruang kerja, bukan dari basis data
5. **+ US4** — katalog akhirnya bisa ditelusuri per Question; ini yang paling terasa bagi sekolah
6. **+ US5** — pembuktian ujung ke ujung, nyaris tanpa kode

Tiap langkah bisa dirilis sendiri. Tidak ada satu pun yang memaksa langkah berikutnya menyusul di
rilis yang sama.

### Parallel Team Strategy

Dua orang setelah Phase 2: satu mengambil US1 (jalur authoring), satu mengambil US2 (jalur
penerbitan dan repository). Keduanya bertemu di `MasterContentController`, jadi sepakati lebih dulu
siapa yang membuat berkasnya — T012 — dan yang lain menambahkan rute setelahnya.

---

## Gerbang rilis di luar daftar tugas ini

- Migrasi `V5` menambah kolom nullable dan constraint yang selalu terpenuhi pada data yang sudah
  ada, sehingga aman dijalankan pada basis data berisi. Tidak ada langkah pemulihan khusus.
- Fitur ini tidak menyentuh jalur pengerjaan Siswa, penilaian, maupun rekap. Bila salah satunya
  bergeser, penyebabnya hampir pasti T009 dan T010 — periksa keduanya lebih dulu.

---

## Notes

- **Penamaan tes (TC-39, wajib).** `AcceptanceCriteriaCoverageTest` menolak `@DisplayName` yang
  tidak memuat `AC-[A-Z]##`, `TC-##`, atau `BR-[A-Z]##`. `FR-060` **tidak** cocok pola itu dan akan
  membuat suite gagal. Tiap tes baru menyebut aturan yang sudah ada — mis. `TC-41` untuk batas
  lintas-Client, `BR-P04` untuk isolasi, `BR-Q01` untuk bentuk Question, `BR-O02` untuk Subject
  `GLOBAL` yang tidak disalin — dan boleh menambahkan rujukan `FR-*` sesudahnya sebagai keterangan.
- Nol dependensi baru. `pom.xml` tidak boleh berubah oleh fitur ini; kalau berubah, ada rungga
  tangga yang terlewat.
- Nol entitas dan nol tabel baru. Setiap dorongan membuat `content_package`, `question_version`,
  atau `client_adoption_log` sudah ditolak beserta alasannya di `research.md` — baca D2 dan D5
  sebelum membukanya lagi.
- Adopsi tetap salinan sekali jalan. Menambahkan sinkronisasi master ke Client membatalkan
  `docs/adr/0001` dan menuntut ADR baru, bukan keputusan implementasi.

---

## Catatan pelaksanaan

Ditambahkan saat implementasi, bukan saat perencanaan:

- **T042, T043** — lubang gambar pada konten master, ditemukan `/speckit-analyze` sebagai CRITICAL.
  `SecurityConfig` menutup `POST /gambar` dari Eduscreen Admin dan `ImageController` memanggil
  `requireClientId()` yang melempar untuknya. `ImageService` sendiri sudah siap sejak awal.
- **T044** — tes FR-065 yang semula tidak punya tugas.
- **Catatan TC-39 di bagian Notes** — `@DisplayName` yang menyebut `FR-*` tidak cocok pola
  `AcceptanceCriteriaCoverageTest` dan akan menggagalkan suite.

Empat cacat yang tersingkap selama implementasi dan ikut diperbaiki:

1. **`QuestionService.update` merusak setiap pembaruan soal pilihan ganda.** Option lama dihapus
   lalu yang baru disisipkan dalam satu flush; Hibernate mengurutkan seluruh INSERT sebelum
   seluruh DELETE, sehingga indeks parsial `question_option_single_correct` sempat melihat dua
   Option benar dan menolak. Bug lama — nol tes menyentuh `update` sebelum ini. Diperbaiki dengan
   `options.flush()` di antara keduanya.
2. **Ekspresi `${...}` bersarang di templat** tidak sah di SpEL.
3. **`th:each` bersama `th:replace` di elemen yang sama** memanggil fragmen sebelum variabel
   iterasinya terikat (presedensi 100 vs 200). Dibungkus `th:block`.
4. **`@{...}` memperlakukan isinya sebagai teks URL, bukan SpEL**, sehingga seluruh tombol
   merender `hx-post="basePath/..."` secara harfiah — dan tetap membalas `200`. Basis jalur
   dinamis dipindah ke preprocessing `__${...}__`. Ini yang paling berbahaya: tiga tes lolos
   sementara setiap tombol di layar mati. `MasterContentRenderTest` kini memeriksa jalurnya, bukan
   hanya status.

Satu temuan operasional di luar cakupan spesifikasi, diperbaiki karena memblokir T040:

- Seed lokal menempati band `V900`+, sehingga **setiap** migrasi produksi baru mendarat
  out-of-order dan membuat database pengembangan yang sudah di-seed menolak start. Profil `local`
  kini menyalakan `spring.flyway.out-of-order`. Environment lain tidak memuat seed, jadi
  urutannya di sana tetap lurus.
