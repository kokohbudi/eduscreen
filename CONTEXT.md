# Eduscreen

Platform SaaS multi-tenant untuk distribusi dan pengerjaan soal latihan di sekolah dan lembaga bimbingan belajar. Glosarium ini adalah bahasa resmi proyek: kode, isu, tes, dan dokumen memakai istilah di sini, bukan sinonimnya.

## Organisasi

**Eduscreen**:
Pemilik platform. Mengelola taksonomi global, konten master, dan onboarding Client.
_Hindari_: Vendor, Provider, Pusat

**Client**:
Sekolah atau lembaga bimbingan belajar yang berlangganan platform. Satu Client adalah satu tenant dengan data yang terisolasi penuh dari Client lain.
_Hindari_: Tenant, Sekolah, Institusi, Organisasi, Customer

## Peran

**Eduscreen Admin**:
Pengguna Eduscreen yang mengelola Subject global, konten master, dan onboarding Client.
_Hindari_: Superadmin, Admin Pusat

**Client Admin**:
Pengguna Client yang mengelola Ruangan, akun Guru dan Siswa, serta Question Bank internal.
_Hindari_: Admin Sekolah, Operator

**Guru**:
Pengguna Client yang meracik Exercise dan menerbitkannya sebagai Assignment ke Ruangan yang ditugaskan padanya.
_Hindari_: Pengajar, Tentor, Teacher

**Siswa**:
Pengguna Client yang menjadi anggota satu atau lebih Ruangan dan mengerjakan Assignment di dalamnya.
_Hindari_: Murid, Peserta Didik, Student

## Konten

**Subject**:
Mata pelajaran pada satu jenjang tertentu, misalnya `Matematika Kelas 4`. Berasal dari Eduscreen (global) atau dibuat Client (lokal).
_Hindari_: Mapel, Mata Pelajaran, Course

**Topic**:
Sub-bahasan di bawah satu Subject, misalnya `Aljabar Dasar`.
_Hindari_: Bab, Materi, Chapter

**Question**:
Satu butir soal, bertipe `MULTIPLE_CHOICE` atau `ESSAY`, yang selalu melekat pada tepat satu Topic.
_Hindari_: Soal, Item, Butir

**Option**:
Satu pilihan jawaban pada Question bertipe `MULTIPLE_CHOICE`. Tepat satu Option benar.
_Hindari_: Pilihan, Choice, Jawaban

**Question Bank**:
Kumpulan seluruh Question milik satu pemilik — Eduscreen atau satu Client. Bukan entitas tersendiri, melainkan cara menyebut ruang lingkup kepemilikan Question.
_Hindari_: Bank Soal, Repository Soal

**Exercise**:
Templat statis berisi kumpulan Question terurut, diracik untuk satu tujuan tertentu. Boleh memuat Question lintas Subject dan Topic. Netral terhadap mode — Exercise yang sama bisa diterbitkan sebagai Quiz maupun Practice.
_Hindari_: Paket Soal, Latihan, Set Soal, Kuis

## Distribusi

**Ruangan**:
Kelompok belajar milik satu Client, misalnya `Kelas 4B` atau `Bimbel Intensif SBMPTN Group B`. Berisi Siswa dan Guru; satu Siswa boleh berada di beberapa Ruangan.
_Hindari_: Kelas, Grup, Rombel, Classroom

**Assignment**:
Exercise yang diterbitkan Guru ke tepat satu Ruangan dengan mode dan aturan waktu tertentu.
_Hindari_: Tugas, Penugasan, Ujian, Tes, Publikasi

**Quiz**:
Mode Assignment untuk penilaian. Timer wajib, navigasi soal bebas, jawaban bisa diubah sampai submit, feedback baru muncul setelah selesai.
_Hindari_: Ujian, Tes, Ulangan

**Practice**:
Mode Assignment untuk latihan. Hanya boleh berisi Question `MULTIPLE_CHOICE`, Timer opsional, navigasi maju satu arah, tiap jawaban langsung terkunci dan pembahasannya terbuka seketika.
_Hindari_: Latihan, Drill, Belajar Mandiri

**Timer Duration**:
Batas durasi pengerjaan satu Session, dihitung dari saat Siswa menekan Start.
_Hindari_: Durasi, Waktu Pengerjaan

**Expiration Date**:
Batas akhir penayangan Assignment. Berlaku untuk seluruh Ruangan tanpa memandang kapan tiap Siswa memulai.
_Hindari_: Deadline, Batas Waktu, Due Date

## Pengerjaan

**Session**:
Satu kali percobaan pengerjaan Assignment oleh satu Siswa. Dibuat saat Siswa menekan Start, tidak pernah sebelumnya.
_Hindari_: Attempt, Pengerjaan, Percobaan, Ujian

**Attempt**:
Nomor urut Session milik satu Siswa pada satu Assignment. Bukan entitas tersendiri, melainkan atribut Session.
_Hindari_: Percobaan ke-, Retry, Trial

**Snapshot**:
Salinan beku isi Assignment ke dalam satu Session pada saat Session lahir — urutan Question dan urutan Option setelah pengacakan. Tidak pernah berubah selama umur Session.
_Hindari_: Freeze, Salinan, Copy

**SessionQuestion**:
Satu Question di dalam Snapshot sebuah Session, membawa posisi urutnya dan urutan Option-nya.
_Hindari_: Item Sesi, Soal Sesi

**SessionAnswer**:
Jawaban Siswa untuk satu SessionQuestion, tersimpan sejak dikirim pertama kali.
_Hindari_: Response, Jawaban Siswa

**Result**:
Hasil terkalkulasi dari satu Session: skor, jumlah benar dan salah, serta status penilaiannya. Result resmi seorang Siswa pada satu Assignment adalah Result dengan skor tertinggi di antara semua Session-nya.
_Hindari_: Nilai, Score, Rapor, Hasil
