---
status: accepted
---

# Konten kaya disanitasi saat ditulis, bukan saat dirender

`Question.body` dan `Option.body` disanitasi dengan allowlist ketat pada saat penyimpanan. Yang masuk database sudah bersih, dan template merendernya dengan `th:utext` tanpa pemrosesan lebih lanjut. Rumus matematika tidak ikut jalur ini: ia disimpan sebagai LaTeX berdelimiter dan dirender KaTeX di klien.

## Alasan

Konten soal ditulis Guru lalu ditayangkan ke puluhan Siswa di dalam Client yang sama — termasuk ke halaman tempat sesi ujian sedang berjalan. Satu akun Guru yang jebol, atau satu berkas impor bermuatan jahat, menjadi stored XSS dengan sasaran yang paling buruk yang bisa dibayangkan di sistem ini.

Sanitasi saat render adalah alternatif yang serius dan ditolak karena dua alasan: ia membayar biaya pembersihan pada setiap penyajian, dan yang lebih menentukan, ia menyebar tanggung jawab keamanan ke setiap template. Satu template baru yang lupa memanggil sanitizer langsung membuka lubang, dan kelalaian semacam itu tidak menghasilkan galat yang terlihat.

## Konsekuensi

- **Markup asli hilang secara permanen.** Yang disimpan adalah hasil sanitasi, bukan apa yang diketik Guru. Bila allowlist kelak diperlebar, konten lama tidak akan mendapatkan kembali apa yang sudah dibuang saat disimpan. Ini keputusan yang tidak bisa dibatalkan terhadap data yang sudah masuk.
- Karena itu allowlist harus cukup lebar sejak awal untuk kebutuhan nyata pengajaran — format dasar, gambar, dan tabel — supaya pelebaran belakangan tidak menjadi kebutuhan yang menyakitkan.
- Data yang masuk sebelum aturan diperketat tidak terlindungi oleh sanitasi baru. Setiap pengetatan allowlist menuntut migrasi pembersihan atas baris yang sudah ada.
- Impor CSV melewati jalur sanitasi yang sama dengan editor. Tidak ada pintu masuk konten yang melewatinya.
