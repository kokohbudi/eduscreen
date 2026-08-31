---
status: accepted
---

# UUID v7 sebagai primary key untuk entitas yang muncul di URL

Seluruh entitas yang pengenalnya pernah tampil di URL — Session, SessionAnswer, Assignment, Result, dan sejenisnya — memakai **UUID v7** sebagai primary key, bukan bilangan auto-increment dan bukan UUID v4.

## Alasan

Auto-increment membuat `/session/102` bisa dijelajahi dengan menambah satu. Itu menjadikan pencegahan IDOR bergantung sepenuhnya pada pemeriksaan otorisasi; satu endpoint yang lupa memeriksa langsung membocorkan seluruh tabel secara berurutan. Pengenal tak tertebak bukan pengganti otorisasi, melainkan lapis kedua yang membuat satu kelalaian tidak berakibat fatal.

Di antara dua bentuk UUID, v7 dipilih. Keduanya sama-sama tidak tertebak, tetapi v7 terurut waktu sementara v4 sepenuhnya acak. `SessionAnswer` adalah tabel dengan tulis paling deras di sistem ini — target beban 10.000 Session serentak dengan auto-save per jawaban (`spec.md` §11) — dan kunci acak menyebar penyisipan ke seluruh index B-tree, memecah lokalitas halaman tepat pada beban yang paling tidak mampu menanggungnya. v7 menulis di ujung index seperti bilangan berurut, tanpa mengembalikan sifat bisa-dijelajahi.

## Konsekuensi

- Pengenal membocorkan **waktu pembuatan** secara kasar. Untuk Session dan Result ini tidak sensitif; waktu mulai pengerjaan memang sudah terbaca oleh Siswa dan Guru yang bersangkutan.
- 16 byte per kunci, bukan 8. Diterima sebagai harga dari sifat non-enumerable.
- Pembuatan UUID dilakukan aplikasi, bukan database, agar entitas punya identitas sebelum disimpan.
- Ini keputusan yang sangat mahal dibatalkan: mengubah tipe primary key setelah data produksi ada berarti menyentuh setiap foreign key dan setiap URL yang sudah tersebar.
