---
status: accepted
---

# Pengerjaan wajib online; tidak ada mode offline di v1

Session hanya bisa dikerjakan saat perangkat Siswa terhubung. Setiap jawaban dikirim ke server segera setelah dipilih; gangguan sesaat ditangani antrean coba-ulang dengan indikator koneksi yang jelas. Bila koneksi hilang lebih lama, Siswa menunggu — tidak ada penyimpanan lokal yang menampung jawaban untuk disinkronkan belakangan.

## Alasan

Waktu adalah satu-satunya kebenaran yang tidak boleh diperdebatkan dalam sistem ujian, dan waktu itu milik server. Mode offline menciptakan pertanyaan yang tidak punya jawaban memuaskan: jawaban yang dibuat perangkat pada menit ke-70 dari Timer 60 menit, lalu tiba di server setelah koneksi pulih — diterima atau ditolak? Menerimanya berarti Timer bisa dilampaui siapa pun yang mematikan wifi. Menolaknya berarti siswa kehilangan pekerjaan yang tampak tersimpan di layarnya.

Timer tetap berjalan di server selama koneksi hilang. Itu properti yang dipertahankan, bukan efek samping.

## Konsekuensi

- Siswa di daerah bersinyal buruk berisiko kehilangan waktu pengerjaan. Mitigasi v1 hanya berupa antrean coba-ulang dan peringatan visual, bukan jaminan.
- Tidak ada PWA, service worker, penyimpanan lokal, atau resolusi konflik di v1 — penghematan ruang lingkup yang besar.
- Auto-save per jawaban tetap memenuhi janji pemulihan yang lain: browser tertutup, perangkat mati, atau tab hilang tidak menghilangkan jawaban dan tidak mengubah urutan Snapshot.
- Bila mode offline dibutuhkan di v2, keputusan yang harus diambil bersamanya adalah kebijakan penolakan jawaban lewat-waktu — bukan sekadar menambah penyimpanan lokal.
