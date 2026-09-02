# Desain: Bank Soal — Subject › Paket › Topic › Question

**Tanggal**: 2026-09-02
**Status**: Disetujui untuk masuk tahap rencana implementasi

## Ringkasan

Satu menu bernama **Bank Soal** menggantikan layar bank soal dan ruang kerja konten master yang
ada sekarang. Strukturnya:

```
Subject (label)
  └── Paket
        └── Topic
              └── Question
```

Paket adalah wadah baru yang menggantikan peran Topic global sebagai tempat soal hidup. Topic
tidak lagi global — ia hidup di dalam satu Paket. Question menempel pada Topic milik paketnya
sendiri.

**Exercise tidak berubah.** Bank Soal ada di level admin; Exercise tetap di level Guru.

```
ADMIN   Subject › Paket › Topic › Question     bank soal: ditulis, diedit, diadopsi
GURU    Exercise                               rakitan soal, diterbitkan jadi Assignment
```

Pembagian ini sudah ada di `CONTEXT.md`: Client Admin mengelola Question Bank internal, Guru
meracik Exercise dan menerbitkannya sebagai Assignment.

## Masalah pada model sekarang

1. **Taksonomi global memaksa klasifikasi sebelum menulis.** `Question.topic_id` tidak boleh
   null, dan Topic harus dipilih dari daftar global. Guru tidak bisa menulis soal sebelum
   memutuskan Subject dan Topic.
2. **Jumlah Subject membengkak.** `CONTEXT.md` mendefinisikan Subject sebagai mata pelajaran
   pada satu jenjang (`Matematika Kelas 4`). Jumlahnya = mapel × jenjang.
3. **Topic berlipat.** `Aljabar Dasar` harus dibuat ulang di bawah tiap Subject berjenjang,
   jadi baris-baris terpisah yang tidak saling kenal.
4. **Adopsi per soal terlalu halus.** `POST /katalog/adopsi` dengan `questionIds[]` memaksa
   Client Admin mencentang soal satu per satu.
5. **Tidak ada wadah yang bisa dijual.** Eduscreen menjual paket latihan, tapi model sekarang
   hanya punya Topic global dan Exercise. Exercise dipakai sebagai paket master, padahal ia
   milik alur Guru.

## Model baru

| Entitas | Kolom | Catatan |
| --- | --- | --- |
| **Subject** | `id`, `name`, `origin` (`GLOBAL`/`CLIENT`), `clientId` | Tidak berubah. Perannya jadi label pada Paket, bukan akar taksonomi |
| **Paket** | `id`, `clientId` (null = milik Eduscreen), `title`, `subjectId`, `publishedAt`, `sourcePaketId` | Entitas baru |
| **Topic** | `id`, `paketId`, `title`, `position` | Milik satu Paket. Kolom `subjectId` dan `origin` dibuang |
| **Question** | `id`, `clientId`, `paketId`, `topicId`, `position`, `type`, `bodyHtml`, `bodyText`, `explanationHtml`, `explanationText`, `sourceQuestionId` | `topicId` sekarang menunjuk Topic milik Paket yang sama |
| **Option** | tidak berubah | |
| **Exercise**, **ExerciseItem** | tidak berubah | Tetap milik alur Guru |

### Aturan kepemilikan

- Question hidup di tepat satu Paket, lewat tepat satu Topic. Topic hidup di tepat satu Paket.
- `Question.topicId` harus menunjuk Topic yang `paketId`-nya sama dengan `Question.paketId`.
  Divalidasi saat menulis.
- Paket ber-`clientId` null adalah paket master milik Eduscreen. Selebihnya sama persis dengan
  paket milik Client. Satu bentuk, dua pemilik — seperti pola `basePath` yang dipakai sekarang.
- Paket tidak punya `lockedAt`. Kunci tetap milik Exercise (BR-E04, FR-026), karena Exercise
  yang ditugaskan ke Siswa, bukan Paket.

### Salin penuh di dalam bank soal

Ada dua cara konten berpindah di level admin, keduanya memakai aturan sama: **salinan penuh,
baris baru**.

1. **Pinjam soal dari Paket lain** dalam satu Client. Menghasilkan `Question` baru di Paket
   tujuan, dengan `sourceQuestionId` menunjuk soal asal.
2. **Adopsi Paket dari katalog**. Menghasilkan `Paket` + `Topic` + `Question` + `Option` baru
   milik Client, dengan `sourcePaketId` dan `sourceQuestionId` terisi.

Konsekuensi yang diterima: perubahan pada soal asal tidak merambat ke salinan. Aturan yang sama
dengan ADR-0001, sekarang berlaku seragam untuk pinjam maupun adopsi.

