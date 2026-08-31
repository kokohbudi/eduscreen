---
status: accepted
---

# Konten master Eduscreen disalin ke Client, bukan direferensikan

Eduscreen menyediakan Question dan Exercise master yang dipakai banyak Client. Kami memutuskan bahwa saat konten master diadopsi sebuah Client — baik ketika onboarding maupun ketika Client Admin mengimpornya belakangan — sistem membuat **salinan penuh** di Question Bank Client tersebut, bukan referensi ke baris milik Eduscreen. Setelah disalin, konten itu sepenuhnya milik Client: boleh diedit, diberi Topic lokal, atau di-soft-delete tanpa menyentuh master.

## Alasan

Referensi hidup berarti Eduscreen Admin bisa mengubah kalimat soal yang sedang dikerjakan siswa di ratusan sekolah. Snapshot Session melindungi Session yang sudah berjalan, tapi tidak melindungi Exercise terkunci yang isinya berubah di bawah kaki Guru. Isolasi tenant adalah properti yang tidak ingin kami kompromikan pada produk yang dipakai lintas sekolah.

## Konsekuensi

- Perbaikan salah ketik di master **tidak** merambat ke Client yang sudah mengadopsi. Ini disengaja; koreksi lintas Client bukan fitur v1.
- Penyimpanan berlipat: 100 Client yang mengadopsi paket yang sama menyimpan 100 salinan. Diterima — Question adalah baris teks, bukan berkas besar; aset gambar boleh berbagi penyimpanan.
- Setiap Question membawa penanda asal (`sourceQuestionId`) agar jejak adopsi tetap bisa ditelusuri, meski tidak dipakai untuk sinkronisasi apa pun.
