# Phase 0 Research: Portal Latihan Siswa Eduscreen (v1)

**Date**: 2026-08-31 | **Plan**: [plan.md](./plan.md)

## Status

Technical Context di `plan.md` memuat **nol** penanda `NEEDS CLARIFICATION`. Seluruh keputusan
teknis diselesaikan sebelum perencanaan ini, melalui enam ronde penggalian kebutuhan yang
hasilnya tercatat sebagai aturan `TC-*` di `CONSTITUTION.md` dan sebagai keputusan beralasan di
`docs/adr/0001`–`0016`.

Dokumen ini karena itu bukan riset baru, melainkan konsolidasi keputusan yang mengikat
implementasi, ditulis ulang dalam format Decision / Rationale / Alternatives agar bisa dinilai
ulang bila keadaan berubah. Setiap butir menyebut ADR asalnya.

---

## R-001: Konten master Eduscreen disalin, bukan direferensikan

**Decision**: Konten master yang diadopsi Client menjadi salinan penuh milik Client tersebut.
Setiap Question membawa `sourceQuestionId` sebagai jejak, yang tidak dipakai untuk sinkronisasi
apa pun.

**Rationale**: Referensi hidup berarti Eduscreen Admin bisa mengubah kalimat soal yang sedang
dikerjakan siswa di ratusan sekolah. Snapshot sesi melindungi pengerjaan yang berjalan, tetapi
tidak melindungi Exercise terkunci yang isinya berubah di bawah kaki Guru.

**Alternatives considered**: Referensi hidup (ditolak: isolasi tenant hilang). Referensi
berversi dengan pin dan upgrade manual (ditolak: butuh entitas versi, alur upgrade, dan UI diff
yang tidak dibayar siapa pun di v1).

**Consequence**: perbaikan salah ketik di master tidak merambat. Disengaja. → ADR-0001

---

## R-002: Finalisasi sesi saat diakses, tanpa penjadwal

**Decision**: Tidak ada cron maupun pekerjaan latar. Setiap pembacaan sesi membandingkan
`effectiveDeadline` dengan waktu server; bila lewat, sesi difinalisasi dan Result ditulis dalam
satu transaksi idempoten.

**Rationale**: Penjadwal menambah infrastruktur, pemantauan, dan mode kegagalan diam-diam demi
ketepatan detik yang tidak dibutuhkan siapa pun. Yang dibutuhkan adalah rekap Guru yang lengkap
saat dibuka — dan membuka rekap itu sendiri adalah akses yang memicu finalisasi.

**Alternatives considered**: Cron per menit (ditolak: infrastruktur untuk ketepatan yang tidak
bernilai). Lazy murni tanpa penulisan (ditolak: angka historis bisa bergeser bila aturan skoring
berubah).

**Consequence**: pembukaan rekap pertama setelah tenggat lebih lambat daripada berikutnya. → ADR-0002

---

## R-003: Mode Quiz/Practice melekat di Assignment

**Decision**: Mode ditentukan saat penerbitan, bukan saat Exercise dibuat. Exercise tetap netral.

**Rationale**: Guru lazim memakai paket soal yang sama sebagai Practice untuk pemanasan lalu
Quiz untuk penilaian. Menempelkan mode di Exercise memaksa duplikasi, diperburuk aturan Exercise
terkunci setelah terbit.

**Alternatives considered**: Mode di Exercise (ditolak: memaksa duplikat yang lalu menyimpang).

**Consequence**: validasi kompatibilitas terjadi saat publish, bukan saat perakitan — Guru baru
tahu Exercise beressay tidak bisa jadi Practice di langkah terakhir. → ADR-0003

---

## R-004: UUID v7 sebagai primary key

**Decision**: Seluruh entitas yang pengenalnya muncul di URL memakai UUID v7, dibuat aplikasi
(bukan database).

**Rationale**: Auto-increment menjadikan pencegahan IDOR bergantung sepenuhnya pada pemeriksaan
otorisasi; satu endpoint yang lupa langsung membocorkan tabel secara berurutan. Di antara bentuk
UUID, v7 terurut waktu sehingga menulis di ujung index — penting karena `SessionAnswer` adalah
tabel dengan tulis paling deras (auto-save per jawaban × ribuan sesi serentak), dan kunci acak v4
memecah lokalitas halaman B-tree tepat pada beban itu.

**Alternatives considered**: `bigserial` (ditolak: bisa dijelajahi). UUID v4 (ditolak:
fragmentasi index tanpa manfaat tambahan).

**Consequence**: pengenal membocorkan waktu pembuatan secara kasar — tidak sensitif di sini; 16
byte per kunci. → ADR-0009

---

## R-005: Halaman dirender server dengan Thymeleaf + HTMX

