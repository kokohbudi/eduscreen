---
status: accepted
---

# Satu instance untuk v1, meski NFR menyebut 10.000 Session serentak

Eduscreen v1 berjalan sebagai satu instance aplikasi: sesi login tersimpan di memori proses, berkas tersimpan di filesystem lokal di balik `FileStoragePort`. Tidak ada Redis, tidak ada penyimpanan objek, tidak ada penyeimbang beban.

## Alasan

Angka 10.000 Session serentak di `spec.md` §11 terdengar seperti menuntut arsitektur mendatar, tetapi beban itu sebagian besar jatuh ke **database**, bukan ke instance aplikasi: auto-save adalah operasi tulis kecil, dan halaman yang dirender server hampir tidak menyimpan state di aplikasi. Menambah Redis dan penyimpanan objek ke v1 berarti membayar dua infrastruktur untuk masalah yang belum pernah diukur.

Karena itu angka tersebut diperlakukan sebagai **hipotesis yang wajib diuji beban**, bukan sebagai fakta yang sudah memutuskan arsitektur.

## Konsekuensi

- **Setiap deploy memutus Session yang sedang berjalan.** Sesi login lenyap bersama proses, dan Siswa yang sedang ujian terlempar ke halaman login. Ini konsekuensi paling tajam dari keputusan ini dan diterima secara sadar untuk v1.
- Karena itu rilis harus dijadwalkan di luar jam ujian yang diumumkan Client. Ini menjadi aturan operasional, bukan kebetulan.
- Berkas berada di balik port sejak awal (TC-28), sehingga perpindahan ke penyimpanan objek kelak adalah pergantian adapter plus satu kali pemindahan data — bukan penulisan ulang.
- **Pemicu untuk pindah ke topologi mendatar adalah kebutuhan deploy tanpa memutus ujian, bukan angka bebannya.** Bila uji beban membuktikan satu instance tidak sanggup, database kemungkinan besar menyerah lebih dulu daripada aplikasi, dan jawabannya bukan menambah instance.
- Rate limit login boleh memakai penghitung di memori (TC-33) selama keputusan ini berlaku. Pindah ke mendatar mengharuskan penghitung itu ikut pindah.
