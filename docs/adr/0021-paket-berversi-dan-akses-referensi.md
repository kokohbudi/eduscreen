---
status: accepted
---

# Paket berversi, soal berbagi baris, dan akses sekolah lewat referensi

Penempatan soal pindah dari kolom di `question` ke tabel keanggotaan per versi Paket
(`paket_version`, `paket_item`). Soal menjadi isi murni yang boleh dipakai banyak versi dan banyak
Paket tanpa disalin. Versi Paket master yang sudah terbit beku; mengubahnya berarti versi baru
(penempatan disalin, soal dibagi) atau instance baru (Paket lain yang berbagi soal yang sama).
Soal master terbit tidak diedit di tempat: revisi melahirkan baris baru yang menggantikannya di
versi kerja saja. Sekolah tidak lagi menerima salinan: Eduscreen Admin memberi **akses**
(`paket_access`: sekolah, Paket, versi, sampai tanggal), dan Guru merakit Exercise langsung dari
soal master. Keputusan ini menggantikan ADR-0001 dan merevisi bagian adopsi ADR-0018.

## Alasan

Adopsi salinan (ADR-0001) membuat 100 sekolah yang memakai Paket 100 soal menyimpan 10.100 baris
`question` kembaran, dan soal yang sama tidak bisa hidup di dua Paket atau dua versi tanpa
disalin lagi. Yang sebenarnya ingin dilindungi ADR-0001 adalah satu hal: teks yang sedang
dikerjakan siswa tidak boleh berubah di bawah kakinya. Itu bisa dijamin tanpa salinan, karena
`session_question` dan `exercise_item` menunjuk baris `question` — cukup baris itu yang beku.
Maka: soal master terbit beku (edit = baris revisi baru), versi Paket terbit beku (isi = himpunan
item yang tidak berubah), dan sekolah memilih sendiri kapan pindah versi.

Topic tetap milik satu Paket (ADR-0018) dan kini murni label: isinya ditentukan `paket_item`
per versi. Dalam satu Paket satu nama Topic adalah satu baris lintas versi; di Paket lain nama
yang sama adalah baris lain. Menjadikannya global lagi akan mengulang masalah ADR-0004.

Akses diberi Eduscreen Admin, bukan diambil sekolah dari katalog: sekolah tidak bisa mengambil
Paket yang tidak diberikan, dan Guru tidak bisa memakai atau menyalin soal di luar sekolahnya
(Pasal 3 CONSTITUTION). Sekolah memilih sendiri kapan pindah versi supaya revisi master tidak
pernah mendarat diam-diam di tengah semester — alasan asli ADR-0001 tetap terpenuhi.

## Bentuk data

- `paket_version(paket_id, client_id, nomor, published_at, superseded_at)`: paling banyak satu
  versi kerja (`published_at` null) per Paket. Paket milik Client punya satu versi kerja
  selamanya; Paket master membekukan versi kerjanya saat terbit.
- `paket_item(paket_version_id, client_id, topic_id, question_id, position)`: satu baris per soal
  per versi. `client_id` = pemilik soal; FK komposit `paket_item_same_owner` dan
  `paket_item_question_owner` menutup soal sekolah masuk versi master atau versi sekolah lain
  (pola V9, MATCH SIMPLE: pasangan ber-null lolos dan dijaga konstruktor entity).
- `question` kehilangan `paket_id/topic_id/position`, mendapat `superseded_by_id`.
- `paket_access(client_id, paket_id, version_id, valid_until, revoked_at)`: satu akses aktif per
  (sekolah, Paket); versi wajib milik Paket itu (FK komposit).

## Aturan

- Menerbitkan Paket membekukan versi kerjanya sebagai versi N. Menulis ke Paket tanpa versi
  kerja ditolak dengan pilihan (`NeedsVersionChoiceException`, 409 — layar mengalihkan ke
  halaman Paket yang menawarkan versi baru / instance baru).
- Soal master `published_at` terisi beku: `QuestionService.revise` melahirkan baris baru
  (draf) yang menggantikannya di versi kerja; baris lama `superseded_by_id`. Menarik soal yang
  ada di versi terbit ditolak; menghapusnya hanya membuang dari versi kerja.
- Versi terbit boleh memuat item soal draf (ADR-0020 tetap); sekolah hanya melihat item yang
  soalnya terbit.
- Predikat ACCESSIBLE (`QuestionRepository`): soal milik Client, atau soal master terbit yang
  ditempatkan di versi yang terlihat Client (`PaketAccessService.visibleVersionIds`, parameter
  eksplisit — TC-36). Versi-lah yang menentukan, bukan `superseded_by_id`: sekolah di versi 1
  tetap membaca baris yang di versi 2 sudah digantikan.
- Akses kedaluwarsa atau dicabut, dan Paket yang ditarik, hanya menutup pemakaian baru.
  Exercise dan sesi yang sudah menunjuk soal tetap membacanya (`findAllForSnapshot`).
- Satu-satunya jalan soal master menjadi baris milik sekolah adalah pinjam (salin ke Paket
  sekolah), dan itu keputusan sekolah saat memang ingin mengubahnya.

## Konsekuensi

- 100 sekolah × 100 soal × 3 versi: `question` 100 + revisi, `paket_item` 300,
  `paket_version` 3, `paket_access` 100. Sekolah nol baris soal.
- Salinan adopsi lama (ADR-0001) dibiarkan: sudah milik sekolah masing-masing,
  `paket.source_paket_id` tinggal jejak. Tidak ada konversi.
- Riwayat versi hanya rantai `superseded_by_id` dan daftar versi; tanpa diff.
- Edit soal milik sekolah yang sudah dipakai Exercise tidak berubah aturannya (di luar lingkup).
- Migrasi: V11 (keanggotaan, perilaku sama), V12 (bekukan versi 1 Paket master terbit),
  V13 (`paket_access`). Seed lokal V901/V902 ditulis ulang.
