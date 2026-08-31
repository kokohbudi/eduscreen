<!--
SYNC IMPACT REPORT — v1.1.0 (2026-08-31)
========================================
Version change: 1.0.0 → 1.1.0
Bump rationale: MINOR. Mengubah batasan teknologi yang mengikat (runtime Java),
                bukan sekadar penajaman kata (bukan PATCH), dan tanpa menghapus
                prinsip atau melonggarkan aturan NON-NEGOTIABLE (bukan MAJOR).

Amandemen:
  - Batasan Teknologi & Data → Runtime: Java 21 LTS → Java 25 LTS
  - Batasan Teknologi & Data → Framework: Spring Boot 3.3+ → Spring Boot 3.5+
    (3.5.16 mendukung JDK 17-25; lini 3.3 tidak mencapai JDK 25)
  Alasan lengkap: docs/adr/0017-java-25-bukan-21.md
  Diselaraskan pada: CONSTITUTION.md Pasal 4, specs/001-student-exercise-portal/
  {plan.md, quickstart.md, tasks.md}

Prinsip: tidak ada yang berubah pada v1.1.0.

--- Riwayat: RATIFIKASI AWAL v1.0.0 (2026-08-31) ---
Version change: (template belum terisi) → 1.0.0
Bump rationale: Ratifikasi awal. Seluruh placeholder template diganti nilai konkret
                yang diturunkan dari CONSTITUTION.md (TC-01..TC-49),
                .scratch/eduscreen/spec.md (BR-*, AC-*), CONTEXT.md, dan docs/adr/0001-0016.

Modified principles:
  [PRINCIPLE_1_NAME] → I. Isolasi Tenant & Anti-IDOR (NON-NEGOTIABLE)
  [PRINCIPLE_2_NAME] → II. Server Memegang Otoritas Waktu & State (NON-NEGOTIABLE)
  [PRINCIPLE_3_NAME] → III. Arsitektur Ditentukan Kepemilikan Batas
  [PRINCIPLE_4_NAME] → IV. Kredensial di Balik Port (NON-NEGOTIABLE)
  [PRINCIPLE_5_NAME] → V. Konten Tidak Tepercaya Dibersihkan di Pintu Masuk
  (baru)             → VI. Kesederhanaan yang Dijaga
  (baru)             → VII. Aturan Ditegakkan Mesin, Bukan Niat Baik

Added sections:
  - Core Principles (7 prinsip; template menyediakan 5, ditambah 2)
  - Batasan Teknologi & Data (SECTION_2)
  - Alur Kerja & Gerbang Mutu (SECTION_3)
  - Governance

Removed sections: tidak ada

Template consistency:
  ✅ .specify/templates/plan-template.md — "Constitution Check" gate terpetakan ke
     tujuh prinsip di bawah; tidak butuh perubahan struktural.
  ✅ .specify/templates/spec-template.md — tidak merujuk konstitusi.
  ✅ .specify/templates/tasks-template.md — tidak merujuk konstitusi.
  ✅ .specify/templates/checklist-template.md — tidak merujuk konstitusi.

Deferred TODOs: tidak ada.
-->

# Eduscreen Constitution

Eduscreen adalah platform SaaS multi-tenant untuk distribusi dan pengerjaan soal latihan di
sekolah dan lembaga bimbingan belajar. Konstitusi ini mengikat setiap perubahan kode.

Dokumen ini adalah lapisan **prinsip**. Aturan terperinci beserta contoh kode, penomoran
`TC-01`..`TC-49`, dan indeksnya tinggal di `CONSTITUTION.md` di root repo; alasan tiap keputusan
tercatat di `docs/adr/`. Setiap prinsip di bawah menyebut kode `TC-*` yang mewujudkannya.

## Core Principles

### I. Isolasi Tenant & Anti-IDOR (NON-NEGOTIABLE)

Data satu Client tidak boleh pernah terbaca Client lain, dan data satu Siswa tidak boleh pernah
terbaca Siswa lain. Perlindungannya berlapis empat dan setiap lapis wajib ada; tidak ada satu pun
yang boleh dianggap cukup sendirian.

