# Phase 0: Keputusan Desain — Question Bank Master Eduscreen

**Feature**: `002-master-question-bank` | **Date**: 2026-08-31

Tidak ada `NEEDS CLARIFICATION` yang perlu diselesaikan. Stack, gaya arsitektur, dan aturan tenancy
sudah terkunci di `CONSTITUTION.md` (`TC-01`..`TC-49`) dan 17 keputusan di `docs/adr/`; empat
keputusan lingkup terbesar sudah dijawab saat penyusunan spesifikasi. Yang tersisa adalah enam
keputusan desain yang murni lahir di rencana ini.

---

## D1 — Kepemilikan konten menjadi parameter, bukan disimpulkan dari principal

**Keputusan.** `QuestionService.create` dan `update` berhenti menerima `UserPrincipal` dan mulai
menerima `UUID clientId`, yang bernilai `null` untuk konten master. Controller yang memanggilnya
memutuskan nilainya: `QuestionBankController` mengirim `principal.requireClientId()`,
`MasterContentController` mengirim `null`.

**Alasan.** Inilah satu-satunya hal yang benar-benar menghalangi Eduscreen Admin menulis konten
master hari ini — `author.requireClientId()` melempar `IllegalStateException` untuk pengguna tanpa
Client. Seluruh method lain di `QuestionService`, `ExerciseService`, dan `ContentAdoptionService`
sudah menerima `UUID clientId` sebagai parameter eksplisit sesuai TC-36. Dua method ini adalah yang
tertinggal, bukan pengecualian yang disengaja.

**Alternatif yang ditolak.**

- *Menambahkan `QuestionService.createMaster` di samping `create`.* Menggandakan validasi Option,
  sanitasi, dan penyimpanan Option hanya untuk membedakan satu nilai. Dua jalur tulis yang harus
  tetap sinkron adalah persis bentuk yang membuat aturan diam-diam melenceng.
- *Membuat `UserPrincipal.contentOwnerId()` yang mengembalikan null untuk Eduscreen Admin.*
  Menyembunyikan keputusan kepemilikan di dalam objek principal, bertentangan dengan semangat TC-36
  yang menuntut kepemilikan terlihat di tanda tangan.

---

## D2 — Keadaan terbit adalah satu kolom nullable, bukan mesin status

**Keputusan.** `question.published_at` dan `exercise.published_at`, bertipe `timestamptz` dan
nullable. Terisi berarti terbit; kosong berarti masih digarap. Menarik dari peredaran berarti
mengosongkannya kembali.

**Alasan.** FR-066 hanya menuntut dua keadaan. Kolom waktu memberi keduanya sekaligus mencatat kapan
penerbitan terjadi, tanpa tabel audit tambahan. Pola ini sudah dipakai berulang di repo — `locked_at`,
`deleted_at`, `closed_at`, `finalized_at` — sehingga tidak memperkenalkan idiom baru.

**Alternatif yang ditolak.**

- *Enum `MasterContentStatus { DRAFT, PUBLISHED }`.* Menambah tipe, kolom `text`, dan check
  constraint untuk menyimpan informasi yang lebih sedikit daripada satu timestamp.
- *Tabel `master_content_version` dengan riwayat revisi.* Spesifikasi menyatakan tidak ada versi di
  v1 (bagian Assumptions). Membangunnya sekarang adalah kompleksitas untuk kebutuhan yang belum ada,
  bertentangan dengan Prinsip VI.

---

## D3 — Invarian "hanya konten master boleh terbit" dijaga database

**Keputusan.** Check constraint `published_at is null or client_id is null` pada `question` dan
`exercise`.

**Alasan.** Prinsip VII: aturan ditegakkan mesin, bukan niat baik. Tanpa constraint ini, satu jalur
tulis yang lupa memeriksa akan membuat konten milik sebuah sekolah membawa keadaan terbit yang tidak
bermakna, dan bug semacam itu tidak terlihat sampai ada yang menghitung. Constraint membuatnya
mustahil, bukan sekadar tidak dianjurkan.

