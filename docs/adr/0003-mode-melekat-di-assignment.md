---
status: accepted
---

# Mode Quiz/Practice melekat di Assignment, bukan Exercise

Eduscreen punya dua mode pengerjaan dengan perilaku sangat berbeda: Quiz (navigasi bebas, feedback setelah submit) dan Practice (linear, jawaban terkunci per soal, pembahasan seketika). Kami memutuskan mode ditetapkan saat Guru **menerbitkan Assignment**, bukan saat Exercise dibuat. Exercise tetap netral: kumpulan Question terurut tanpa opini tentang cara mengerjakannya.

## Alasan

Guru lazim memakai paket soal yang sama dua kali — sebagai Practice untuk pemanasan, lalu sebagai Quiz untuk penilaian. Menempelkan mode di Exercise memaksa Guru menduplikasi paket, dan duplikat itu akan menyimpang satu sama lain seiring waktu. Aturan "Exercise terkunci setelah dipublish" memperburuknya: Exercise yang lahir sebagai Practice terkunci sebagai Practice selamanya.

## Konsekuensi

- Perilaku pengerjaan bercabang di runtime, bukan di data. Klien pengerjaan membaca `assignment.mode` untuk menentukan navigasi, penguncian jawaban, dan waktu munculnya pembahasan.
- Validasi kompatibilitas terjadi **saat publish**, bukan saat Exercise dirakit: Exercise yang memuat Question `ESSAY` ditolak bila hendak diterbitkan sebagai Practice. Guru baru mengetahuinya di langkah terakhir.
- Aturan waktu ikut bercabang: Timer wajib untuk Quiz, opsional untuk Practice.