- Primary key entitas yang muncul di URL MUST memakai UUID v7, bukan bilangan berurut (`TC-08`).
- Kepemilikan dan `client_id` MUST masuk ke **klausa query**, bukan diperiksa setelah entitas
  termuat. Data Client lain tidak boleh pernah sampai ke memori proses (`TC-08`, `TC-10`).
- Objek milik orang lain dan objek yang tidak ada MUST menghasilkan respons identik `404` —
  tanpa perbedaan pesan, kode, maupun waktu tanggap (`TC-09`).
- Penyaringan `client_id` MUST ditulis eksplisit di tanda tangan method repository. Filter
  otomatis Hibernate dan Row-Level Security TIDAK dipakai untuk ini (`TC-36`).
- Berkas gambar MUST dilayani lewat endpoint yang memeriksa izin. Penyajian statis langsung
  dari direktori penyimpanan dilarang (`TC-26`).
- Setiap endpoint yang menyentuh Session, SessionAnswer, Result, atau berkas MUST disertai tes
  yang membuktikan permintaan lintas-Siswa dan lintas-Client mendapat `404` (`TC-11`, `TC-41`).

**Rasional**: membalas `403` untuk objek milik orang lain mengubah tembok menjadi oracle yang
bisa ditanyai penyerang. Penyaringan eksplisit dipilih di atas filter otomatis karena kebocoran
lintas-Client bisa berjalan berbulan-bulan tanpa terdeteksi, dan risiko sebesar itu layak
terlihat di kode dan di tinjauan (ADR-0009, ADR-0012).

Pengecualian tunggal: akses dukungan Eduscreen yang **baca-saja**, dinyalakan Client Admin,
berbatas waktu, dan teraudit (`TC-46`, BR-P05, ADR-0015).

### II. Server Memegang Otoritas Waktu & State (NON-NEGOTIABLE)

Dalam sistem ujian, waktu dan status sesi adalah kebenaran yang tidak boleh diperdebatkan klien.

- Sisa waktu MUST dihitung server dari `startedAt`. Jam perangkat Siswa tidak pernah menjadi
  rujukan; hitung mundur di layar murni tampilan (`TC-12`, `TC-15`, BR-T03).
- `effectiveDeadline` MUST dibekukan saat Session lahir sebagai
  `min(startedAt + timer, expiresAt)`. Global Expiration selalu memangkas Timer (BR-T04).
- Server MUST menolak setiap perubahan bila Session tidak lagi `IN_PROGRESS` atau batas
  waktunya sudah lewat — tanpa memandang muatan yang dikirim klien (BR-T08).
- Finalisasi Session MUST mengambil kunci pesimistis pada baris Session sebelum memeriksa
  status, dan `result.session_id` MUST punya unique constraint (`TC-18`, `TC-19`).
- Auto-save MUST idempoten: upsert berkunci `sessionQuestionId`, kiriman ulang berisi jawaban
  identik adalah no-op yang sukses (`TC-20`).

**Rasional**: antrean coba-ulang di klien menjamin server menerima kiriman ganda; server yang
menolak kiriman ulang mengubah mekanisme pemulihan menjadi sumber kerusakan. Finalisasi terjadi
saat Session diakses tanpa scheduler, sehingga dua pembaca bisa berlomba (ADR-0002).

### III. Arsitektur Ditentukan Kepemilikan Batas

Dua gaya arsitektur hidup berdampingan dengan sengaja. Pembaginya adalah pertanyaan tunggal:
*apakah yang di seberang batas ini bisa berubah tanpa izin kami?*

- Hexagonal (`port.out` + `adapter.out`) MUST dipakai untuk ketergantungan pihak ketiga:
  Identity, Notification, File Storage.
- Layered (`controller` → `service` → `repository`) MUST dipakai untuk inti bisnis: bank soal,
  perakitan Exercise, penerbitan Assignment, exam engine, persistensi.
- Kode di `assessment` MUST NOT membuat port dan adapter untuk hal yang tidak melintasi batas
  sistem (`TC-01`).