**Decision**: Fragmen Thymeleaf dirender server; HTMX menukar fragmen; Alpine.js hanya untuk
state kecil di klien. Endpoint auto-save mengembalikan fragmen HTML, bukan JSON.

**Rationale**: Aturan paling penting di sistem ini adalah bahwa waktu dan kepemilikan sesi
ditentukan server. Ketika server yang merender, keadaan yang dilihat Siswa berasal dari tempat
yang sama dengan keadaan yang menjadi keputusan. SPA menyimpan salinan keadaan di klien — persis
benda yang empat lapis perlindungan dibangun untuk tidak percayai.

**Alternatives considered**: SPA + REST API (ditolak: dua model data yang harus disinkronkan,
dua tempat otorisasi bisa menyimpang, kontrak API tanpa konsumen lain).

**Consequence**: aplikasi seluler native kelak memerlukan API yang saat ini tidak ada. → ADR-0010

---

## R-006: Sanitasi konten kaya saat menulis

**Decision**: `Question.body` dan `Option.body` disanitasi allowlist saat disimpan; database
hanya berisi HTML bersih; template merender dengan `th:utext`. Rumus disimpan sebagai LaTeX
berdelimiter dan dirender KaTeX di klien. Impor CSV melewati jalur sanitasi yang sama.

**Rationale**: Soal ditulis Guru lalu ditayangkan ke puluhan Siswa, termasuk ke halaman tempat
ujian sedang berjalan. Sanitasi saat render menyebar tanggung jawab keamanan ke setiap template,
dan satu template yang lupa memanggilnya membuka lubang tanpa galat yang terlihat.

**Alternatives considered**: Sanitasi saat render (ditolak: menyebar tanggung jawab). Markdown
terbatas (ditolak: editor kurang ramah bagi guru non-teknis). Tanpa sanitasi (ditolak: stored
XSS).

**Consequence**: markup asli hilang permanen, sehingga allowlist harus cukup lebar sejak awal
dan setiap pengetatan menuntut migrasi pembersihan. → ADR-0011

---

## R-007: Filter `client_id` eksplisit; soft delete otomatis

**Decision**: `deletedAt` ditegakkan otomatis lewat `@SQLRestriction`. `client_id` ditulis
eksplisit di tanda tangan setiap method repository — tanpa `@Filter`, tanpa Row-Level Security.

**Rationale**: Kedua risiko tidak setara. Melewatkan filter soft delete menampilkan konten basi,
terlihat segera. Melewatkan filter `client_id` membocorkan bank soal satu sekolah ke sekolah
lain, dan bisa berjalan berbulan-bulan tanpa terdeteksi. Untuk risiko sebesar itu, keterlihatan
di kode dan di tinjauan lebih berharga daripada kenyamanan.

**Alternatives considered**: `@Filter` Hibernate (ditolak: aturan terpenting jadi tak terlihat).
PostgreSQL RLS (ditolak untuk v1: butuh `SET LOCAL` yang bersanding dengan connection pool, dan
mengubah setiap bug menjadi hasil kosong tanpa penjelasan; tetap terbuka sebagai lapis tambahan
kelak).

**Consequence**: method repository panjang; penegakannya bersandar pada tes TC-41. → ADR-0012

---

## R-008: Kunci pesimistis untuk finalisasi, upsert untuk auto-save

**Decision**: Finalisasi mengambil `SELECT … FOR UPDATE` pada baris sesi lalu memeriksa status,
didukung unique constraint pada `result.session_id`. Auto-save adalah upsert berkunci
`session_question_id`; kiriman ulang berisi jawaban identik adalah no-op yang sukses.

**Rationale**: R-002 menjadikan dua pembaca bisa memfinalisasi sesi yang sama bersamaan. Untuk
auto-save, antrean coba-ulang di klien menjamin server menerima kiriman ganda — server yang
menolak kiriman ulang mengubah mekanisme pemulihan menjadi sumber kerusakan, terutama pada
Practice yang mengunci jawaban saat dikirim.

**Alternatives considered**: Optimistic locking dengan `@Version` dan coba-ulang (ditolak: bisa
gagal berulang saat rekap borongan). Unique constraint saja (ditolak: status sesi bisa tertulis
dua kali). Idempotency key dari klien (ditolak: tabel tambahan dan pembersihan berkala untuk
masalah yang sudah diselesaikan kunci alami).

**Consequence**: rekap Guru memfinalisasi per sesi dalam transaksi terpisah, bukan satu transaksi
panjang yang mengunci seluruh Ruangan. → TC-18 sampai TC-21

---

## R-009: Satu instance untuk v1

**Decision**: Sesi login di memori, berkas di filesystem lokal di balik `FileStoragePort`. Tanpa
Redis, tanpa penyimpanan objek, tanpa penyeimbang beban.

