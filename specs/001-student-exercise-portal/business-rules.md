# Aturan Bisnis & Kriteria Penerimaan — Eduscreen v1

**Feature**: [spec.md](./spec.md) | **Konstitusi**: `CONSTITUTION.md` | **Glosarium**: `CONTEXT.md`

Berkas ini memuat perilaku terperinci yang menjadi rujukan `FR-*` di [spec.md](./spec.md):
**72 aturan bisnis** `BR-*` dan **72 kriteria penerimaan** `AC-*`.

Yang TIDAK ada di sini karena sudah punya rumah sendiri, agar tidak ada dua sumber kebenaran:

| Isi | Rumahnya |
| --- | --- |
| Visi, aktor, cerita pengguna, `FR-*`, `SC-*`, lingkup | [spec.md](./spec.md) |
| Entitas, relasi, state machine, index | [data-model.md](./data-model.md) |
| Permukaan endpoint | [contracts/](./contracts/) |
| Keputusan beserta alasannya | `docs/adr/` |
| Aturan teknis `TC-*` | `CONSTITUTION.md` |

---

## Aturan Siklus Hidup

Aturan yang mengikat transisi status Assignment dan Result. Diagram state machine-nya
ada di [data-model.md](./data-model.md); yang di bawah adalah aturannya.

- **BR-A01** — Di `DRAFT` seluruh atribut bebas diubah.
- **BR-A02** — Di `PUBLISHED` hanya `expiresAt` yang boleh diubah, dan hanya **diperpanjang**. Memajukannya ditolak.
- **BR-A03** — `timerDurationMinutes`, `mode`, `exerciseId`, `shuffleQuestions`, `shuffleOptions`, dan `maxAttempts` terkunci setelah publish.
- **BR-A04** — Assignment `PUBLISHED` atau `CLOSED` tidak bisa dihapus. Hanya draft yang bisa.
- **BR-A05** — Menutup lebih awal memfinalisasi seluruh Session `IN_PROGRESS` di Assignment itu dengan `terminalReason = EXPIRATION_REACHED`.

- **BR-R01** — Result `PRACTICE` selalu langsung `FINAL`; Practice tidak boleh memuat essay (BR-M04).
- **BR-R02** — Skor Result `PENDING_REVIEW` menampilkan porsi MCQ saja dan ditandai sementara.

---
## Aturan Izin

Catatan izin yang tidak jelas dari tabel:

- **BR-P01** — Guru hanya bisa menerbitkan Assignment ke Ruangan tempat ia ditugaskan.
- **BR-P02** — Question dan Exercise milik Client terlihat oleh seluruh Guru di Client itu, tanpa memandang siapa pembuatnya. Tidak ada konten privat per Guru.
- **BR-P03** — Siswa tidak pernah bisa membaca Question langsung; ia hanya melihat Question lewat SessionQuestion di dalam Session miliknya.
- **BR-P04** — Tidak ada peran yang bisa membaca data Client lain. Isolasi tenant absolut, dengan satu pengecualian yang diatur BR-P05.
- **BR-P05** — Client Admin dapat menyalakan **akses dukungan**: jendela **baca-saja** bagi Eduscreen Admin atas data Client-nya, padam otomatis setelah beberapa jam, dengan setiap pembacaan tercatat di audit yang bisa ditunjukkan kepada Client. Eduscreen Admin tidak pernah bisa mengubah data Client, bahkan selama jendela itu terbuka. Tanpa persetujuan Client Admin, tanda `—` pada matriks di atas berlaku penuh (ADR-0015).

## 6. Alur Fungsional

### 6.1 Eduscreen Admin — onboarding Client

1. Isi nama Client dan `timezone`.
2. Isi email Client Admin pertama; sistem mengirim undangan.
3. Pilih Paket master yang disalin ke Client. Subject tidak dipilih terpisah — ia ikut sebagai label Paket.
4. Sistem melakukan copy-on-adopt (ADR-0001): Paket beserta Topic, Question, dan Option di dalamnya disalin ke `clientId` baru. Exercise dan ExerciseItem tidak ikut disalin — Exercise milik alur Guru dan tidak pernah jadi objek adopsi.
5. Client berstatus `ACTIVE` dengan bank soal terisi.

- **BR-O01** — Onboarding tidak membuat Ruangan maupun akun Siswa. Itu pekerjaan Client Admin.
- **BR-O02** — Subject `GLOBAL` tidak disalin; ia dibaca langsung oleh semua Client. Yang disalin adalah Paket, Topic, Question, dan Option.

### 6.2 Eduscreen Admin — konten master

Mengelola Subject `GLOBAL`, Topic `GLOBAL`, Question `owner = EDUSCREEN`, dan Exercise `owner = EDUSCREEN`. Perubahan di sini tidak pernah merambat ke Client yang sudah mengadopsi (ADR-0001).

- **BR-O03** — Nama Subject `GLOBAL` tunggal, dibandingkan tanpa memandang kapital maupun spasi tepi. Subject `GLOBAL` ada justru agar semua sekolah memakai satu ejaan (ADR-0004); duplikat di dalamnya merusak alasan itu. Subject lokal tidak terikat aturan ini — dua Client berhak menamai Subject-nya sama karena taksonomi mereka terisolasi.
- **BR-O04** — Nama Subject `GLOBAL` boleh diperbaiki kapan saja. Karena Subject `GLOBAL` tidak pernah disalin (BR-O02), perbaikan itu langsung terlihat semua Client tanpa satu pun salinan ikut berubah.
- **BR-O05** — Pekerjaan konten master yang macet karena aturan penerbitan harus terlihat tanpa dicari. Paket yang isinya belum terbit (FR-069) dan Subject global tanpa Topic adalah jalan buntu yang tidak menjelaskan dirinya sendiri di layar tempat ia dibuat.

### 6.3 Client Admin — Ruangan dan akun

1. Buat Ruangan dengan nama yang memuat periode (`Kelas 4B 2026/2027`).
2. Buat akun Guru dan Siswa satu per satu atau lewat impor.
3. Tugaskan Guru dan Siswa ke satu atau lebih Ruangan.
4. Di akhir tahun ajaran, arsipkan Ruangan dan buat yang baru.

