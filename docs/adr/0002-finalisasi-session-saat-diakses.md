---
status: accepted
---

# Session difinalisasi saat diakses, tanpa scheduler

Session lahir hanya ketika Siswa menekan Start (lazy instantiation), sehingga tidak ada proses yang secara alami menyentuh Session yang ditinggalkan siswanya. Kami memutuskan **tidak** memakai cron atau background job untuk menutupnya. Sebagai gantinya, setiap kali sebuah Session dibaca — oleh Siswa yang kembali, oleh Guru yang membuka laporan, oleh siapa pun — sistem membandingkan `startedAt + effectiveDuration` dengan waktu server; bila sudah lewat, Session difinalisasi jadi `EXPIRED`, Result dihitung, dan keduanya **ditulis ke database** dalam satu operasi idempoten.

## Alasan

Menambah scheduler ke v1 berarti menambah infrastruktur, pemantauan, dan mode kegagalan diam-diam (job mati, Result tidak pernah muncul, tidak ada yang tahu) demi ketepatan waktu yang tidak dibutuhkan siapa pun: tidak ada pihak yang perlu tahu sebuah Session expired pada detik yang sama ia expired. Yang dibutuhkan adalah laporan Guru yang lengkap saat dibuka — dan membuka laporan itu sendiri adalah akses yang memicu finalisasi.

## Konsekuensi

- Status yang dibaca tidak pernah bohong: `IN_PROGRESS` yang sudah lewat waktu tidak akan pernah tersaji, karena perhitungan mendahului penyajian.
- Laporan Guru memuat seluruh Session Assignment itu, sehingga membukanya memfinalisasi semuanya sekaligus. Pembukaan pertama setelah deadline lebih lambat daripada berikutnya.
- Finalisasi harus idempoten dan aman dari balapan: dua permintaan bersamaan atas Session yang sama tidak boleh menghasilkan dua Result.
- Result menyimpan skor hasil hitung, bukan menghitung ulang saat dibaca, agar angka historis tidak bergeser bila aturan skoring berubah.