- Layer `service` MUST NOT menyebut nama vendor mana pun (`TC-02`).
- Modul `assessment` MUST NOT mengimpor kelas dari `identity.adapter`; ia hanya menyentuh
  `identity.port.in` (`TC-03`).

**Rasional**: hexagonal di mana-mana membanjiri inti dengan interface beranggota tunggal;
layered di mana-mana menanam nama vendor ke dalam logika bisnis (ADR-0007).

### IV. Kredensial di Balik Port (NON-NEGOTIABLE)

Seluruh autentikasi dan manajemen kredensial berada di balik `IdentityProviderPort`, sehingga
migrasi ke Keycloak tidak menyentuh satu baris pun di layer `service`.

- Adapter identity berkredensial statis MUST beranotasi `@Profile({"local", "demo"})` **dan**
  memuat pemeriksaan gagal-cepat yang menolak start bila `EDUSCREEN_ENV` bukan `local` atau
  `demo` — termasuk bila variabelnya tidak diset (`TC-04`).
- Adapter dummy MUST NOT hidup di environment mana pun yang memuat nama, email, jawaban, atau
  nilai siswa nyata. Batasnya adalah **data**, bukan label environment (`TC-34`).
- Aplikasi MUST gagal start bila `IdentityProviderPort` yang aktif tidak tepat satu (`TC-05`).
- Password mentah MUST NOT masuk log, pesan galat, maupun tabel aplikasi. Adapter lokal apa pun
  yang dibangun sebelum Keycloak MUST menyimpan dengan BCrypt (`TC-06`).
- Environment `demo` MUST menampilkan spanduk permanen, MUST NOT dipulihkan dari cadangan
  produksi, dan MUST menonaktifkan pengiriman email transaksional (`TC-47`, `TC-48`, `TC-49`).

**Rasional**: adapter dummy menerima satu password untuk semua akun — siapa pun yang
mengetahuinya masuk sebagai Siswa, Guru, atau Client Admin mana pun. Jalur kebocoran yang paling
mungkin adalah niat baik: menyalin data Client nyata ke demo agar peragaan terasa meyakinkan
(ADR-0008, ADR-0016).

**Gerbang keras**: Client pertama yang membawa data siswa nyata menuntut Keycloak terpasang,
atau adapter lokal berbasis BCrypt beserta kebijakan passwordnya, sebelum akun sungguhan
pertama dibuat.

### V. Konten Tidak Tepercaya Dibersihkan di Pintu Masuk

`Question.body` dan `Option.body` ditulis Guru lalu ditayangkan ke seluruh Ruangan — termasuk ke
halaman tempat ujian sedang berlangsung. Ini jalur data dengan kepercayaan paling rendah di
sistem.

- Konten kaya MUST disanitasi dengan allowlist **saat menulis**; database hanya berisi HTML yang
  sudah bersih (`TC-22`). Impor CSV melewati jalur sanitasi yang sama.
- `<script>`, `<style>`, `<iframe>`, `<object>`, atribut `on*`, dan URL berskema `javascript:`
  MUST ditolak tanpa pengecualian (`TC-23`).
- Rumus matematika MUST disimpan sebagai LaTeX berdelimiter dan dirender di klien, tidak pernah
  sebagai HTML hasil render (`TC-24`).
- Unggahan gambar MUST divalidasi dengan batas ukuran, tipe dari **magic bytes**, dan encode
  ulang saat disimpan (`TC-27`).
- Pencarian MUST menyentuh kolom teks polos turunan, bukan kolom HTML (`TC-25`).
- Password, isi jawaban Siswa, isi soal, dan alamat email MUST NOT masuk log dalam bentuk apa
  pun (`TC-44`).

**Rasional**: sanitasi saat render menyebar tanggung jawab keamanan ke setiap template, dan satu
template yang lupa memanggilnya membuka lubang tanpa galat yang terlihat. Konsekuensi yang
diterima: markup asli hilang permanen, sehingga allowlist harus cukup lebar sejak awal
(ADR-0011).

### VI. Kesederhanaan yang Dijaga