- **BR-U01** — Satu Siswa boleh menjadi anggota banyak Ruangan; satu Ruangan boleh punya banyak Guru.
- **BR-U02** — Ruangan `ARCHIVED` bersifat read-only: tidak menerima Assignment baru, tidak menerima anggota baru, Session baru tidak bisa dimulai. Riwayat Result tetap terbaca.
- **BR-U03** — Menonaktifkan akun Siswa tidak menghapus Session dan Result miliknya.
- **BR-U04** — Email **transaksional** termasuk lingkup v1: undangan akun dan reset password. Tanpanya setiap password lupa menjadi tiket ke Client Admin. Email **pemberitahuan** (tugas baru terbit, pengingat deadline) tetap di luar v1 — lihat §12.

### 6.4 Client Admin & Guru — Question Bank

Keduanya boleh menulis Topic dan Question di lingkup Client. Tiga jalur pengisian:

1. **Adopsi katalog Eduscreen** — pilih Paket master, sistem menyalinnya (ADR-0001).
2. **Editor manual** — rich text, gambar, dan notasi matematika di batang soal maupun tiap Option.
3. **Impor Excel/CSV** — hanya soal berbasis teks. Alurnya: unggah berkas → pratinjau hasil parsing → laporan baris gagal beserta alasannya → simpan hanya baris yang valid.

- **BR-Q01** — Question `MULTIPLE_CHOICE` wajib punya minimal 2 Option dan tepat 1 Option benar.
- **BR-Q02** — Question wajib melekat pada tepat satu Topic.
- **BR-Q03** — Question yang akan dipakai Practice wajib punya `explanation`; divalidasi saat publish, bukan saat penulisan.
- **BR-Q04** — Penghapusan Question dan Exercise selalu soft delete. Konten yang dihapus hilang dari pencarian bank soal tetapi tetap utuh di Exercise, Assignment, dan Session yang sudah memakainya.
- **BR-Q05** — Impor menolak seluruh berkas hanya bila formatnya tidak terbaca; kegagalan per baris tidak membatalkan baris lain.
- **BR-Q06** — Satu berkas impor memuat maksimum **500 baris**. Berkas yang lebih besar ditolak sebelum diproses, disertai pesan yang meminta pengguna memecahnya. Batas ini menjaga impor tetap berjalan sinkron tanpa infrastruktur pekerjaan latar (ADR-0014).

### 6.5 Guru — meracik Exercise

1. Buat Exercise, beri judul.
2. Telusuri bank soal Client. Filter berdasarkan Subject dan Topic, dan **boleh berpindah Subject maupun Topic dalam satu sesi perakitan**.
3. Tambahkan Question ke Exercise; atur urutannya.
4. Simpan.

- **BR-E01** — Exercise boleh memuat Question dari Paket dan Topic mana pun di dalam Client.
- **BR-E02** — Exercise terlihat dan bisa diduplikasi oleh seluruh Guru di Client yang sama.
- **BR-E03** — Exercise wajib memuat minimal 1 Question untuk bisa diterbitkan.
- **BR-E04** — Begitu Assignment pertamanya dibuat, `lockedAt` terisi dan Exercise menjadi read-only. Untuk mengubahnya, Guru menduplikasinya menjadi Exercise baru.

### 6.6 Guru — menerbitkan Assignment

1. Pilih Exercise dan Ruangan tujuan.
2. Pilih `mode`: `QUIZ` atau `PRACTICE`.
3. Isi aturan waktu, `maxAttempts`, dua sakelar pengacakan, dan `revealAnswersAt`.
4. Publish.

- **BR-M01** — Guru hanya bisa memilih Ruangan tempat ia ditugaskan dan yang berstatus `ACTIVE`.
- **BR-M02** — Satu Assignment menyasar tepat satu Ruangan. Menerbitkan ke tiga Ruangan menghasilkan tiga Assignment; UI boleh menyediakan tindakan borongan, tetapi entitasnya tetap tiga.
- **BR-M03** — `QUIZ` wajib mengisi `timerDurationMinutes`. `PRACTICE` boleh mengosongkannya.
- **BR-M04** — Publish sebagai `PRACTICE` **ditolak** bila Exercise memuat Question bertipe `ESSAY`, atau ada Question tanpa `explanation`. Pesan galat menyebutkan Question mana yang menyebabkannya.
- **BR-M05** — `expiresAt` wajib di kedua mode dan harus berada di masa depan saat publish.
- **BR-M06** — `maxAttempts` minimal 1 untuk `QUIZ`. Untuk `PRACTICE` nilainya diabaikan; Attempt tidak terbatas.
- **BR-M07** — Publish mengunci Exercise (BR-E04).

### 6.7 Siswa — dashboard dan pengerjaan

Dashboard menampilkan dua bagian: **Assignment aktif** dari seluruh Ruangan yang diikuti, dan **riwayat Result** miliknya sendiri.

Alur pengerjaan:

1. Siswa membuka Assignment dan menekan **Mulai Exercise**.
2. Sistem membuat Session, menghitung `effectiveDeadline`, mengacak, dan membekukan Snapshot.
3. Siswa menjawab; tiap jawaban terkirim otomatis ke server.
4. Session berakhir lewat salah satu dari tiga terminal condition (§8.3).

Perbedaan perilaku per mode:

| | `QUIZ` | `PRACTICE` |
| --- | --- | --- |
| Navigasi | bebas ke soal mana pun, ada peta soal | maju satu arah |
| Ubah jawaban | boleh sampai Selesai | tidak; terkunci saat dikirim |
| Feedback | sesuai `revealAnswersAt` | benar/salah + `explanation` seketika |
| Selesai | tombol Selesai | otomatis setelah soal terakhir, atau tombol Selesai |