**Rationale**: Beban 10.000 sesi serentak sebagian besar jatuh ke database, bukan ke instance
aplikasi: auto-save adalah tulis kecil dan halaman yang dirender server hampir tidak menyimpan
state. Menambah dua infrastruktur untuk masalah yang belum diukur adalah biaya tanpa bukti.

**Alternatives considered**: Siap mendatar sejak awal (ditolak untuk v1). Berkas di S3 sejak awal
(ditolak: port sudah membuat penukarannya murah).

**Consequence**: setiap deploy memutus sesi yang sedang berjalan, sehingga rilis dijadwalkan di
luar jam ujian. Pemicu pindah ke mendatar adalah kebutuhan deploy tanpa memutus ujian, bukan
angka beban. → ADR-0013

---

## R-010: Impor sinkron berbatas 500 baris

**Decision**: Impor diproses di thread permintaan dengan batas tegas 500 baris; berkas lebih
besar ditolak dengan pesan untuk memecahnya.

**Rationale**: Impor adalah tempat pertama yang menggoda seseorang memasukkan kembali antrean
pekerjaan yang R-002 tolak — dan begitu ia ada, ia akan segera dipakai untuk hal lain. Onboarding
adalah peristiwa sekali seumur Client, dan memecah berkas adalah pekerjaan beberapa menit.

**Alternatives considered**: Tabel pekerjaan dan pemroses latar (ditolak: mencabut R-002 tanpa
mencabutnya secara sadar). Pemecahan di klien (ditolak: logika pemecahan hidup di klien).

**Consequence**: Client dengan bank soal warisan besar harus memecah berkasnya — friksi
onboarding yang disebut sejak awal. Batasnya angka, bukan prinsip. → ADR-0014

---

## R-011: Akses dukungan break-glass

**Decision**: Isolasi tenant absolut, dengan satu pengecualian: jendela baca-saja yang dinyalakan
Client Admin, padam sendiri setelah 4 jam, dan setiap pembacaannya tercatat.

**Rationale**: Isolasi absolut yang tidak menyediakan jalur dukungan resmi tidak menghasilkan
privasi; ia menghasilkan seseorang yang membuka koneksi database produksi untuk "melihat
sebentar" — akses penuh tulis, tanpa persetujuan, tanpa jejak.

**Alternatives considered**: Tanpa akses sama sekali (ditolak: mendorong jalur tidak resmi).
Akses penuh dengan audit (ditolak: mematahkan janji isolasi).

**Consequence**: BR-P04 tidak lagi mutlak; pengecualiannya tercatat sebagai BR-P05. → ADR-0015

---

## R-012: Batas pemakaian adapter identity dummy

**Decision**: Kebijakan password ditunda ke Keycloak. Adapter dummy boleh hidup di `local` dan
`demo`, dan tidak boleh berada di sistem yang memuat data siswa nyata. Batasnya adalah isi data,
bukan label environment.

**Rationale**: Menulis kebijakan password sendiri hari ini berarti membangun sesuatu yang dibuang
saat Keycloak masuk. Yang tidak bisa diterima adalah menunda kebijakan sambil melepas rilis:
adapter dummy menerima satu password untuk semua akun, sehingga siapa pun yang mengetahuinya
masuk sebagai peran mana pun.

**Alternatives considered**: Membangun adapter lokal BCrypt sekarang (ditunda sampai ada Client
yang membawa data nyata). Dummy sampai produksi tanpa pagar (ditolak).

**Consequence**: environment `demo` wajib berspanduk, tanpa email keluar, dan tidak pernah
dipulihkan dari cadangan produksi. Client pertama berdata nyata adalah pemicu keras. → ADR-0016

---

## Praktik implementasi yang mengikat

Bukan keputusan baru; dicatat di sini agar tersedia saat penyusunan tugas.

| Area | Praktik |
| --- | --- |
| UUID v7 | Dibuat aplikasi sebelum persist, sehingga entitas punya identitas sebelum disimpan |
| Waktu | `OffsetDateTime` di Java, `timestamptz` di PostgreSQL, konversi ke zona Client hanya di lapisan render |
| Skema | Flyway SQL murni; `ddl-auto: validate`; migrasi adalah sumber kebenaran |
| Galat HTMX | Satu `@ControllerAdvice`; tak terautentikasi → `401` + `HX-Redirect`, bukan `302` |
| Unggahan | Batas ukuran, tipe dari magic bytes, encode ulang saat simpan |
| Pencarian | Kolom teks polos turunan diperbarui pada operasi tulis yang sama |
| Log | Terstruktur, membawa `clientId`/`userId`/`sessionId`; tanpa password, jawaban, isi soal, email |
| Tes | Testcontainers PostgreSQL; nama tes merujuk `AC-*`; H2 dilarang |