**Alternatif yang ditolak.** *Memeriksa di `MasterPublishingService` saja.* Cukup untuk jalur yang
lewat service, tidak cukup untuk impor, seed, maupun perbaikan data manual.

---

## D4 — Katalog memakai query terpisah untuk konten terbit, bukan parameter boolean

**Keputusan.** `QuestionRepository` mendapat `searchPublishedMaster(...)` di samping `searchMaster(...)`
yang sudah ada. Ruang kerja Eduscreen Admin memakai yang lama (melihat draf dan terbit); katalog
Client memakai yang baru (hanya terbit).

**Alasan.** Repo ini sudah memilih memisahkan `search` dan `searchMaster` alih-alih satu query
berparameter, dengan alasan yang tertulis di javadoc `ExerciseService.list`: `client_id = null` tidak
pernah cocok di SQL. Menambah parameter boolean ke query yang sama akan mengulang kesalahan bentuk
yang sudah dihindari, dan javadoc `QuestionRepository.search` mencatat bahwa PostgreSQL gagal saat
runtime untuk parameter yang tipenya tidak bisa disimpulkan.

**Alternatif yang ditolak.** *Satu query dengan `(:onlyPublished = false or q.publishedAt is not null)`.*
Menyembunyikan perbedaan penting — siapa yang boleh melihat draf — di balik parameter yang mudah
salah kirim, dan mengulang bentuk parameter yang sudah pernah gagal di repo ini.

---

## D5 — Penanda "sudah pernah diadopsi" dibaca dari `source_question_id`, tanpa tabel jejak

**Keputusan.** FR-076 dan FR-077 dipenuhi dengan satu query:
`select distinct q.sourceQuestionId from QuestionEntity q where q.clientId = :clientId and q.sourceQuestionId in :ids`,
dijalankan sekali per halaman katalog atas id yang sedang ditampilkan saja.

**Alasan.** `source_question_id` sudah ditulis `ContentAdoptionService.copyQuestion` sejak
spesifikasi 001 dan ADR-0001 menyebutnya "jejak adopsi agar bisa ditelusuri". Menelusurinya adalah
persis pemakaian yang sudah dirancang untuknya. Membatasi query pada id satu halaman menjaga
biayanya tetap datar berapa pun besar katalognya.

**Alternatif yang ditolak.** *Tabel `client_adoption_log`.* Menyimpan ulang fakta yang sudah ada di
kolom yang sudah ada, dan menambah satu tempat lagi yang bisa tidak sinkron.

---

## D6 — Template bank soal dipakai ulang untuk ruang kerja master

**Keputusan.** `soal/daftar.html` dan `soal/editor.html` menerima satu atribut model `basePath`
(`/soal` atau `/eduscreen/soal`) dan dipasang di dua rute. Hanya perakit Exercise master yang
mendapat template sendiri, karena tampilannya membawa gerbang penerbitan yang tidak ada di perakit
Client.

**Alasan.** Editor soal master dan editor soal Client menampilkan hal yang sama persis: batang soal,
pembahasan, pilihan jawaban, gambar, dan rumus. Menggandakannya berarti setiap perbaikan editor
harus dikerjakan dua kali, dan yang kedua akan terlupakan.

**Alternatif yang ditolak.** *Template terpisah `eduscreen/soal-editor.html`.* Dua berkas yang wajib
tetap identik adalah utang yang membayar dirinya sendiri dengan bug tampilan.

---

## Yang sengaja TIDAK diriset

- **Sinkronisasi master ke Client.** Ditolak eksplisit oleh `docs/adr/0001`. Membalikkannya menuntut
  ADR baru, bukan keputusan rencana.
- **Row-Level Security untuk katalog.** Ditolak eksplisit oleh `docs/adr/0012` untuk v1.
- **Impor massal konten master.** Di luar cakupan v1 menurut bagian Assumptions spesifikasi.
- **Antrean latar untuk adopsi massal.** `TC-45` dan `docs/adr/0014` mewajibkan pemrosesan sinkron.
