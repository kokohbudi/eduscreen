---
status: accepted
---

# Jenjang melekat di Subject; Subject punya dua asal

Taksonomi konten hanya dua lapis: Subject → Topic. Jenjang tidak dimodelkan sebagai entitas atau atribut tersendiri melainkan ikut ke dalam identitas Subject (`Matematika Kelas 4`, `Matematika Kelas 9` adalah dua Subject berbeda). Subject boleh berasal dari dua tempat: **global** milik Eduscreen, tersedia untuk semua Client, dan **lokal** milik satu Client. Setiap Subject membawa penanda asalnya dan keduanya tampil berdampingan di layar pemilihan.

## Alasan

`Matematika` tanpa jenjang tidak bermakna di bank soal yang memuat Kelas 1 sampai Kelas 12 — Guru akan tenggelam. Menambah entitas `GradeLevel` sebagai dimensi ketiga menyelesaikannya, tapi membayar dengan filter tiga sumbu di setiap layar, setiap impor, dan setiap laporan. Memasukkan jenjang ke nama Subject memberi hasil praktis yang sama dengan nol entitas baru.

Subject global saja terlalu kaku: Client dengan muatan lokal (`Bahasa Sunda Kelas 5`) atau kurikulum khusus akan terhenti menunggu Eduscreen. Subject Client saja menghancurkan taksonomi bersama — konten master tidak punya rumah dan setiap sekolah menulis variasi ejaannya sendiri.

## Konsekuensi

- Nama Subject berulang lintas jenjang. Diterima; keterbacaan Guru lebih penting daripada normalisasi.
- Laporan lintas jenjang (`bagaimana performa Matematika di seluruh sekolah?`) tidak bisa dilakukan lewat query taksonomi. Di luar lingkup v1.
- Setiap layar pemilihan Subject harus menggabungkan dua sumber dan menandai asalnya, agar Guru tahu mana yang datang dari Eduscreen dan mana buatan sekolahnya.
- Topic selalu milik Client bila dibuat di sisi Client, meski Subject induknya global.