- **BR-S01** — Session lahir **hanya** saat Siswa menekan Start. Tidak ada pembuatan massal (lazy instantiation).
- **BR-S02** — Snapshot dibekukan saat Session lahir dan tidak pernah berubah, termasuk ketika Siswa kembali setelah terputus.
- **BR-S03** — Dua Siswa pada Assignment yang sama mendapat urutan Question berbeda bila `shuffleQuestions` menyala.
- **BR-S04** — Tiap Attempt adalah Session baru dengan Snapshot baru.
- **BR-S05** — Siswa hanya bisa memulai Attempt baru bila Session sebelumnya sudah terminal dan `attemptNumber < maxAttempts` (Quiz) atau selalu (Practice).
- **BR-S06** — Membuka kembali Assignment yang Session-nya masih `IN_PROGRESS` mengembalikan Siswa ke Session itu, bukan membuat yang baru.
- **BR-S07** — Pada `PRACTICE`, SessionAnswer yang sudah dikirim tidak bisa diubah; `lockedAt` terisi dan `explanation` terbuka.
- **BR-S08** — Pengerjaan membutuhkan koneksi (ADR-0006). Jawaban dikirim segera dengan antrean coba-ulang; kegagalan berkepanjangan ditampilkan sebagai indikator koneksi, bukan disimpan lokal.

### 6.8 Guru — menilai essay

1. Buka daftar Result berstatus `PENDING_REVIEW` untuk satu Assignment.
2. Baca `essayText` tiap SessionAnswer, beri `essayScore` 0–100.
3. Saat essay terakhir di satu Session dinilai, Result-nya dihitung ulang dan berubah menjadi `FINAL`.

- **BR-G01** — Hanya Guru yang ditugaskan di Ruangan Assignment itu yang bisa menilai.
- **BR-G02** — `essayScore` boleh diubah selama Result belum dipakai di luar sistem; setiap perubahan memicu perhitungan ulang Result.
- **BR-G03** — Setiap perubahan `essayScore` dan setiap perhitungan ulang Result meninggalkan jejak audit permanen: siapa yang mengubah, kapan, dari nilai berapa ke berapa. Jejak ini tidak pernah diubah atau dihapus, dan bisa ditampilkan saat nilai dipersengketakan.

### 6.9 Guru — laporan

Laporan satu Assignment menampilkan **seluruh Siswa anggota Ruangan**, bukan hanya yang mengerjakan.

| Kolom | Isi |
| --- | --- |
| Siswa | nama |
| Status | `NOT_STARTED` \| `IN_PROGRESS` \| `COMPLETED` \| `EXPIRED` |
| Attempt | jumlah Session |
| Skor resmi | skor tertinggi di antara Result-nya; 0 bila `NOT_STARTED` |
| Penilaian | `FINAL` atau `PENDING_REVIEW` |

- **BR-L01** — Laporan dibangun dari daftar anggota Ruangan, bukan dari daftar Session. Siswa tanpa Session tampil `NOT_STARTED` dengan skor 0 **tanpa** membuat baris Session (BR-S01 tetap utuh).
- **BR-L02** — Membuka laporan memicu finalisasi seluruh Session Assignment itu yang sudah lewat `effectiveDeadline` (ADR-0002).
- **BR-L03** — Skor resmi seorang Siswa adalah skor **tertinggi** di antara seluruh Result-nya pada Assignment itu. Seluruh Attempt tetap bisa dibuka Guru.
- **BR-L04** — Result `kind = PRACTICE` tidak masuk rekap nilai; ditampilkan di laporan aktivitas latihan yang terpisah.

---

## 7. Aturan Bisnis — indeks

| Kelompok | Prefiks | Isi |
| --- | --- | --- |
| Izin | `BR-P` | akses lintas peran dan tenant |
| Onboarding | `BR-O` | pembuatan Client dan adopsi konten |
| Akun & Ruangan | `BR-U` | keanggotaan dan arsip |
| Question Bank | `BR-Q` | validitas soal, soft delete, impor |
| Exercise | `BR-E` | perakitan dan penguncian |
| Assignment | `BR-A`, `BR-M` | siklus hidup dan penerbitan |
| Session | `BR-S` | lazy instantiation, snapshot, attempt |
| Waktu | `BR-T` | deadline efektif dan finalisasi |
| Skoring | `BR-C` | perhitungan nilai |
| Penilaian | `BR-G` | essay |
| Laporan | `BR-L` | rekap Ruangan |
| Result | `BR-R` | status penilaian |

---

## 8. Aturan Waktu & Penutupan Sesi

### 8.1 Otoritas waktu

- **BR-T01** — Seluruh waktu disimpan dan dihitung dalam UTC.
- **BR-T02** — Setiap tampilan waktu dan setiap deadline dirender dalam `client.timezone`. `Minggu 23:59` berarti 23:59 di zona Client, bukan di zona perangkat Siswa.
- **BR-T03** — Perhitungan sisa waktu adalah otoritas server. Jam perangkat Siswa tidak pernah dipercaya; hitung mundur di layar hanyalah tampilan yang disinkronkan berkala ke server.

### 8.2 Deadline efektif

Saat Session lahir, sistem menghitung dan **membekukan**:

```
effectiveDeadline =
    timerDurationMinutes ada
        ? min(startedAt + timerDurationMinutes, assignment.expiresAt)
        : assignment.expiresAt
```

- **BR-T04** — Global Expiration selalu memangkas Timer. Siswa yang Start 10 menit sebelum `expiresAt` dengan Timer 60 menit mendapat 10 menit, dan layar menampilkan sisa waktu yang sudah terpangkas sejak detik pertama — bukan 60 menit yang tiba-tiba terputus.
- **BR-T05** — Practice tanpa Timer memakai `expiresAt` sebagai satu-satunya deadline.
- **BR-T06** — `effectiveDeadline` beku. Guru memperpanjang `expiresAt` **tidak** memperpanjang Session yang sudah terminal, dan tidak menghidupkan kembali Session `EXPIRED`. Perpanjangan hanya menguntungkan Session yang belum dimulai dan yang masih `IN_PROGRESS` — untuk yang masih berjalan, `effectiveDeadline` dihitung ulang sekali saat perpanjangan disimpan.

