# Implementation Plan: Portal Latihan Siswa Eduscreen (v1)

**Branch**: `001-student-exercise-portal` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-student-exercise-portal/spec.md`

## Summary

Membangun portal latihan multi-tenant tempat Guru meracik Exercise dari bank soal Client,
menerbitkannya sebagai Assignment ke Ruangan, dan Siswa mengerjakannya dengan urutan soal yang
diacak khusus untuk dirinya, tersimpan otomatis, dan dinilai tanpa campur tangan manusia untuk
soal pilihan ganda.

Pendekatan teknis sudah terkunci sebelum perencanaan ini, melalui enam ronde penggalian yang
hasilnya tercatat sebagai 49 aturan `TC-*` di `CONSTITUTION.md` dan 16 keputusan di `docs/adr/`.
Karena itu tidak ada satu pun `NEEDS CLARIFICATION` di bawah: aplikasi monolit Spring Boot
dirender server, dengan pemisahan hexagonal hanya di batas eksternal (identity, notifikasi,
penyimpanan berkas) dan berlapis lurus di inti asesmen.

Tiga keputusan yang paling membentuk implementasi:

1. **Sesi pengerjaan lahir malas dan membeku.** Tidak ada pembuatan massal; urutan soal dibekukan
   saat Siswa menekan Mulai dan tidak pernah berubah (ADR-0002).
2. **Tidak ada penjadwal.** Sesi kedaluwarsa difinalisasi saat diakses, dijaga kunci pesimistis
   dan unique constraint (ADR-0002, TC-18, TC-19).
3. **Isolasi tenant ditulis eksplisit.** `client_id` masuk ke tanda tangan setiap method
   repository, bukan disembunyikan di filter otomatis (ADR-0012, TC-36).

## Technical Context

**Language/Version**: Java 25 LTS

**Primary Dependencies**: Spring Boot 3.5+ (Web, Security, Data JPA, Validation, Mail),
Thymeleaf, HTMX, Alpine.js, Tailwind CSS (CLI standalone via `frontend-maven-plugin`), Flyway,
OWASP Java HTML Sanitizer, KaTeX, Apache POI (impor Excel/CSV), ArchUnit

**Storage**: PostgreSQL 16+; berkas gambar di filesystem lokal di balik `FileStoragePort`

**Testing**: JUnit 5 + Testcontainers (PostgreSQL sungguhan; H2 dilarang oleh TC-38), MockMvc
untuk lapisan controller, ArchUnit untuk batas arsitektur

**Target Platform**: Server Linux, satu instance (TC-42); klien adalah peramban desktop dan
seluler

**Project Type**: Aplikasi web monolit yang dirender server (bukan SPA, TC-13)

**Performance Goals**:
- Simpan satu jawaban di bawah 300 ms pada persentil 95
- Buat sesi pengerjaan (Exercise 50 soal) di bawah 2 detik
- Rekap satu Ruangan berisi 40 Siswa di bawah 3 detik termasuk finalisasi (SC-007)

**Constraints**:
- Pengerjaan membutuhkan koneksi; tidak ada mode luring (ADR-0006)
- Seluruh waktu disimpan UTC, ditampilkan dalam zona waktu Client (TC-01 spec, BR-T01/T02)
- Adapter identity dummy tidak boleh menyentuh data siswa sungguhan (TC-34)
- Impor sinkron maksimum 500 baris per berkas (TC-45)

**Scale/Scope**: 2.000 sesi aktif serentak per Client, ~10.000 platform-wide (diperlakukan
sebagai hipotesis yang wajib dibuktikan uji beban, TC-42); 7 cerita pengguna, 59 kebutuhan
fungsional, 11 entitas kunci

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Diperiksa terhadap `.specify/memory/constitution.md` v1.0.0.

| Prinsip | Status | Bagaimana desain ini memenuhinya |
| --- | --- | --- |
| I. Isolasi Tenant & Anti-IDOR (NON-NEGOTIABLE) | PASS | UUID v7 sebagai primary key; `client_id` dan `student_id` masuk klausa query di setiap method repository; `404` seragam untuk milik orang lain dan tidak ada; gambar hanya lewat endpoint berotorisasi; tes lintas-Siswa dan lintas-Client wajib per endpoint |
| II. Server Memegang Otoritas Waktu & State (NON-NEGOTIABLE) | PASS | `effectiveDeadline` dibekukan saat sesi lahir; finalisasi memakai kunci pesimistis lalu memeriksa status; unique constraint pada `result.session_id`; auto-save berupa upsert idempoten; hitung mundur di klien murni tampilan |
| III. Arsitektur Ditentukan Kepemilikan Batas | PASS | `modules/identity` dan `modules/notification` dan `modules/storage` hexagonal; `modules/assessment` berlapis lurus tanpa port; ArchUnit menegakkan larangan impor |
| IV. Kredensial di Balik Port (NON-NEGOTIABLE) | PASS | Seluruh autentikasi lewat `IdentityProviderPort`; adapter dummy dikurung `@Profile({"local","demo"})` plus pemeriksaan `EDUSCREEN_ENV`; environment `demo` berspanduk, tanpa email keluar, tanpa pemulihan dari cadangan produksi |
| V. Konten Tidak Tepercaya Dibersihkan di Pintu Masuk | PASS | Sanitasi allowlist di satu `ContentSanitizer` yang dipanggil semua jalur tulis termasuk impor; kolom teks polos turunan untuk pencarian; unggahan divalidasi magic bytes dan di-encode ulang |
| VI. Kesederhanaan yang Dijaga | PASS | Tanpa SPA, tanpa penjadwal, tanpa antrean pekerjaan, tanpa Redis, tanpa penyimpanan objek; impor sinkron berbatas; satu instance |
| VII. Aturan Ditegakkan Mesin, Bukan Niat Baik | PASS | Testcontainers wajib; nama tes merujuk `AC-*`; satu kelas ArchUnit; invariant dijaga constraint database |

**Hasil gate (pra-Phase 0): LULUS.** Tidak ada pelanggaran yang perlu dibenarkan, sehingga bagian
Complexity Tracking dihapus dari rencana ini.

### Re-check pasca-Phase 1

Diperiksa ulang setelah `data-model.md`, `contracts/`, dan `quickstart.md` tersusun.

| Prinsip | Status | Bukti di artefak desain |
| --- | --- | --- |
| I | PASS | `client_id NOT NULL` di setiap tabel milik Client; `exam-session.md` mewajibkan `404` identik dan kepemilikan di klausa query; `content-authoring.md` mewajibkan gambar lewat endpoint berotorisasi |
| II | PASS | `data-model.md` membekukan `effective_deadline` saat sesi lahir dan memuat algoritma finalisasi berkunci; `result.session_id` unik; `exam-session.md` mewajibkan upsert idempoten dan penolakan `410` |
| III | PASS | Struktur paket memisahkan tiga modul hexagonal dari satu modul berlapis; `FileStoragePort` dan `NotificationPort` muncul sebagai port, bukan panggilan langsung |
| IV | PASS | `quickstart.md` mendokumentasikan penolakan start di luar `local`/`demo`; `auth-and-accounts.md` menaruh seluruh pemeriksaan kredensial di balik port |
| V | PASS | `data-model.md` memasangkan `*_html` tersanitasi dengan `*_text` turunan; `content-authoring.md` mewajibkan sanitasi saat tulis termasuk jalur impor, dan validasi unggahan lewat magic bytes |
| VI | PASS | Tidak ada tabel pekerjaan, tidak ada endpoint JSON untuk klien lain, tidak ada `JSONB`; impor tetap sinkron berbatas |
| VII | PASS | `quickstart.md` memuat perintah yang menjalankan ArchUnit, tes IDOR, dan pemeriksaan `ddl-auto`; unique constraint menjadi jaring invariant |

**Hasil gate (pasca-Phase 1): LULUS.** Desain tidak memperkenalkan pelanggaran baru.

Catatan gerbang rilis (bukan pelanggaran): TC-34 melarang adapter dummy menyentuh data siswa
sungguhan. Client pertama yang membawa data nyata menuntut Keycloak terpasang atau adapter lokal
berbasis BCrypt beserta kebijakan passwordnya. Ini pekerjaan di luar lingkup fitur ini dan
dicatat sebagai prasyarat rilis di `docs/adr/0016`.

## Project Structure

### Documentation (this feature)

```text
specs/001-student-exercise-portal/
├── plan.md              # This file (/speckit-plan command output)
├── business-rules.md    # BR-* rules and AC-* acceptance criteria
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── README.md
│   ├── auth-and-accounts.md
│   ├── content-authoring.md
│   ├── assignment-publishing.md
│   ├── exam-session.md
│   └── grading-and-reports.md
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify output)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/com/eduscreen/app/
├── EduscreenApplication.java
├── config/                       # Spring Security, JPA, Flyway, MVC, sanitizer beans
├── shared/
│   ├── domain/                   # UUID v7 generator, ClientId, jenis waktu bersama
│   ├── web/                      # GlobalExceptionAdvice (TC-31), HX-Redirect (TC-30)
│   └── security/                 # UserPrincipal, rate limiter login (TC-33)
├── modules/
│   ├── identity/                 # HEXAGONAL — IAM & Auth
│   │   ├── port/in/              # LoginUseCase, ManageUserUseCase
│   │   ├── port/out/             # IdentityProviderPort
│   │   ├── adapter/out/          # DummyIdentityProviderAdapter (kelak KeycloakAdapter)
│   │   ├── service/
│   │   └── controller/
│   ├── notification/             # HEXAGONAL — email transaksional
│   │   ├── port/out/             # NotificationPort
│   │   └── adapter/out/          # SmtpNotificationAdapter, NoOpNotificationAdapter (demo)
│   ├── storage/                  # HEXAGONAL — berkas gambar
│   │   ├── port/out/             # FileStoragePort
│   │   └── adapter/out/          # LocalFileStorageAdapter
│   └── assessment/               # LAYERED — Domain Core & DB
│       ├── controller/           # Thymeleaf + fragmen HTMX
│       ├── service/              # Exam engine, penilaian, laporan, impor
│       ├── repository/           # Entity JPA + akses data
│       └── dto/
└── ...

src/main/resources/
├── application.yml               # profil: local, demo
├── db/migration/                 # V1__*.sql .. (Flyway, TC-17)
├── templates/                    # Thymeleaf: halaman + fragmen
└── static/                       # keluaran Tailwind, HTMX, Alpine, KaTeX

src/test/java/com/eduscreen/app/
├── architecture/                 # ArchUnitRulesTest (TC-40)
├── modules/                      # tes service & repository (Testcontainers)
└── web/                          # tes controller (MockMvc), termasuk tes IDOR (TC-41)
```

**Structure Decision**: satu proyek Maven monolit dengan pemisahan modul berbasis paket, bukan
multi-module Maven. Batas antar modul ditegakkan tes ArchUnit (TC-40), bukan oleh compiler —
pilihan sadar yang menukar penegakan terkuat dengan build yang jauh lebih sederhana selama
iterasi awal, sesuai Prinsip VI. Empat modul mencerminkan Prinsip III secara langsung: tiga modul
hexagonal untuk batas yang tidak kami kendalikan (`identity`, `notification`, `storage`) dan satu
modul berlapis untuk inti yang kami kendalikan penuh (`assessment`).
