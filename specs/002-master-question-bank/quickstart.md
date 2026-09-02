# Quickstart & Validation Guide

**Date**: 2026-08-31 | **Plan**: [plan.md](./plan.md)

Panduan **membuktikan** fitur ini bekerja ujung ke ujung. Perubahan skema ada di
[data-model.md](./data-model.md); permukaan rute di [contracts/](./contracts/README.md).

Prasyarat, cara menjalankan aplikasi, dan akun profil `local` tidak diulang di sini — semuanya sama
persis dengan [001/quickstart.md](../001-student-exercise-portal/quickstart.md). Yang dibutuhkan
tambahan hanyalah satu akun yang sudah ada di seed: `admin@eduscreen.id` (peran `EDUSCREEN_ADMIN`).

## Menjalankan tes fitur ini

```bash
./mvnw test -Dtest=MasterContentIT             # US1, US2, US3 — authoring & penerbitan
./mvnw test -Dtest=CatalogAdoptionIT           # US4, US5 — katalog granular & adopsi
./mvnw test -Dtest=ContentIdorTest             # batas /eduscreen/** dua arah (TC-41)
./mvnw test -Dtest=ArchUnitRulesTest           # memastikan tidak ada port/adapter baru (TC-01, TC-40)
```

Migrasi baru diverifikasi ikut jalan:

```bash
./mvnw test -Dtest=PostgresSmokeTest
```

## Validasi manual — jalur lengkap hulu ke hilir

Satu putaran ini menyentuh kelima cerita pengguna. Jalankan berurutan.

### 1. Eduscreen Admin menulis Question master (US1)

Masuk sebagai `admin@eduscreen.id`.

1. Buka ruang kerja konten master dari portal Eduscreen.
2. Buat Subject `GLOBAL` bila belum ada, lalu buat Topic di bawahnya.
3. Tulis satu Question pilihan ganda (4 pilihan, 1 kunci) dan satu Question essay berpembahasan.
4. Cari kata yang ada di dalam batang soal.

**Diharapkan**: kedua Question tersimpan dan ditemukan lewat pencarian, keduanya bertanda **belum
terbit**.

**Bukti negatif yang wajib dicek**: coba simpan Question pilihan ganda dengan dua kunci benar —
harus ditolak dengan pesan yang menyebut aturan tepat satu kunci, bukan galat internal.

### 2. Katalog masih kosong (US2, sisi negatif)

Masuk sebagai `admin@contoh.sch.id` (Client Admin), buka katalog.

**Diharapkan**: kedua Question tadi **tidak muncul** dan tidak bisa diadopsi. Ini pembuktian FR-067
dan SC-013; kalau ia muncul, seluruh siklus terbit tidak bermakna.

### 3. Menerbitkan dan gerbang paket (US2, US3)

Kembali sebagai Eduscreen Admin.

1. Rakit satu paket master berisi kedua Question, ditambah satu Question dari Topic lain.
2. Coba terbitkan paket itu **sebelum** menerbitkan isinya.
3. Terbitkan seluruh Question, lalu terbitkan paketnya.

**Diharapkan**: langkah 2 ditolak `400` dengan pesan yang **menyebut Question penyebabnya**
(FR-069); langkah 3 berhasil.

### 4. Katalog granular dan adopsi (US4)

Masuk sebagai Client Admin, buka katalog.

1. Saring per Subject, lalu per Topic, lalu cari satu kata.
2. Centang dua Question, adopsi.
3. Muat ulang katalog.

**Diharapkan**: hasil tampil **per Question**, bukan hanya daftar paket (FR-074); ringkasan
menyebut jumlah Question dan Topic yang tersalin (FR-079); setelah dimuat ulang, kedua Question
bertanda **sudah diadopsi** (FR-076). Tidak ada Subject baru dibuat untuk sekolah itu (AC-O02).

### 5. Guru memakai hasil adopsi (US5)

Masuk sebagai `guru@contoh.sch.id`.

**Diharapkan**: kedua Question hasil adopsi terlihat di bank soal sekolah berdampingan dengan soal
buatan sendiri, dan bisa langsung ditambahkan ke Exercise tanpa langkah tambahan apa pun (FR-024,
SC-018).

### 6. Isolasi setelah adopsi (US2, inti ADR-0001)

Kembali sebagai Eduscreen Admin.

1. Ubah redaksi salah satu Question master yang sudah diadopsi.
2. Tarik Question yang satunya dari peredaran.

Lalu kembali sebagai Guru.

**Diharapkan**: kedua salinan milik sekolah **tidak berubah sama sekali** — redaksinya tetap yang
lama dan yang ditarik tetap ada dan tetap bisa dipakai (FR-068, FR-070, SC-014). Ini kriteria yang
paling mudah rusak diam-diam dan paling mahal ketika rusak.

### 7. Batas kewenangan (FR-080, FR-081)

| Coba | Sebagai | Diharapkan |
| --- | --- | --- |
| Buka rute `/eduscreen/**` | Guru, Siswa, Client Admin | ditolak |
| Buka rute `/katalog` | Guru, Siswa | ditolak |
| Buka Question milik sebuah Client lewat rute master | Eduscreen Admin | `404`, tak terbedakan dari yang tidak ada |
| Adopsi Question master yang belum terbit dengan menebak pengenalnya | Client Admin | `404` |

## Onboarding dengan paket terbit

```text
POST /eduscreen/client  dengan paketIds[] berisi paket terbit dari langkah 3
```

**Diharapkan**: Client baru berdiri dengan seluruh Question paket itu sudah ada di Question Bank-nya
pada login pertama Client Admin (SC-016), dan paket yang belum terbit tidak muncul sebagai pilihan.

## Yang tidak perlu divalidasi ulang

Perilaku berikut tidak disentuh fitur ini dan sudah dijamin tes spesifikasi 001: perakitan Exercise
oleh Guru, penerbitan Assignment, pengerjaan Siswa, penilaian, dan rekap. Bila salah satunya rusak
setelah perubahan ini, tersangka pertamanya adalah perubahan tanda tangan
`QuestionService.create/update`. Satu-satunya pemanggilnya hari ini adalah `QuestionBankController`
(baris 129 dan 158); impor massal menulis lewat jalurnya sendiri dan tidak ikut terpengaruh.
