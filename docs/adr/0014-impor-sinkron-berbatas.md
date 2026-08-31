---
status: accepted
---

# Impor soal diproses sinkron dengan batas 500 baris, bukan lewat antrean pekerjaan

Impor Excel/CSV berjalan di dalam thread permintaan dengan batas tegas 500 baris per berkas. Berkas yang lebih besar ditolak, disertai pesan yang meminta pengguna memecahnya. Tidak ada tabel pekerjaan, tidak ada pemroses latar belakang, tidak ada pemantauan progres.

## Alasan

ADR-0002 menolak scheduler dan pekerjaan latar untuk finalisasi Session, dengan alasan bahwa infrastruktur pekerjaan membawa mode kegagalan diam-diam yang mahal dijaga. Impor adalah tempat pertama yang akan menggoda seseorang untuk memasukkannya kembali — dan begitu ia ada untuk impor, ia akan segera dipakai untuk hal lain, dan ADR-0002 mati tanpa pernah dicabut secara sadar.

Batas 500 baris adalah harga untuk menjaga keputusan itu tetap utuh. Ia juga tidak seburuk kelihatannya: onboarding adalah peristiwa sekali seumur Client, dan memecah berkas 2.000 baris menjadi empat adalah pekerjaan beberapa menit bagi orang yang memang sedang duduk memigrasikan bank soal.

## Konsekuensi

- Client dengan bank soal warisan yang besar harus memecah berkasnya. Ini friksi onboarding yang nyata dan harus disebut sejak awal.
- Batasnya adalah angka, bukan prinsip. Menaikkannya ke 1.000 setelah pengukuran adalah perubahan yang sah; menggantinya dengan antrean pekerjaan adalah keputusan berbeda yang menuntut ADR baru dan mencabut ADR-0002.
- Karena impor berjalan sinkron, ia harus tetap responsif: pratinjau dan validasi per baris dikerjakan sebelum penulisan, sehingga pengguna melihat laporan galat tanpa menunggu seluruh berkas tersimpan.
- Seseorang akan mengusulkan antrean pekerjaan begitu ada Client pertama yang mengeluh. Dokumen ini ada supaya usul itu ditimbang sebagai perubahan arsitektur, bukan sebagai perbaikan kecil.