### 8.3 Tiga terminal condition

| Sebab | Pemicu | `terminalReason` | Status akhir |
| --- | --- | --- | --- |
| Manual Submit | Siswa menekan Selesai | `MANUAL_SUBMIT` | `COMPLETED` |
| Timer Timeout | `effectiveDeadline` tercapai karena Timer habis | `TIMER_TIMEOUT` | `EXPIRED` |
| Global Expiration | `effectiveDeadline` tercapai karena `expiresAt` | `EXPIRATION_REACHED` | `EXPIRED` |

### 8.4 Finalisasi

Tidak ada scheduler (ADR-0002). Setiap pembacaan Session menjalankan:

```
finalize(session):
    jika session.status ≠ IN_PROGRESS: kembalikan apa adanya
    jika now ≤ session.effectiveDeadline: kembalikan apa adanya

    sebab := session.effectiveDeadline == assignment.expiresAt
                 ? EXPIRATION_REACHED
                 : TIMER_TIMEOUT

    dalam satu transaksi, dengan kunci pada session:
        session.status         := EXPIRED
        session.terminalReason := sebab
        session.finalizedAt    := now
        hitung dan simpan Result
```

- **BR-T07** — Finalisasi idempoten. Dua permintaan bersamaan atas Session yang sama menghasilkan tepat satu Result.
- **BR-T08** — Jawaban yang tiba setelah `effectiveDeadline` ditolak, meskipun Session belum sempat difinalisasi.
- **BR-T09** — Skor disimpan sebagai angka hasil hitung, tidak dihitung ulang saat dibaca, agar angka historis tidak bergeser bila aturan skoring berubah.

### 8.5 Kasus tepi yang harus terjawab

| Skenario | Perilaku |
| --- | --- |
| Start 10 menit sebelum `expiresAt`, Timer 60 menit | durasi efektif 10 menit, tampil sejak awal (BR-T04) |
| Browser tertutup, tidak pernah kembali | Result muncul saat Guru membuka laporan (BR-L02) |
| Tidak pernah Start | `NOT_STARTED`, skor 0, tanpa baris Session (BR-L01) |
| Guru perpanjang `expiresAt` setelah beberapa Session `EXPIRED` | yang expired tetap expired (BR-T06) |
| Question di-soft-delete saat Assignment berjalan | Session tidak terganggu (BR-Q04) |
| Publish Exercise beressay sebagai Practice | ditolak dengan daftar Question penyebab (BR-M04) |
| Attempt ke-2 lebih rendah dari ke-1 | skor resmi tetap yang tertinggi (BR-L03) |
| Siswa terputus 5 menit lalu kembali | Snapshot dan jawaban utuh, Timer tetap berjalan selama terputus (BR-S02, BR-T03) |

---

## 9. Skoring & Penilaian

### 9.1 Bobot

- **BR-C01** — Setiap Question bernilai **1 poin**. Tidak ada bobot per soal.
- **BR-C02** — Tidak ada nilai minus. Salah dan tidak dijawab sama-sama bernilai 0.
- **BR-C03** — Question `MULTIPLE_CHOICE` bernilai 1 bila `selectedOptionId` menunjuk Option `isCorrect`, selain itu 0.

### 9.2 Essay

- **BR-C04** — Guru memberi `essayScore` 0–100. Poin soal itu = `essayScore ÷ 100`, menghasilkan pecahan dari 1 poin. Bobot tetap seragam, penilaian tetap luwes untuk jawaban separuh benar.
- **BR-C05** — Essay yang belum dinilai berkontribusi 0 poin pada skor sementara, dan Result berstatus `PENDING_REVIEW`.

### 9.3 Rumus

```
score = Σ poin per SessionQuestion ÷ totalQuestions
```

- **BR-C06** — Soal tak terjawab saat finalisasi dihitung salah dan masuk `unansweredCount`.
- **BR-C07** — Skor resmi Siswa pada satu Assignment = skor tertinggi di antara seluruh Result-nya (BR-L03).

### 9.4 Practice

- **BR-C08** — Result Practice memakai rumus yang sama tetapi ber-`kind = PRACTICE` dan tidak masuk rekap nilai (BR-L04).
- **BR-C09** — Practice tidak pernah memuat essay (BR-M04), sehingga Result-nya selalu langsung `FINAL`.

### 9.5 Visibilitas ke Siswa

| Mode | Yang tampil | Kapan |
| --- | --- | --- |
| `PRACTICE` | benar/salah + `explanation` per soal | seketika setelah jawaban dikirim |
| `QUIZ`, `revealAnswersAt = AFTER_SUBMIT` | skor + kunci + `explanation` | setelah Session terminal |
| `QUIZ`, `revealAnswersAt = AFTER_EXPIRATION` | skor saja setelah submit; kunci + `explanation` | setelah `expiresAt` Assignment |

- **BR-C10** — Skor yang tampil saat Result `PENDING_REVIEW` ditandai sementara dan menyatakan bahwa penilaian essay masih berjalan.

---

## 10. Kriteria Penerimaan

### Izin & tenancy

**AC-P01** (BR-P01)
Given Guru A ditugaskan di Ruangan `4A` saja
When ia membuka daftar Ruangan tujuan saat publish
Then hanya `4A` yang muncul, dan permintaan langsung ke `4B` ditolak.

**AC-P02** (BR-P04)
Given Question milik Client X
When Guru dari Client Y mencari di bank soal
Then Question itu tidak muncul dalam hasil apa pun.

**AC-P03** (BR-P02)
Given Guru A menulis Question baru di Client X
When Guru B di Client X membuka bank soal
Then Question itu terlihat dan bisa dipakai.

**AC-P05** (BR-P05)
Given Client Admin menyalakan akses dukungan selama 4 jam
When Eduscreen Admin membuka bank soal Client itu
Then ia bisa membacanya, setiap pembacaan tercatat di audit, dan setiap upaya mengubah data ditolak
And setelah 4 jam lewat, akses padam sendiri tanpa tindakan siapa pun.

