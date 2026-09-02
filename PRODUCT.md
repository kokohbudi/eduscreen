# Product

## Register

product

## Users

Empat peran, semuanya pengguna yang sedang di tengah tugas, bukan pengunjung:

- **Eduscreen Admin** — pemilik platform. Merawat Subject global dan konten master, mengonboarding Client. Bekerja di kantor, laptop, sesi panjang.
- **Client Admin** — admin sekolah atau bimbel. Mengelola Ruangan, akun Guru dan Siswa, Question Bank internal, impor CSV. Laptop di ruang tata usaha, sering diselingi tugas lain.
- **Guru** — meracik Exercise, menerbitkannya sebagai Assignment ke Ruangan, menilai essay, membaca rekap. Laptop 13" di ruang guru, siang hari, waktu terbatas antar jam pelajaran.
- **Siswa** — mengerjakan Quiz dan Practice, membaca hasil dan pembahasan. HP atau laptop, sering malam hari di rumah, kadang di kelas. Butuh fokus, bukan navigasi.

Istilah resmi ada di `CONTEXT.md`; antarmuka memakai istilah itu, bukan sinonimnya.

## Product Purpose

Eduscreen adalah SaaS multi-tenant untuk distribusi dan pengerjaan soal latihan di sekolah dan bimbel di Indonesia. Konten mengalir dari Eduscreen (master, lewat katalog) ke Client (bank soal sendiri), lalu Guru meraciknya menjadi Assignment, lalu Siswa mengerjakannya dalam Session yang waktunya dan kepemilikannya ditentukan server. Berhasil berarti: Guru bisa menerbitkan tugas dalam hitungan menit, Siswa bisa mengerjakan tanpa terganggu antarmuka, Admin selalu tahu apa yang macet.

## Brand Personality

Tenang, presisi, terpercaya. Nada bicara lugas dalam Bahasa Indonesia, tanpa jargon pemasaran. Antarmuka mengikuti pola Apple Human Interface Guidelines untuk aplikasi produktivitas: hirarki tipografi jelas dengan huruf sistem, ruang kosong yang murah hati, satu warna aksen hangat yang dipakai hemat, permukaan berlapis dengan bayangan lembut, kontrol standar yang terasa akrab. Alat ini harus menghilang ke dalam tugasnya.

## Anti-references

- Dashboard SaaS bergradien dengan kartu statistik besar dan ikon berwarna-warni.
- Grid kartu seragam ikon + judul + teks yang diulang sepanjang halaman.
- Eyebrow uppercase kecil di atas setiap seksi, side-stripe berwarna di kiri kartu, teks gradien, kaca buram sebagai dekorasi.
- Antarmuka "admin template" generik: sidebar biru tua, tabel bergaris zebra pekat, tombol warna-warni untuk setiap aksi.
- LMS lama yang memaksa Siswa menavigasi menu berlapis sebelum menemukan soal.

## Design Principles

1. **Alat menghilang ke dalam tugas.** Layar pengerjaan Siswa hanya berisi soal, waktu, dan jawaban; chrome lain disingkirkan.
2. **Keadaan selalu terbaca.** Status Assignment, Session, Paket, dan koneksi tampil sebagai badge dan pesan yang konsisten, bukan disimpulkan dari warna samar.
3. **Satu kosakata kontrol di semua layar.** Tombol simpan, input, tabel, badge, dan pesan galat berbentuk sama di seluruh peran; kalau berbeda, salah satunya salah.
4. **Kepadatan untuk Admin, ketenangan untuk Siswa.** Tabel padat dan sidebar untuk peran yang mengelola; satu kolom sempit dan ruang lega untuk yang mengerjakan.
5. **Server yang bercerita.** Semua keadaan dirender server (TC-13); motion dan JavaScript hanya menyampaikan perubahan, tidak pernah menjadi sumber kebenaran.

## Accessibility & Inclusion

- Target WCAG 2.2 AA: kontras teks bodi ≥ 4.5:1 di tema terang maupun gelap, teks besar ≥ 3:1.
- Tema terang dan gelap mengikuti `prefers-color-scheme`; tidak ada informasi yang hanya dibawa warna (selalu disertai teks atau badge).
- `prefers-reduced-motion` mematikan seluruh transisi dan animasi.
- Semua kontrol dapat dioperasikan keyboard dengan fokus yang terlihat; target sentuh Siswa minimal 44 px.
- Bahasa antarmuka Bahasa Indonesia; rumus matematika dirender KaTeX dengan MathML fallback.