Beberapa hal sengaja tidak dibangun. Larangan ini bukan kelalaian, dan mencabutnya menuntut ADR
baru — bukan sekadar pull request yang "merapikan".

- MUST NOT ada SPA. Halaman dirender server; HTMX menukar fragment; auto-save mengembalikan
  fragment, bukan JSON (`TC-13`, `TC-14`).
- MUST NOT ada scheduler atau antrean pekerjaan latar. Finalisasi terjadi saat diakses;
  impor berjalan sinkron dengan batas 500 baris per berkas (`TC-45`, ADR-0002, ADR-0014).
- MUST NOT memakai `JSONB` untuk data yang bentuknya tetap (`TC-16`).
- Topologi v1 adalah satu instance. Pemicu pindah ke mendatar adalah kebutuhan deploy tanpa
  memutus ujian, bukan angka beban (`TC-42`, ADR-0013).

**Rasional**: setiap infrastruktur yang ditambahkan membawa mode kegagalan diam-diam yang harus
dijaga selamanya. Angka 10.000 Session serentak diperlakukan sebagai hipotesis yang wajib
dibuktikan uji beban, bukan sebagai fakta yang sudah memutuskan arsitektur.

### VII. Aturan Ditegakkan Mesin, Bukan Niat Baik

Aturan yang tidak dijalankan apa pun akan luntur dalam hitungan bulan. Prinsip I sampai VI harus
bisa gagal di CI.

- Tes service dan repository MUST berjalan terhadap PostgreSQL sungguhan lewat Testcontainers.
  H2 dilarang (`TC-38`).
- Nama tes MUST merujuk pengenal `AC-*` dari `spec.md`, sehingga cakupan kriteria penerimaan
  bisa diperiksa mesin (`TC-39`).
- Satu kelas tes ArchUnit MUST menegakkan batas arsitektur Prinsip III (`TC-40`).
- Invariant yang tidak boleh dilanggar MUST dijaga constraint database, bukan hanya kode
  aplikasi (`TC-19`).

**Rasional**: ADR-0007 sudah memperkirakan nasib aturan tanpa penegakan otomatis. H2 berbohong
pada tepian UUID, `timestamptz`, dan constraint khas PostgreSQL, sehingga tes hijau di sana tidak
membuktikan apa pun tentang produksi.

## Batasan Teknologi & Data

**Stack yang mengikat.** Perubahan pada baris mana pun menuntut ADR baru.

| Lapis | Pilihan |
| --- | --- |
| Runtime | Java 25 LTS |
| Framework | Spring Boot 3.5+ (3.5.x mendukung JDK 17-25) |
| Database | PostgreSQL 16+ |
| Persistensi | Spring Data JPA; migrasi lewat Flyway SQL murni |
| Render | Thymeleaf berbasis fragment, dirender server |
| Interaktivitas | HTMX untuk pertukaran fragment; Alpine.js untuk state klien |
| Styling | Tailwind CSS lewat CLI standalone, terikat build Maven |
| Identity | `IdentityProviderPort`; adapter dummy (`local`/`demo`) → Keycloak |

**Skema dan persistensi.**

- Migrasi Flyway adalah sumber kebenaran skema; entity divalidasi terhadapnya. `ddl-auto` selain
  `validate` dilarang di luar pengembangan lokal (`TC-17`).
- Penghapusan konten selalu soft delete, ditegakkan otomatis lewat `@SQLRestriction` (`TC-35`,
  BR-Q04).
- Setiap perubahan `essayScore` dan perhitungan ulang Result dicatat ke tabel audit hanya-sisip
  (`TC-37`, BR-G03).
- Kosakata kode mengikuti `CONTEXT.md`: kolom dan field memakai `client_id` / `clientId`,
  bukan `tenant_id` / `tenantId`.

**Operasi dan data.**

- Cadangan penuh harian ditambah arsip WAL untuk pemulihan titik waktu, dengan uji pemulihan
  terjadwal (`TC-43`).