**AC-P04** (BR-P03)
Given seorang Siswa mengetahui pengenal sebuah Question
When ia meminta Question itu di luar konteks Session
Then permintaan ditolak; Question hanya terbaca lewat SessionQuestion di Session miliknya sendiri.

### Onboarding & konten

**AC-O01** (BR-O01, ADR-0001)
Given Eduscreen Admin melakukan onboarding Client baru dan memilih satu Paket master berisi 1 Topic dan 20 Question
When onboarding selesai
Then Client punya 1 Paket, 1 Topic, dan 20 Question dengan `clientId` miliknya, dan mengedit Question master setelahnya tidak mengubah salinan Client.

**AC-Q01** (BR-Q01)
Given Guru menyusun Question `MULTIPLE_CHOICE` dengan dua Option ditandai benar
When ia menyimpan
Then penyimpanan ditolak dengan pesan bahwa tepat satu Option harus benar.

**AC-Q02** (BR-Q04)
Given Question `Q1` dipakai di 12 Exercise, tiga di antaranya punya Assignment `PUBLISHED`
When Client Admin menghapus `Q1`
Then `Q1` hilang dari pencarian bank soal, seluruh Exercise dan Session yang memakainya tetap utuh, dan Siswa yang sedang mengerjakan tidak melihat perubahan apa pun.

**AC-Q03** (BR-Q05)
Given berkas impor 500 baris dengan 7 baris tanpa kunci jawaban
When Client Admin mengimpornya
Then pratinjau menampilkan 493 baris valid dan 7 baris gagal beserta nomor baris dan alasannya, dan menyimpan hanya memasukkan 493 Question.

**AC-Q06** (BR-Q06)
Given berkas impor berisi 2.000 baris
When Client Admin mengunggahnya
Then berkas ditolak sebelum diproses dengan pesan yang menyebut batas 500 baris dan meminta pengguna memecahnya.

**AC-Q07** (BR-Q05, ADR-0018)
Given Client Admin memilih Paket dan Topic tujuan di layar impor, dan kolom `topic` pada berkas menyebut nama Topic lain miliknya
When ia menyimpan hasil pratinjau
Then seluruh baris valid tersimpan ke Paket dan Topic yang dipilih; kolom `topic` berkas tidak menentukan tujuan, dan mengosongkannya tidak membuat barisnya gagal.

**AC-O02** (BR-O02)
Given paket master yang diadopsi berada di bawah Subject `GLOBAL` `Matematika Kelas 4`
When onboarding selesai
Then tidak ada Subject baru dibuat untuk Client; yang disalin adalah Paket, Topic, Question, dan Option, semuanya menunjuk Subject global yang sama, karena Subject `GLOBAL` tidak pernah disalin.

**AC-Q04** (BR-Q02)
Given Guru menulis Question tanpa memilih Topic
When ia menyimpan
Then penyimpanan ditolak; Question tidak boleh menggantung di luar taksonomi.

**AC-Q05** (BR-Q03)
Given Exercise berisi 10 Question `MULTIPLE_CHOICE`, dua di antaranya tanpa `explanation`
When Guru menerbitkannya sebagai `PRACTICE`
Then publish ditolak dengan menyebut dua Question itu
And menerbitkannya sebagai `QUIZ` tetap diterima.

### Bank soal & Paket

`FR-012`, `FR-013`, `FR-014`, `FR-015`, `FR-074`, dan `FR-075` digantikan oleh kriteria
berikut:

- **AC-B01**: Paket baru lahir dengan tepat satu Topic bernama `Topik 1`, sehingga soal pertama
  bisa ditulis tanpa membuat Topic lebih dulu.
- **AC-B02**: Question hanya boleh menunjuk Topic yang `paketId`-nya sama dengan `paketId`
  Question itu; kombinasi lain ditolak.
- **AC-B03**: Meminjam soal dari Paket lain menghasilkan Question baru milik Paket tujuan,
  dengan `sourceQuestionId` menunjuk soal asal. Mengubah salinan tidak mengubah soal asal.
- **AC-B04**: Soal yang `sourceQuestionId`-nya sudah ada di Paket tujuan tidak muncul lagi di
  daftar pinjam Paket itu.
- **AC-B05**: Adopsi katalog dilakukan per Paket dan menyalin Paket, seluruh Topic, seluruh
  Question, beserta Option-nya.
- **AC-B06**: Paket yang dibuat dengan nama Subject yang sudah ada menempel ke Subject itu,
  bukan melahirkan Subject kedua bernama sama. Pencocokan mengabaikan besar-kecil huruf dan
  spasi tepi.
- **AC-B07**: Salinan hasil pinjam membawa seluruh Option soal asal — jumlahnya, mana yang
  benar, dan urutannya.
- **AC-B08**: Soal yang masuk ke sebuah Topic mendarat di urutan berikutnya, tidak menumpuk
  di posisi yang sama dengan soal yang sudah ada.
- **AC-B09**: Menghapus lunak Question master menghilangkannya dari ruang kerja dan katalog
  master, tapi tidak menyentuh satu pun Question salinan yang sudah diadopsi Client.
- **AC-B10**: Menarik Paket master dari peredaran tidak menyentuh satu pun Paket atau Question
  salinan yang sudah diadopsi Client; adopsi kedua atas Paket master yang sama tetap diizinkan
  dan melahirkan salinan kedua yang terpisah dari salinan pertama.
- **AC-B11**: Katalog menandai Paket master yang sudah pernah diadopsi Client yang sedang
  melihatnya, dan penanda itu berlaku per Client — Client lain yang belum mengadopsi Paket yang
  sama tidak melihat penanda itu.
- **AC-B12** *(diubah, ADR-0020)*: Menerbitkan Paket master yang masih memuat Question belum
  terbit menawarkan dua jalan, bukan menolak: menerbitkan Question draf itu sekalian, atau
  menerbitkan Paket dengan Question yang sudah terbit saja dan meninggalkan draf tetap draf.
  Pilihan kedua hilang selama Paket belum punya satu pun Question terbit (AC-B16).
