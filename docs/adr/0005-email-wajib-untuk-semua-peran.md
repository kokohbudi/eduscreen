---
status: accepted
---

# Email wajib sebagai identitas login semua peran, termasuk Siswa

Setiap akun di Eduscreen — Eduscreen Admin, Client Admin, Guru, Siswa — masuk dengan alamat email. Tidak ada jalur alternatif berupa username, kode Ruangan, atau nama siswa.

## Alasan

Alternatif yang serius adalah membolehkan username untuk Siswa, unik dalam lingkup Client. Itu ditolak: satu jalur autentikasi berarti satu alur undangan, satu alur reset password, satu aturan keunikan, dan satu permukaan serangan. Menambah username sebagai identitas kedua menggandakan semuanya, sementara email tetap dibutuhkan untuk pemulihan akun.

Konsekuensinya diketahui dan diterima secara sadar.

## Konsekuensi

- Segmen SD terkena friksi: anak Kelas 1 tidak punya email. Client harus menyediakan email sekolah (`budi.4b@sdxyz.sch.id`) atau memakai email orang tua. Ini beban onboarding Client, bukan cacat sistem, dan harus dinyatakan sejak percakapan penjualan.
- Satu alamat email hanya boleh dipakai satu akun Siswa. Orang tua dengan dua anak di sekolah yang sama membutuhkan dua alamat.
- Karena email siswa sering berupa alamat sekolah yang tidak pernah dibuka, alur undangan tidak boleh menjadi satu-satunya jalan masuk: Client Admin harus bisa memicu ulang undangan dan menetapkan password sementara.
- Bila segmen SD terbukti menolak, ini adalah keputusan yang mahal dibatalkan — identitas login menyentuh autentikasi, impor siswa, dan seluruh data akun yang sudah ada.
