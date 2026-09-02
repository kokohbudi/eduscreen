# Contract: Katalog Granular & Adopsi

**Cerita**: US4, US5 | **Kebutuhan**: FR-074 sampai FR-082

Menggantikan bagian "Konten master & onboarding" pada
[`001/contracts/content-authoring.md`](../../001-student-exercise-portal/contracts/content-authoring.md)
untuk dua rute katalog.

> **Catatan (ADR-0018, Task 8/11)**: kontrak ini semula ditulis untuk katalog granular per
> Question, sejajar daftar paket (Exercise ber-`clientId` null). ADR-0018 menggeser satuan
> katalog dan adopsi seluruhnya menjadi Paket — Question tidak lagi ditampilkan atau diadopsi
> satu per satu, dan Exercise tidak pernah jadi objek adopsi. Isi di bawah sudah ditulis ulang
> mengikuti keadaan itu; baris yang menyebut Question/Exercise sebagai satuan katalog di versi
> lama sudah tidak berlaku.

## Katalog

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/katalog` | Client Admin | `subjectId` | `page` daftar Subject + daftar Paket master terbit di Subject terpilih | — |
| POST | `/katalog/adopsi` | Client Admin | `paketIds[]`, `confirm` (opsional) | `fragment` ringkasan salinan, atau `fragment` peringatan adopsi berulang | `400`, `404` |

**Aturan mengikat**

- Katalog MUST menampilkan Paket master, tersaring per Subject (FR-074). Paket adalah
  satu-satunya satuan yang ditampilkan dan diadopsi — tidak ada lagi daftar Question terpisah.
- Hanya Paket master **terbit** yang MUST muncul; Paket yang masih digarap MUST NOT terlihat dan
  MUST NOT bisa diadopsi lewat jalur apa pun, termasuk dengan menebak pengenalnya (FR-067). Paket
  terbit MUST NOT memuat Question yang belum terbit — gerbangnya ada di penerbitan Paket, bukan
  di penyalinan (FR-067, FR-069 setara): menyaring saat menyalin akan menghasilkan salinan yang
  diam-diam tidak lengkap tanpa sekolah pernah tahu.
- Setiap Paket yang tampil MUST membawa penanda apakah Client yang sedang melihat sudah pernah
  mengadopsinya (FR-076). Penanda dibaca dari `sourcePaketId` yang sudah tersimpan sejak adopsi
  pertama, dibatasi pada pengenal yang tampil di halaman itu.
- Adopsi berulang MUST tetap diizinkan, tetapi MUST dihentikan sebelum menyalin dan membalas
  peringatan yang menyebut Paket mana yang sudah pernah diadopsi (FR-077). Peringatan ini
  ditegakkan di server, bukan `confirm()` di klien: permintaan tanpa penanda konfirmasi berhenti
  di peringatan; permintaan yang sama disertai penanda konfirmasi (`confirm=true`) tetap menyalin
  dan melahirkan salinan kedua yang terpisah dari salinan pertama.
- Adopsi MUST membuat **salinan penuh** milik Client — Paket beserta seluruh Topic, Question, dan
  Option di dalamnya; perubahan pada master setelahnya MUST NOT merambat (FR-078, FR-021,
  ADR-0001).
- Subject `GLOBAL` MUST NOT disalin — ia dibaca langsung; yang disalin adalah Paket, Topic,
  Question, dan Option (BR-O02, AC-O02). Exercise MUST NOT pernah jadi objek adopsi — Exercise
  milik alur Guru, dirakit dari Question yang sudah ada di bank soal Client-nya sendiri, termasuk
  Question hasil adopsi.
- Paket hasil adopsi MUST membawa `sourcePaketId` yang menunjuk Paket master asalnya (ADR-0001).
  Pengenalan MUST lewat jejak itu, MUST NOT lewat kesamaan judul: judul bisa berubah di kedua
  sisi — Eduscreen me-rename master, Client Admin merapikan salinan.
- Ringkasan MUST menyebut jumlah Paket, Topic, dan Question yang tersalin (FR-079).
- Adopsi MUST diproses sinkron; MUST NOT ada antrean pekerjaan latar (TC-45, ADR-0014).
- Rute `/katalog/**` MUST tetap tertutup bagi Guru dan Siswa (FR-081). Guru meracik Exercise dari
  Question yang sudah ada di Client-nya, termasuk hasil adopsi.

## Onboarding — yang berubah

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| POST | `/eduscreen/client` | Eduscreen Admin | `name`, `timezone`, `adminEmail`, `adminFullName`, `paketIds[]` | `302` ke detail Client | `400`, `404` paket belum terbit |

**Aturan mengikat**

- Pilihan Paket saat onboarding MUST hanya memuat Paket master **terbit**, lintas Subject
  (FR-067). Bentuk masukan dan alur onboarding lainnya tidak berubah dari FR-020.
- Onboarding memanggil jalur adopsi yang sama dengan katalog (satu gerbang FR-067, bukan dua) —
  ia MUST bukan pintu belakang yang melewati penerbitan Paket.

## Batas kewenangan yang wajib dibuktikan tes (TC-41)

| Pemanggil | Sasaran | Harus |
| --- | --- | --- |
| Guru, Siswa | `/eduscreen/**`, `/katalog/**` | ditolak |
| Client Admin | `/eduscreen/**` | ditolak |
| Eduscreen Admin | Question, Paket, atau Exercise milik sebuah Client | `404` |
| Client Admin Client X | Paket master belum terbit | `404` |
| Client Admin Client X | salinan hasil adopsi milik Client Y | `404` |