- **AC-B14**: Mengadopsi Paket yang salinannya sudah ada di Client itu dihentikan sebelum
  menyalin dan membalas peringatan yang menyebut Paket mana yang sudah pernah diadopsi.
  Mengonfirmasi permintaan yang sama tetap menyalin dan melahirkan salinan kedua yang terpisah.
- **AC-B15**: Menyimpan soal dengan "Simpan & buat lagi" mengembalikan formulir soal baru pada
  Topic yang sama, sehingga soal berikutnya bisa langsung ditulis; menyimpan biasa kembali ke
  halaman isi Paket.
- **AC-B16** *(dipertajam, ADR-0020)*: Paket master tanpa satu pun Question **terbit** ditolak
  terbit — Paket kosong maupun Paket yang seluruh isinya masih draf. Katalog tidak pernah memuat
  Paket yang tidak menghasilkan satu soal pun saat diadopsi.
- **AC-B17**: Menarik atau menghapus Question yang Paket induknya sedang terbit ditolak, dengan
  pesan yang menyuruh menarik Paket itu dari katalog lebih dulu. Keduanya kembali diizinkan
  begitu Paket ditarik. Gerbang penerbitan berlaku dua arah: tanpa ini, Paket terbit bisa
  kehilangan seluruh isi terbitnya — Paket terbit yang tidak menghasilkan satu soal pun saat
  diadopsi, melanggar AC-B16 — dan tetap tampil di katalog sekolah.
- **AC-B18**: Tingkat pertama Bank Soal menampilkan seluruh Paket milik pemiliknya — Client yang
  sedang masuk, atau seluruh Paket master di ruang kerja Eduscreen — lintas Subject sekaligus,
  tanpa Paket milik Client lain ikut tampil. Memilih Subject lewat penyaring `subjectId`
  mempersempit tabel yang sama ke satu Subject, bukan berpindah ke templat atau tingkat
  navigasi lain.
- **AC-B19**: Panel pinjam menawarkan sumber lintas Subject sejak dibuka, termasuk soal dari Paket
  yang Subject-nya belum punya Paket lain — daftar sumber tidak pernah dipersempit ke Subject
  Paket tujuan lebih dulu, dan tetap terisi tanpa menunggu satu Paket diklik/disaring.
- **AC-B20**: Panel pinjam tidak pernah menawarkan Question yang sudah berada di Paket tujuan
  sendiri sebagai sumber pinjam.
- **AC-B21**: Penyaring Subject/Paket/Topic pada panel pinjam saling menyempit: memilih satu
  Subject membuat daftar Paket yang ditawarkan hanya berisi Paket di Subject itu, dan memilih
  satu Paket membuat daftar Topic yang ditawarkan hanya berisi Topic milik Paket itu. Penyaring
  tidak pernah menawarkan pilihan yang pasti menghasilkan nol soal.
- **AC-B22** *(ADR-0020)*: Halaman isi Paket master menyediakan satu tindakan yang menerbitkan
  seluruh Question draf di Paket itu sekaligus, dan tindakan itu hanya muncul selama masih ada
  draf. Hasilnya sama persis dengan menerbitkan tiap Question satu per satu.
- **AC-B23** *(ADR-0020)*: Mengadopsi Paket master hanya menyalin Question yang terbit. Question
  draf yang tersisa di Paket terbit tidak pernah sampai ke Client, dan ringkasan adopsi
  menyebutkan jumlah yang benar-benar disalin.
- **AC-B24**: Topic tujuan pada panel pinjam ditentukan lewat NAMA, dan satu kolom itu melayani
  tiga jalan: nama yang sudah ada di Paket tujuan menempel ke Topic itu (mengabaikan besar-kecil
  huruf dan spasi tepi, sama dengan AC-B06), nama yang belum ada melahirkan Topic baru di Paket
  tujuan, dan kolomnya terisi otomatis dengan nama Topic ASAL soal yang dipinjam selama pengguna
  belum mengisinya sendiri. Isian pengguna tidak pernah ditimpa balik oleh centangan berikutnya.
- **AC-B25**: Pinjam tidak pernah mendarat di Topic yang tidak dipilih. Menekan tombol Salin
  belum menyalin apa pun: ia membuka langkah Topic tujuan di tempat yang sama, dan penyalinan
  baru berjalan setelah langkah itu dikonfirmasi. Kolom Topic tujuan wajib diisi, tidak ada Topic
  yang terpilih diam-diam, dan permintaan pinjam yang tidak menyalin satu soal pun tidak
  melahirkan Topic kosong di Paket tujuan.
- **AC-B26**: Setiap daftar yang aksinya masuk akal dijalankan pada banyak baris sekaligus
  menawarkan kotak centang dan satu bar aksi massal: soal di satu Topic (terbitkan/tarik/hapus di
  master, hapus di Client), Paket master (tarik), pengguna (undang ulang/nonaktifkan), Ruangan
  (arsipkan), anggota Ruangan (keluarkan), dan item Exercise (hapus). Aksi yang butuh masukan per
  baris — ubah, pratinjau, terbitkan Paket dengan dialog drafnya — tidak. Hasil aksi massal sama
  persis dengan menjalankan aksi per baris satu per satu, termasuk gerbangnya (AC-B17): selama
  Paket induknya terbit, aksi massal soal tidak ditawarkan dan permintaannya ditolak.

### Exercise & Assignment

**AC-E01** (BR-E04)
Given Exercise `X` sudah punya satu Assignment
When Guru mencoba menambah Question ke `X`
Then permintaan ditolak dan sistem menawarkan menduplikasi `X` menjadi Exercise baru.

**AC-E02** (BR-E01)
Given Guru sedang merakit Exercise dan sudah menambah 5 Question dari Topic `Aljabar`
When ia berpindah ke Subject `Fisika Kelas 9` Topic `Gerak Lurus` dan menambah 3 Question
Then Exercise memuat 8 Question lintas Subject tanpa peringatan apa pun.

**AC-M01** (BR-M04)
Given Exercise `X` memuat 9 Question `MULTIPLE_CHOICE` dan 1 `ESSAY`
When Guru menerbitkannya sebagai `PRACTICE`
Then publish ditolak, dan pesan galat menyebut Question `ESSAY` mana penyebabnya.

