---
status: accepted
---

# Topic turun jadi milik Paket, bukan milik Subject

Taksonomi konten menjadi tiga lapis: Subject (label) › Paket › Topic › Question. Paket adalah
wadah baru, satu Subject, berisi soal yang ditulis sebagai satu kesatuan. Topic tidak lagi
global di bawah Subject — ia hidup di dalam satu Paket, dan Question menempel pada Topic milik
Paketnya sendiri. Paket menjadi satuan yang dijual lewat katalog dan satuan yang diadopsi Client.
Meminjam soal antar-Paket dalam satu Client memakai **salinan penuh**, baris Question baru
dengan `sourceQuestionId` menunjuk soal asal — bukan referensi.

## Alasan

Taksonomi dua lapis (ADR-0004) memaksa klasifikasi sebelum menulis: `Question.topic_id` wajib
diisi dan Topic harus dipilih dari daftar global lebih dulu, sehingga Guru dan Client Admin tidak
bisa mulai menulis soal sebelum memutuskan Subject dan Topic. Subject sendiri membengkak karena
jenjang melekat di namanya (`Matematika Kelas 4`, `Matematika Kelas 9`, dst — keputusan ADR-0004
yang tetap berlaku): jumlah Subject tumbuh sebanding mapel dikali jenjang, dan Topic seperti
`Aljabar Dasar` harus dibuat ulang di bawah tiap Subject berjenjang, jadi baris-baris terpisah
yang tidak saling kenal padahal bahasannya sama.

Menurunkan Topic jadi milik Paket menghapus dua masalah sekaligus tanpa menambah entitas jenjang
terpisah. Paket, bukan Topic global, yang menjadi tempat soal pertama kali ditulis dan tempat
Topic hidup, sehingga penulis boleh langsung membuat Paket lalu menulis, dan Topic yang berlipat
di bawah tiap Subject berjenjang tidak lagi terjadi — ia cukup dibuat sekali di dalam satu Paket.
Paket sekaligus mengisi kekosongan wadah yang bisa dijual: sebelumnya Eduscreen memakai Exercise
sebagai paket master, padahal Exercise adalah entitas milik alur Guru (lihat `CONTEXT.md`,
`_Hindari_: Paket Soal` pada entri Exercise). Memberi Eduscreen wadah jual miliknya sendiri
menghentikan tumpang tindih peran itu.

Pinjam antar-Paket memakai salinan penuh, sedangkan `ExerciseItem` tetap memakai referensi ke
`questionId` — dua pola berbeda untuk dua pekerjaan berbeda. Client Admin di bank soal
**menyunting** soal: mengubah `bodyHtml`, menambah Option, memperbaiki `explanation`. Kalau
pinjaman berupa referensi, suntingan itu akan bocor ke setiap Paket lain yang pernah meminjam
soal yang sama, sebuah efek samping yang tidak diinginkan pemiliknya. Guru di Exercise hanya
**merakit**: memilih dan mengurutkan Question yang sudah jadi, tidak pernah mengubah isinya.
Referensi aman di situ karena tidak ada suntingan yang bisa bocor. Aturan salinan penuh saat
meminjam ini konsisten dengan ADR-0001, yang sudah menetapkan salinan penuh untuk adopsi katalog.

## Konsekuensi

- **Soal terduplikasi antar-Paket.** Meminjam satu Question ke tiga Paket menghasilkan tiga baris
  Question, bukan satu baris yang dipakai bertiga. Diterima; ini harga dari mengizinkan Client
  Admin menyunting salinannya sendiri tanpa merambat ke Paket lain.
- **Perubahan pada soal asal tidak merambat ke salinan.** Sama seperti adopsi katalog (ADR-0001),
  sekarang berlaku seragam untuk pinjam antar-Paket juga. Soal yang meminjam sudah ada di Paket
  tujuan (`sourceQuestionId` yang sama) disembunyikan dari daftar pinjam, supaya tidak tersalin
  dua kali secara tidak sengaja.
- **Exercise dan Session tidak terpengaruh.** `ExerciseItem` tetap menunjuk `questionId` sebagai
  referensi, tidak berubah. Session yang sedang berjalan tetap dilindungi Snapshot seperti
  sebelumnya, tanpa hubungan dengan perubahan taksonomi ini.
- Setiap query Paket, Topic, dan Question harus disaring `clientId` di query utama, bukan di kode
  pemanggil — pola yang sama dengan aturan isolasi tenant yang sudah berlaku.
- Migrasi data satu kali diperlukan: tiap Topic lama menjadi satu Paket baru berisi satu Topic
  bernama sama, dan seluruh Question di bawahnya dipindah ke sana. `Topic.subject_id` dan
  `Topic.origin` dibuang setelah migrasi selesai.
