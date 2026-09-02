# Implementation Plan: Question Bank Master Eduscreen (v1)

**Branch**: `002-master-question-bank` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-master-question-bank/spec.md`

## Summary

Menutup hulu konten master: memberi Eduscreen Admin tempat menulis Topic dan Question miliknya,
merakitnya menjadi Exercise master, dan menerbitkannya — lalu menyambungkan katalog Client agar
penelusurannya berlangsung per Question, bukan hanya per paket.

Ini bukan modul baru. Sebagian besar mesinnya sudah berdiri sejak spesifikasi 001 dan hanya
terkunci dari Eduscreen Admin oleh satu baris: `QuestionService.create/update` memanggil
`author.requireClientId()`, yang melempar `IllegalStateException` untuk pengguna tanpa Client.
Karena itu rencana ini didominasi pemakaian ulang, bukan penulisan baru.

Tiga keputusan yang paling membentuk implementasi:

1. **Kepemilikan konten menjadi parameter, bukan asumsi.** `QuestionService.create/update`
   berhenti menyimpulkan pemilik dari principal dan mulai menerimanya sebagai `UUID clientId`,
   yang bernilai `null` untuk konten master. Pola ini sudah dipakai seluruh method lain di
   `QuestionService`, `ExerciseService`, dan `ContentAdoptionService` — perubahan ini menyeragamkan
   dua method yang tertinggal, bukan memperkenalkan gaya baru.
2. **Keadaan terbit adalah satu kolom nullable, bukan mesin status.** `published_at` pada `question`
   dan `exercise`, dengan check constraint yang menolaknya terisi pada baris milik Client. Tidak ada
   enum, tidak ada tabel status, tidak ada tabel versi.
3. **Tidak ada entitas baru sama sekali.** "Paket" adalah `exercise` ber-`client_id` null yang sudah
   dipakai `paketIds[]` di onboarding. Penanda "sudah pernah diadopsi" dibaca dari
   `source_question_id` yang sudah ditulis `ContentAdoptionService` sejak awal.

## Technical Context

**Language/Version**: Java 25 LTS

**Primary Dependencies**: tidak ada dependensi baru. Spring Boot 3.5+ (Web, Security, Data JPA),
Thymeleaf, HTMX, Alpine.js, Tailwind CSS, Flyway, OWASP Java HTML Sanitizer, KaTeX, ArchUnit —
seluruhnya sudah terpasang untuk spesifikasi 001

**Storage**: PostgreSQL 16+; satu migrasi Flyway baru (`V5`) yang menambah dua kolom dan dua check
constraint. Tidak ada tabel baru

**Testing**: JUnit 5 + Testcontainers (PostgreSQL sungguhan, TC-38), MockMvc untuk lapisan
controller, ArchUnit untuk batas arsitektur

**Target Platform**: Server Linux satu instance (TC-42); peramban desktop dan seluler

**Project Type**: Aplikasi web monolit yang dirender server (TC-13)

**Performance Goals**:
- Pencarian katalog atas 5.000 Question master di bawah 1 detik pada persentil 95 (SC-015)
- Adopsi 10 Question sekaligus di bawah 2 detik termasuk penyalinan Topic
- Halaman ruang kerja master (25 Question per halaman) di bawah 500 ms

**Constraints**:
- Adopsi tetap salinan penuh sekali jalan; tidak ada sinkronisasi master ke Client (ADR-0001)
- Subject `GLOBAL` tidak pernah disalin (BR-O02)
- Bank soal adalah segmen **berlapis**; dilarang membuat port atau adapter (TC-01, ditegakkan TC-40)
- Filter `client_id` ditulis eksplisit di tiap method repository (TC-36)
- Konten master melewati `ContentSanitizer` yang sama dengan konten Client (TC-22)

**Scale/Scope**: 5.000 Question master dan ~200 Exercise master platform-wide; 5 cerita pengguna,
23 kebutuhan fungsional (`FR-060`..`FR-082`), 5 entitas kunci yang seluruhnya memakai tabel yang
sudah ada

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Diperiksa terhadap `.specify/memory/constitution.md` v1.1.0.

| Prinsip | Status | Bagaimana desain ini memenuhinya |
| --- | --- | --- |
| I. Isolasi Tenant & Anti-IDOR (NON-NEGOTIABLE) | PASS | Katalog tetap satu-satunya pembacaan sah yang menembus batas, dan ia hanya membaca baris ber-`client_id` null yang sudah terbit. Ruang kerja master tidak punya satu pun query yang menerima `client_id` bukan-null. Setiap rute `/eduscreen/**` baru mendapat tes lintas-peran dan lintas-Client (TC-41) |
| II. Server Memegang Otoritas Waktu & State (NON-NEGOTIABLE) | PASS | `published_at` diisi jam server lewat `ClientClock`; gerbang penerbitan Exercise master dievaluasi di server dan tidak pernah dari masukan klien |
| III. Arsitektur Ditentukan Kepemilikan Batas | PASS | Seluruh kode baru tinggal di `modules/assessment/{controller,service,repository}` berlapis lurus. Tidak ada port, tidak ada adapter, tidak ada interface beranggota tunggal |
| IV. Kredensial di Balik Port (NON-NEGOTIABLE) | PASS | Tidak menyentuh jalur autentikasi. Otorisasi rute memakai aturan `SecurityConfig` yang sudah ada (`/eduscreen/**` → `hasRole("EDUSCREEN_ADMIN")`) |
| V. Konten Tidak Tepercaya Dibersihkan di Pintu Masuk | PASS | Konten master melewati `ContentSanitizer` yang sama; kolom `body_text` turunan tetap yang dicari (TC-25); gambar tetap lewat `ImageService` dan endpoint berotorisasi |
| VI. Kesederhanaan yang Dijaga | PASS | Nol entitas baru, nol tabel baru, nol dependensi baru, nol enum baru. Satu kolom nullable per tabel, dua template dipakai ulang untuk dua tempat pasang |
| VII. Aturan Ditegakkan Mesin, Bukan Niat Baik | PASS | Invariant "hanya konten master boleh terbit" dijaga check constraint database, bukan validasi layanan. Testcontainers wajib; ArchUnit yang sudah ada tetap menjaga batas modul |

**Hasil gate (pra-Phase 0): LULUS.** Tidak ada pelanggaran yang perlu dibenarkan, sehingga bagian
Complexity Tracking dihapus dari rencana ini.

**Hasil gate (pasca-Phase 1): LULUS.** Desain Phase 1 tidak menambah entitas, tabel, port, maupun
dependensi apa pun di luar yang sudah dihitung di atas. Rincian di [data-model.md](./data-model.md).

## Project Structure

### Documentation (this feature)

```text
specs/002-master-question-bank/
├── plan.md                       # Berkas ini
├── spec.md                       # Spesifikasi (FR-060..FR-082, SC-011..SC-018)
├── research.md                   # Phase 0: enam keputusan desain beserta alternatif yang ditolak
├── data-model.md                 # Phase 1: perubahan skema dan invariannya
├── quickstart.md                 # Phase 1: panduan validasi ujung-ke-ujung
├── contracts/
│   ├── README.md                 # Indeks kontrak
│   ├── master-authoring.md       # Ruang kerja Eduscreen Admin — US1, US2, US3
│   └── catalog-adoption.md       # Katalog granular dan adopsi — US4, US5
├── checklists/
│   └── requirements.md           # Checklist kualitas spesifikasi (16/16)
└── tasks.md                      # Phase 2 (/speckit-tasks — BUKAN keluaran perintah ini)
```

### Source Code (repository root)

```text
src/main/java/com/eduscreen/app/modules/assessment/
├── controller/
│   ├── MasterContentController.java      # BARU — /eduscreen/topic, /eduscreen/soal, /eduscreen/paket
│   ├── CatalogController.java            # DIUBAH — daftar Question terbit, filter, penanda adopsi
│   ├── EduscreenAdminController.java     # DIUBAH — tautan ke ruang kerja master
│   └── QuestionBankController.java       # DIUBAH — menyesuaikan tanda tangan QuestionService
├── service/
│   ├── MasterPublishingService.java      # BARU — terbit, tarik, gerbang FR-069
│   ├── QuestionService.java              # DIUBAH — pemilik jadi parameter, bukan asumsi
│   ├── TaxonomyService.java              # DIUBAH — createGlobalTopic, requireWritableTopic
│   └── ContentAdoptionService.java       # DIUBAH — hanya konten terbit yang bisa diadopsi
└── repository/
    ├── QuestionEntity.java               # DIUBAH — publishedAt + publish/unpublish
    ├── ExerciseEntity.java               # DIUBAH — publishedAt + publish/unpublish
    ├── QuestionRepository.java           # DIUBAH — varian terbit, penanda adopsi, gerbang paket
    └── ExerciseRepository.java           # DIUBAH — varian terbit

src/main/resources/
├── db/migration/V5__master_publishing.sql   # BARU — dua kolom, dua constraint, dua indeks
└── templates/
    ├── soal/daftar.html                     # DIUBAH — dipakai ulang lewat basePath
    ├── soal/editor.html                     # DIUBAH — dipakai ulang lewat basePath
    ├── eduscreen/paket.html                 # BARU — perakit Exercise master
    ├── eduscreen/client.html                # DIUBAH — tautan ruang kerja master
    └── katalog/index.html                   # DIUBAH — daftar Question, filter, penanda

src/test/java/com/eduscreen/app/
├── modules/MasterContentIT.java          # BARU — US1, US2, US3
├── modules/CatalogAdoptionIT.java        # BARU — US4, US5
└── web/ContentIdorTest.java              # DIUBAH — batas /eduscreen/** dua arah (TC-41)
```

**Structure Decision**: Fitur ini seluruhnya tinggal di modul `assessment` yang sudah ada, mengikuti
alur berlapis `controller → service → repository` yang diwajibkan Pasal 1 CONSTITUTION untuk inti
bisnis. Tidak ada modul, paket, maupun lapisan baru. Satu-satunya berkas benar-benar baru adalah
satu controller, satu service, satu migrasi, satu template, dan dua kelas tes integrasi.

## Phase 0 — Riset

Nol `NEEDS CLARIFICATION`. Seluruh pertanyaan terbuka sudah dijawab sebelum perencanaan: empat oleh
klarifikasi lingkup saat penyusunan spesifikasi, sisanya oleh aturan yang sudah mengikat di
`CONSTITUTION.md` dan `docs/adr/`. Keluaran Phase 0 karena itu berupa catatan keputusan desain
beserta alternatif yang ditolak, bukan riset teknologi.

Rincian: [research.md](./research.md)

## Phase 1 — Desain & Kontrak

- Perubahan skema dan invariannya: [data-model.md](./data-model.md)
- Kontrak rute, peran, dan aturan mengikat: [contracts/](./contracts/README.md)
- Panduan validasi ujung-ke-ujung: [quickstart.md](./quickstart.md)