**AC-M02** (BR-M02)
Given Guru memilih Exercise `X` dan tiga Ruangan `4A`, `4B`, `4C`
When ia menerbitkan
Then terbentuk tiga Assignment terpisah, masing-masing menyasar satu Ruangan.

**AC-A01** (BR-A02)
Given Assignment `PUBLISHED` dengan `expiresAt` Minggu 23:59
When Guru mengubahnya menjadi Sabtu 23:59
Then perubahan ditolak; mengubahnya menjadi Senin 23:59 diterima.

**AC-A02** (BR-A03)
Given Assignment `PUBLISHED` dengan Timer 60 menit
When Guru mencoba mengubah Timer menjadi 90 menit
Then permintaan ditolak.

**AC-E03** (BR-E02)
Given Guru A membuat Exercise `Ulangan Harian Aljabar` di Client X
When Guru B di Client X membuka daftar Exercise
Then Exercise itu terlihat, bisa diterbitkan, dan bisa diduplikasi.

**AC-E04** (BR-E03)
Given Exercise tanpa satu pun Question
When Guru menerbitkannya
Then publish ditolak.

**AC-E05**
Given Guru membuka panel penelusuran bank soal di dalam perakit Exercise, dan Paket `A` maupun
Paket `B` sama-sama memuat soal
When ia memilih Paket `A` di panel tanpa mengganti Topic atau kata kunci
Then hasil pencarian hanya memuat soal dari Paket `A`; soal dari Paket `B` tidak ikut tampil
sampai Paket `A` dikosongkan atau diganti. Saringan ini murni tampilan panel — BR-E01 tetap
mengizinkan Guru MENAMBAHKAN soal dari Paket mana pun, terlepas dari Paket yang sedang disaring.

**AC-M03** (BR-M01)
Given Guru ditugaskan di `4A` (`ACTIVE`) dan `3C` (`ARCHIVED`), serta ada Ruangan `4B` yang bukan miliknya
When ia membuka pemilihan Ruangan tujuan
Then hanya `4A` yang tersedia.

**AC-M04** (BR-M03, BR-M05, BR-M06)
Given Guru mengisi formulir publish
When ia menerbitkan `QUIZ` tanpa `timerDurationMinutes`, atau dengan `expiresAt` di masa lalu, atau dengan `maxAttempts = 0`
Then masing-masing ditolak dengan pesan spesifik
And `PRACTICE` tanpa `timerDurationMinutes` diterima.

**AC-M05** (BR-M07)
Given Exercise `X` dengan `lockedAt` kosong
When Guru menerbitkan Assignment pertamanya
Then `lockedAt` terisi pada saat yang sama dengan `publishedAt`.

**AC-A03** (BR-A01)
Given Assignment berstatus `DRAFT`
When Guru mengubah Exercise, mode, Timer, dan sakelar pengacakannya
Then semua perubahan diterima.

**AC-A04** (BR-A04)
Given satu Assignment `DRAFT` dan satu `PUBLISHED`
When Guru menghapus keduanya
Then yang `DRAFT` terhapus dan yang `PUBLISHED` ditolak; untuk yang terakhir sistem menawarkan menutup lebih awal.

**AC-A05** (BR-A05)
Given Assignment `PUBLISHED` dengan 6 Session `IN_PROGRESS`
When Guru menutupnya lebih awal
Then keenam Session menjadi `EXPIRED` dengan `terminalReason = EXPIRATION_REACHED` dan Result-nya terhitung.

### Session & waktu

**AC-S01** (BR-S01)
Given Assignment `PUBLISHED` di Ruangan berisi 30 Siswa
When belum ada satu pun yang menekan Start
Then tidak ada satu pun baris Session di sistem.

**AC-S02** (BR-S03)
Given Assignment dengan `shuffleQuestions = true` dan 40 Question
When Siswa A dan Siswa B masing-masing menekan Start
Then keduanya mendapat urutan Question yang berbeda, dan tiap urutan tetap sama sepanjang Session masing-masing.

**AC-S03** (BR-S02, BR-S06)
Given Siswa mengerjakan 12 dari 40 soal lalu menutup browser
When ia membuka kembali Assignment 5 menit kemudian
Then ia kembali ke Session yang sama, 12 jawaban utuh, urutan soal identik, dan sisa waktu sudah berkurang 5 menit.

**AC-S04** (BR-S07)
Given Assignment `PRACTICE` dan Siswa menjawab soal ke-3
When jawaban terkirim
Then benar/salah dan `explanation` muncul seketika, dan jawaban soal ke-3 tidak bisa diubah lagi.

**AC-T01** (BR-T04)
Given Assignment `expiresAt` pukul 21:00 dengan Timer 60 menit
When Siswa menekan Start pukul 20:50
Then hitung mundur menampilkan 10 menit sejak awal, dan Session berakhir pukul 21:00 dengan `terminalReason = EXPIRATION_REACHED`.

**AC-T02** (BR-T06)
Given tiga Session sudah `EXPIRED` karena `expiresAt` terlewat
When Guru memperpanjang `expiresAt` dua hari
Then tiga Session itu tetap `EXPIRED` dengan Result-nya, sementara Siswa yang belum pernah Start bisa memulai Session baru.

**AC-T03** (BR-T03)
Given Siswa memundurkan jam perangkatnya 30 menit
When ia memuat ulang halaman pengerjaan
Then sisa waktu tetap sesuai perhitungan server dan tidak bertambah.

**AC-T04** (BR-T08)
Given `effectiveDeadline` Session sudah terlewat 2 detik
When jawaban dari Siswa tiba
Then jawaban ditolak dan Session difinalisasi.

**AC-T05** (BR-T07)
Given Session sudah lewat `effectiveDeadline`
When Siswa dan Guru mengaksesnya pada saat bersamaan
Then terbentuk tepat satu Result.