Saat meminjam, soal yang `sourceQuestionId`-nya sudah ada di Paket tujuan disembunyikan dari
daftar pilihan, supaya tidak tersalin dua kali.

**Exercise memakai aturan berbeda dan tidak berubah**: `ExerciseItem` menunjuk `questionId`,
tidak menyalin. Guru merakit, tidak mengedit. Session yang sedang berjalan tetap aman karena
sudah dilindungi Snapshot.

## Layar dan rute

### Level admin — Bank Soal

Peran: Client Admin dan Guru, sama seperti `/soal` sekarang.

| Metode | Jalur | Keluaran |
| --- | --- | --- |
| GET | `/bank-soal` | Tabel Subject, dengan jumlah Paket per Subject |
| GET | `/bank-soal?subjectId={id}` | Tabel Paket di Subject itu, dengan jumlah soal per Paket |
| POST | `/bank-soal/paket` | Buat Paket: `title`, `subjectId` atau `subjectName` |
| GET | `/bank-soal/paket/{id}` | Isi Paket: soal dikelompokkan per Topic |
| POST | `/bank-soal/paket/{id}/topic` | Tambah Topic |
| PUT | `/bank-soal/paket/{id}/urutan` | Urutkan ulang soal dan Topic |
| GET | `/bank-soal/paket/{id}/soal/baru` | Editor soal baru, `topicId` sebagai induk |
| POST | `/bank-soal/paket/{id}/soal` | Simpan soal |
| PUT | `/bank-soal/soal/{id}` | Ubah soal |
| DELETE | `/bank-soal/soal/{id}` | Hapus soal |
| GET | `/bank-soal/paket/{id}/pinjam` | Panel pinjam: cari soal di Paket lain |
| POST | `/bank-soal/paket/{id}/pinjam` | Salin `questionIds[]` atau seluruh `sourceTopicId` |

Ruang kerja Eduscreen memakai rute kembar berawalan `/eduscreen/bank-soal`, dengan template
yang sama dan `basePath` berbeda — pola yang sudah dipakai `MasterContentController` sekarang.
Tombol terbit dan tarik hanya muncul di ruang kerja master (FR-066, FR-067).

Perilaku layar:

- **Kolom Subject saat membuat Paket** memakai autocomplete. Nama yang cocok menempel ke Subject
  yang ada; nama yang belum ada membuat Subject baru saat Paket disimpan. Tidak ada formulir
  "tambah Subject" terpisah.
- **Paket baru lahir dengan satu Topic bernama `Topik 1`.** Penulis bisa langsung menulis soal,
  dan tidak ada `topicId` null yang harus ditangani di query, adopsi, maupun perakit.
- **Pinjam bisa per soal atau per Topic.** "Ambil seluruh Topic X dari Paket Y" menyalin semua
  soal Topic itu ke Topic yang sedang terbuka.
- Pencarian soal tetap menyentuh `bodyText`, bukan kolom HTML (FR-019, TC-25).

### Level Guru — Exercise

Rute `/exercise` tidak berubah. Yang berubah hanya panel penelusuran di perakit: penyaringnya
sekarang **Paket › Topic**, bukan Subject › Topic. Tindakan massal `POST /exercise/{id}/item/topik`
tetap ada, artinya jadi "tambahkan semua soal dari satu Topic di dalam satu Paket".

Menambahkan soal ke Exercise tetap membuat `ExerciseItem` yang menunjuk `questionId`.

### Katalog

`/katalog` menampilkan Paket master yang sudah terbit, disaring per Subject. Adopsi dilakukan
per Paket. Bagian "adopsi per soal" dihapus.

## Aturan mengikat

Menggantikan atau menyesuaikan aturan yang ada:

- **Menggantikan FR-013, FR-014, FR-015.** Question melekat pada tepat satu Topic; Topic
  melekat pada tepat satu Paket; Paket melekat pada tepat satu Subject.
- **Menggantikan FR-074 dan FR-075.** Adopsi dilakukan per Paket, bukan per soal. Pemilihan
  halus terjadi setelahnya: di bank soal lewat pinjam antar-Paket, dan di alur Guru lewat
  perakitan Exercise.
- **Menyesuaikan FR-024 dan BR-E01.** Exercise tetap boleh memuat soal dari Paket dan Topic
  mana pun di dalam Client. Kata "Subject dan Topic" pada rumusan lama menjadi "Paket dan
  Topic".