- Log berformat terstruktur dan wajib membawa `clientId`, `userId`, serta `sessionId` (`TC-44`).
- Seluruh waktu disimpan UTC dan ditampilkan dalam timezone Client (BR-T01, BR-T02).
- Sasaran beban v1: 2.000 Session serentak per Client, ~10.000 platform-wide — diperlakukan
  sebagai hipotesis yang wajib diuji.

## Alur Kerja & Gerbang Mutu

**Hierarki dokumen.** Empat dokumen dengan pembagian tugas yang tidak boleh tertukar:

| Dokumen | Menjawab |
| --- | --- |
| `CONTEXT.md` | istilah apa yang dipakai (glosarium, bebas implementasi) |
| `.scratch/eduscreen/spec.md` | apa yang harus terjadi (`BR-*`, `AC-*`) |
| `CONSTITUTION.md` | bagaimana bentuk kodenya (`TC-01`..`TC-49`) |
| `docs/adr/` | mengapa masing-masing dipilih |

Konstitusi ini adalah lapisan prinsip di atas keempatnya dan yang dibaca perkakas Spec Kit.

**Gerbang sebelum pull request digabung.**

1. Perubahan yang menyentuh Session, SessionAnswer, Result, atau berkas membawa tes `404`
   lintas-Siswa dan lintas-Client (`TC-41`).
2. Tes ArchUnit hijau (`TC-40`).
3. Perilaku baru merujuk `BR-*` yang mengaturnya; bila belum ada, `spec.md` diperbarui lebih
   dulu — bukan sesudah.
4. Nama tes baru merujuk `AC-*` yang dibuktikannya (`TC-39`).
5. Istilah domain memakai kosakata `CONTEXT.md`, bukan sinonim yang dihindari di sana.

**Kapan ADR wajib ditulis.** Ketiganya harus benar: keputusannya mahal dibatalkan, mengejutkan
tanpa konteks, dan merupakan hasil trade-off nyata. Melonggarkan larangan mana pun di Prinsip VI
selalu memenuhi ketiganya.

**Pelanggaran.** Melanggar pasal mana pun adalah alasan sah untuk menolak sebuah pull request.
Pelanggaran yang disengaja dan dibenarkan dicatat sebagai ADR, bukan sebagai komentar di kode.

## Governance

Konstitusi ini mengungguli praktik lain. Ketika dokumen lain bertentangan dengannya, konstitusi
yang menang, dan dokumen itu yang diperbaiki.

**Prosedur amandemen.**

1. Usulan menyebut prinsip atau aturan `TC-*` yang terdampak dan alasan perubahannya.
2. Perubahan yang mencabut larangan atau melonggarkan aturan NON-NEGOTIABLE menuntut ADR baru
   di `docs/adr/` sebelum konstitusi diubah.
3. Amandemen diterapkan ke `CONSTITUTION.md` (teks aturan) dan ke berkas ini (lapisan prinsip)
   dalam perubahan yang sama, agar keduanya tidak pernah berselisih.
4. Tanggal amandemen dan versi diperbarui bersamaan.

**Kebijakan versi.** Semantic versioning atas dokumen ini:

- **MAJOR** — prinsip dihapus atau didefinisikan ulang secara tidak kompatibel; larangan
  NON-NEGOTIABLE dicabut.
- **MINOR** — prinsip atau bagian baru ditambahkan; panduan diperluas secara material.
- **PATCH** — klarifikasi, perbaikan kata, penajaman tanpa mengubah makna.

**Tinjauan kepatuhan.** Setiap tinjauan kode memeriksa kepatuhan terhadap tujuh prinsip di atas.
Kepatuhan yang bisa diperiksa mesin (Prinsip VII) tidak boleh dialihkan menjadi tugas manusia:
bila sebuah aturan sering dilanggar, jawabannya adalah menambah penegakan otomatis, bukan
menambah pengingat.

**Panduan runtime.** `CLAUDE.md` di root memuat petunjuk operasional bagi agen yang bekerja di
repo ini, termasuk penunjuk ke keempat dokumen di atas.

**Version**: 1.1.0 | **Ratified**: 2026-08-31 | **Last Amended**: 2026-08-31
