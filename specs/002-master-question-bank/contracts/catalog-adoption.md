# Contract: Katalog Granular & Adopsi

**Cerita**: US4, US5 | **Kebutuhan**: FR-074 sampai FR-082

Menggantikan bagian "Konten master & onboarding" pada
[`001/contracts/content-authoring.md`](../../001-student-exercise-portal/contracts/content-authoring.md)
untuk dua rute katalog. Rute `/eduscreen/client` (onboarding) tidak berubah bentuknya; yang berubah
hanyalah paket mana yang boleh muncul di pilihannya.

## Katalog

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/katalog` | Client Admin | `subjectId`, `topicId`, `q`, `page` | `page` daftar Question master terbit berhalaman + daftar paket terbit | — |
| GET | `/katalog/soal` | Client Admin | `subjectId`, `topicId`, `q`, `page` | `fragment` hasil berhalaman | — |
| POST | `/katalog/adopsi` | Client Admin | `questionIds[]` atau `exerciseIds[]` | `fragment` ringkasan salinan | `400`, `404` |

**Aturan mengikat**

- Katalog MUST menampilkan Question master satu per satu dengan filter Subject dan Topic serta
  pencarian pada isi soal, berdampingan dengan daftar paket (FR-074). Menampilkan paket saja tidak
  memenuhi kontrak ini.
- Hanya konten master **terbit** yang MUST muncul; konten yang masih digarap MUST NOT terlihat dan
  MUST NOT bisa diadopsi lewat jalur apa pun, termasuk dengan menebak pengenalnya (FR-067).
- Setiap baris hasil MUST membawa penanda apakah Client yang sedang melihat sudah pernah
  mengadopsinya (FR-076). Penanda dibaca dari jejak adopsi yang sudah tersimpan, dibatasi pada
  pengenal yang tampil di halaman itu.
- Adopsi berulang MUST tetap diizinkan, tetapi MUST didahului peringatan yang menyebut salinan
  sebelumnya sudah ada (FR-077).
- Adopsi MUST membuat **salinan penuh** milik Client; perubahan pada master setelahnya MUST NOT
  merambat (FR-078, FR-021, ADR-0001).
- Subject `GLOBAL` MUST NOT disalin — ia dibaca langsung; yang disalin adalah Topic, Question, dan
  Exercise (BR-O02, AC-O02).
- Topic hasil adopsi MUST membawa `sourceTopicId` yang menunjuk Topic master asalnya, sejajar
  `sourceQuestionId` pada Question (ADR-0001). Pengenalannya MUST lewat jejak itu, MUST NOT lewat
  kesamaan nama: nama berubah di kedua sisi — Eduscreen me-rename master, Guru merapikan salinan.
- Menyaring katalog ke Topic yang sudah pernah diadopsi Client yang sedang melihat MUST
  memunculkan peringatan; adopsi keduanya MUST tetap diizinkan dan MUST melahirkan Topic baru
  dengan pengenal sendiri, bukan memakai ulang yang lama (FR-076, FR-077).
- Ringkasan MUST menyebut jumlah Question, Topic, dan Exercise yang tersalin (FR-079).
- Adopsi MUST diproses sinkron; MUST NOT ada antrean pekerjaan latar (TC-45, ADR-0014).
- Rute `/katalog/**` MUST tetap tertutup bagi Guru dan Siswa (FR-081). Guru meracik Exercise dari
  Question yang sudah ada di Client-nya.

## Onboarding — yang berubah

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| POST | `/eduscreen/client` | Eduscreen Admin | `name`, `timezone`, `adminEmail`, `adminFullName`, `paketIds[]` | `302` ke detail Client | `400`, `404` paket belum terbit |

**Aturan mengikat**

- Pilihan paket saat onboarding MUST hanya memuat paket master **terbit** (FR-067). Bentuk masukan
  dan alur onboarding lainnya tidak berubah dari FR-020.

## Batas kewenangan yang wajib dibuktikan tes (TC-41)

| Pemanggil | Sasaran | Harus |
| --- | --- | --- |
| Guru, Siswa | `/eduscreen/**`, `/katalog/**` | ditolak |
| Client Admin | `/eduscreen/**` | ditolak |
| Eduscreen Admin | Question atau Exercise milik sebuah Client | `404` |
| Client Admin Client X | Question master belum terbit | `404` |
| Client Admin Client X | salinan hasil adopsi milik Client Y | `404` |