- **Tetap berlaku tanpa perubahan**: FR-016 (pilihan ganda ≥2 opsi, tepat 1 benar), FR-018
  (hapus soal tidak memutus Exercise, Assignment, atau Session yang sudah berjalan), FR-019 dan
  TC-25 (pencarian di kolom teks polos), FR-021 dan ADR-0001 (adopsi = salinan penuh), FR-025
  (Exercise kosong tidak boleh diterbitkan), FR-026 dan BR-E04 (Exercise ber-`lockedAt` menolak
  perubahan dengan 409 dan menawarkan duplikasi), BR-M04 (Practice hanya pilihan ganda), FR-066
  dan FR-067 (status terbit hanya milik konten master), TC-22 sampai TC-28 (sanitasi, LaTeX,
  gambar).
- **Menyesuaikan BR-O02.** Subject `GLOBAL` tetap tidak pernah disalin; Paket hasil adopsi
  menunjuk Subject global yang sama. Yang berubah: Topic tidak lagi punya `origin`, karena
  Topic sekarang selalu milik Paket, dan Paket yang punya pemilik.
- **Menyesuaikan TC-36.** Aturan "Topic yang bukan milik Client pemanggil menghasilkan 0 soal,
  bukan kebocoran maupun galat yang membedakannya dari Topic kosong" sekarang berlaku pada
  Paket.

## Isolasi antar-Client

- Setiap query Paket, Topic, dan Question disaring `clientId` di query utama, bukan di kode
  pemanggil.
- Katalog hanya menampilkan Paket `clientId IS NULL` yang sudah `publishedAt`.
- Meminjam soal dari Paket milik Client lain menghasilkan nol hasil, bukan galat yang
  membedakannya dari Paket kosong.

## Migrasi data

Dijalankan sekali, satu migration:

1. **Tiap Topic lama menjadi satu Paket baru.** Judul Paket = nama Topic. `subjectId` = Subject
   induk Topic itu. `clientId` = pemilik Topic (`null` untuk Topic `GLOBAL`). Di dalam Paket itu
   dibuat satu Topic bernama sama, dan semua Question milik Topic lama dipindah ke sana dengan
   `position` menurut `created_at`.
2. **`Question.paket_id` diisi** menunjuk Paket hasil langkah 1. `Question.topic_id` diarahkan
   ke Topic baru di dalam Paket itu.
3. **Exercise dan ExerciseItem tidak disentuh.** `ExerciseItem.question_id` tetap menunjuk soal
   yang sama, yang sekarang tinggal di dalam Paket.
4. Kolom yang ditambah: `paket` (tabel baru), `question.paket_id`, `question.position`,
   `topic.paket_id`, `topic.position`. Kolom yang dibuang setelah langkah 1–3 selesai:
   `topic.subject_id`, `topic.origin`.

`Subject` tidak dimigrasi. Semantiknya tetap seperti di `CONTEXT.md` sekarang — mata pelajaran
pada satu jenjang.

Paket master hari ini adalah Exercise ber-`clientId` null. Setelah migrasi, Exercise itu tetap
ada sebagai Exercise, tapi bukan lagi yang dijual: katalog beralih ke Paket. Konversinya
dilakukan Eduscreen Admin secara manual, karena jumlahnya sedikit dan penamaannya perlu
keputusan editorial.

## Dampak ke dokumen lain

- **`CONTEXT.md`**: tambah entri `Paket` (wadah soal milik Eduscreen atau Client, dijual dan
  diadopsi). Entri `Topic` diubah: sub-bahasan di dalam satu Paket. Entri `Subject` diubah:
  label pada Paket. Entri `Exercise` tidak berubah.
- **`specs/001-student-exercise-portal/contracts/content-authoring.md`**: tabel Taksonomi dan
  Bank soal ditulis ulang; tabel Exercise hanya berubah pada penyaring panel penelusuran.
- **`specs/001-student-exercise-portal/business-rules.md`**: BR-E01, BR-O02 disesuaikan.
- **ADR baru**: keputusan Topic diturunkan dari taksonomi global menjadi milik Paket, dan
  keputusan pinjam antar-Paket memakai salinan penuh sementara Exercise memakai referensi.

## Yang dihapus

- Topic global dan `TaxonomyService.createGlobalTopic`.
- Cascading Subject → Topic lewat HTMX di `soal/daftar.html` dan `soal/editor.html`.
- Adopsi per soal (`questionIds[]` pada `POST /katalog/adopsi`).
- Rute `/soal` dan `/eduscreen/soal` beserta templatnya, diganti `/bank-soal`.

## Di luar lingkup

- Memecah Subject menjadi mapel dan jenjang terpisah. Subject tetap seperti sekarang.
- Tingkat kesulitan, tag bebas, atau faset tambahan pada soal.
- Peringatan ketidakcocokan jenjang antara Paket dan Ruangan saat menerbitkan Assignment.
- Impor massal. Rutenya tetap, tapi tujuan barisnya berubah dari Topic global ke Paket, dan itu
  ditangani sebagai pekerjaan terpisah.