**AC-S05** (BR-S04, BR-S05)
Given Assignment `QUIZ` dengan `maxAttempts = 3` dan Siswa sudah menyelesaikan Attempt 1
When ia memulai Attempt 2
Then terbentuk Session baru dengan Snapshot baru yang urutannya diacak ulang
And setelah Attempt 3 selesai, permintaan Attempt 4 ditolak
And pada Assignment `PRACTICE` permintaan Attempt ke berapa pun tetap diterima.

**AC-S06** (BR-S08)
Given Siswa sedang mengerjakan lalu kehilangan koneksi
When ia mencoba menjawab soal berikutnya
Then indikator koneksi terlihat dan jawaban masuk antrean coba-ulang
And bila koneksi tidak pulih, jawaban itu tidak dianggap tersimpan dan tidak ada salinan lokal yang disinkronkan belakangan.

**AC-T06** (BR-T01, BR-T02)
Given Client dengan `timezone = Asia/Makassar` dan Assignment `expiresAt` Minggu 23:59 waktu Client
When nilai itu disimpan dan ditampilkan
Then penyimpanan berupa UTC Minggu 15:59, dan seluruh tampilan bagi Guru maupun Siswa menunjukkan Minggu 23:59 tanpa memandang zona perangkat mereka.

**AC-T07** (BR-T05)
Given Assignment `PRACTICE` tanpa `timerDurationMinutes`, `expiresAt` Minggu 23:59
When Siswa menekan Start Jumat pukul 08:00
Then `effectiveDeadline` Session bernilai Minggu 23:59 dan tidak ada hitung mundur durasi yang ditampilkan.

**AC-T08** (BR-T09)
Given Result lama dengan skor 0,8 sudah tersimpan
When aturan skoring diubah di rilis berikutnya
Then skor Result lama itu tetap 0,8 saat dibaca kembali.

### Laporan & skor

**AC-L01** (BR-L01, BR-L02)
Given Ruangan berisi 30 Siswa, 22 mengerjakan, 8 tidak pernah Start, dan `expiresAt` sudah terlewat
When Guru membuka laporan Assignment
Then laporan menampilkan 30 baris; 8 di antaranya `NOT_STARTED` dengan skor 0 dan tanpa baris Session; Session terbengkalai sudah difinalisasi beserta Result-nya.

**AC-L02** (BR-L03, BR-C07)
Given `maxAttempts = 3` dan seorang Siswa memperoleh 60, 85, lalu 70
When Guru membuka laporan
Then skor resmi Siswa itu 85, dan ketiga Attempt tetap bisa dibuka.

**AC-C01** (BR-C06)
Given Session berisi 40 soal, Siswa menjawab 25, Timer habis
When Session difinalisasi
Then `unansweredCount = 15`, 15 soal itu dihitung salah, dan `score` dihitung dari 40 soal.

**AC-C02** (BR-C04, BR-C05, BR-C10, BR-R02)
Given Session Quiz berisi 9 MCQ (8 benar) dan 1 essay
When Session selesai tetapi essay belum dinilai
Then Result berstatus `PENDING_REVIEW` dengan skor sementara 0,8 bertanda sementara
And ketika Guru memberi `essayScore = 75`, Result menjadi `FINAL` dengan skor `(8 + 0,75) ÷ 10 = 0,875`.

**AC-C03** (BR-L04)
Given seorang Siswa mengerjakan 4 Assignment `PRACTICE` dan 1 `QUIZ`
When Guru membuka rekap nilai Ruangan
Then hanya Result Quiz yang muncul, dan aktivitas Practice tampil di laporan latihan terpisah.

**AC-C04** (BR-C01, BR-C02, BR-C03)
Given Session Quiz berisi 10 Question `MULTIPLE_CHOICE`; Siswa menjawab 6 benar, 2 salah, 2 dibiarkan kosong
When Session difinalisasi
Then `score` bernilai 0,6; soal salah dan soal kosong sama-sama menyumbang 0 poin, dan tidak ada pengurangan nilai.

**AC-C05** (BR-C08, BR-C09, BR-R01)
Given Assignment `PRACTICE` diselesaikan seorang Siswa
When Result terbentuk
Then Result ber-`kind = PRACTICE`, berstatus `FINAL` seketika tanpa melewati `PENDING_REVIEW`, dan tidak muncul di rekap nilai Ruangan.

**AC-G01** (BR-G01)
Given Guru B tidak ditugaskan di Ruangan tempat Assignment diterbitkan
When ia mencoba menilai essay di Assignment itu
Then permintaan ditolak.

**AC-G02** (BR-G02, BR-G03)
Given Result sudah `FINAL` dengan `essayScore = 75`
When Guru mengubahnya menjadi 90
Then Result dihitung ulang seketika dan skornya naik sesuai rumus
And jejak audit mencatat siapa yang mengubah, kapan, serta nilai 75 → 90, dan jejak itu tidak bisa diubah maupun dihapus.

### Akun & Ruangan

**AC-U01** (BR-U01)
Given seorang Siswa terdaftar di `Kelas 4B` dan `Bimbel Intensif SBMPTN Group B`
When ia membuka dashboard
Then Assignment aktif dari kedua Ruangan tampil dalam satu daftar dengan penanda Ruangannya.

**AC-U02** (BR-U02)
Given Ruangan `Kelas 4B 2025/2026` berstatus `ARCHIVED`
When Guru mencoba menerbitkan Assignment ke sana
Then Ruangan itu tidak muncul sebagai tujuan, sementara laporan dan Result lamanya tetap terbaca.

**AC-U04** (BR-U04)
Given Client Admin membuat akun Guru baru
When akun tersimpan
Then undangan terkirim ke alamat emailnya dan Guru bisa menetapkan password sendiri lewat tautan itu
And Guru yang lupa password bisa memulihkannya sendiri lewat email tanpa menghubungi Client Admin.

**AC-U03** (BR-U03)
Given seorang Siswa dengan 12 Result dinonaktifkan Client Admin
When Guru membuka laporan Assignment lama
Then Result Siswa itu tetap muncul lengkap, dan Siswa itu tidak lagi bisa login.

---
