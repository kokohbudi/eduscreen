---
status: accepted
---

# Halaman dirender server dengan Thymeleaf + HTMX, bukan SPA

Antarmuka Eduscreen dirender di server memakai fragment Thymeleaf. Interaktivitas — auto-save asinkron, pertukaran komponen, hitung mundur — ditangani HTMX dan Alpine.js. Tidak ada React, Vue, atau kerangka klien lain, dan tidak ada API JSON yang dibangun khusus untuk melayani klien tersebut.

## Alasan

Jalur yang lazim ditempuh untuk produk seperti ini adalah SPA plus REST API. Kami menolaknya karena ia mengharuskan dua model data yang harus dijaga tetap sinkron, dua tempat logika otorisasi bisa menyimpang, dan satu kontrak API yang harus dipelihara meski tidak ada konsumen lain selain halaman kami sendiri.

Yang meyakinkan bukan hanya soal biaya, melainkan soal keamanan. Aturan paling penting di sistem ini adalah bahwa waktu dan kepemilikan Session ditentukan server (`CONSTITUTION.md` Pasal 3). Ketika server yang merender, keadaan yang dilihat Siswa berasal dari tempat yang sama dengan keadaan yang menjadi keputusan. SPA menyimpan salinan keadaan di klien, dan salinan itu adalah persis benda yang kami habiskan empat lapis perlindungan untuk tidak percayai.

Halaman pengerjaan soal juga bukan aplikasi yang rumit: ia menampilkan satu soal, menerima satu jawaban, dan menyimpannya. HTMX menutupi seluruh kebutuhan itu.

## Konsekuensi

- Endpoint auto-save mengembalikan fragment HTML, bukan JSON. Satu jalur render melayani muat awal maupun pembaruan parsial.
- Hitung mundur Timer tetap membutuhkan JavaScript, ditangani Alpine sebagai komponen yang murni menampilkan. Sisa waktu yang berlaku selalu datang dari server dan disinkronkan berkala.
- Aplikasi seluler native kelak, bila memang dibangun, akan memerlukan API yang saat ini tidak ada. Itu adalah biaya yang sengaja ditunda, bukan yang terlewat.
- Tailwind dijalankan lewat CLI standalone yang terikat ke build Maven, sehingga tidak ada Node.js di jalur runtime — hanya di jalur build.
