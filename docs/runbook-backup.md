# Runbook — Pencadangan & Pemulihan

## Prinsip

Cadangan yang belum pernah dipulihkan bukan cadangan, melainkan asumsi. `pg_dump` yang selesai
tanpa galat membuktikan bahwa data bisa dibaca dan ditulis ke berkas — ia tidak membuktikan bahwa
berkas itu bisa dipulihkan menjadi database yang berfungsi, dengan skema yang cocok dengan versi
aplikasi yang akan membacanya. Satu-satunya cara tahu itu benar adalah memulihkannya sungguhan
(lihat "Uji pemulihan terjadwal" di bawah).

Yang dipertaruhkan bukan data yang bisa diketik ulang. Yang dipertaruhkan adalah pekerjaan ujian
yang tidak bisa diulang: seorang Siswa yang sudah mengerjakan dan dinilai tidak bisa diminta
mengerjakan ulang ujian yang sama, dan seorang Guru yang sudah menuliskan nilai essay tidak bisa
diminta mengingat kembali angka yang sudah hilang. TC-43 menjadikan pencadangan bagian dari
kontrak operasional v1, bukan langkah opsional yang menyusul kalau sempat.

## Cadangan penuh harian

Dijalankan sekali sehari terhadap database `eduscreen` (PostgreSQL 16), memakai format custom
`pg_dump` (`-Fc`) karena format ini mendukung pemulihan selektif per tabel lewat `pg_restore`
dan terkompresi secara default — lebih hemat ruang dan lebih cepat dipulihkan sebagian daripada
dump teks polos.

```bash
PGPASSWORD="$BACKUP_DB_PASSWORD" pg_dump \
  -h "$BACKUP_DB_HOST" -U "$BACKUP_DB_USER" -d eduscreen \
  -Fc -f "eduscreen_$(date +%Y%m%d_%H%M%S).dump"
```

Penamaan berkas bertanggal (`eduscreen_YYYYMMDD_HHMMSS.dump`) supaya urutan cadangan bisa
ditelusuri tanpa membaca metadata berkas, dan supaya cadangan hari yang sama tidak saling
menimpa bila prosesnya dijalankan ulang.

Retensi yang disarankan: cadangan harian disimpan 14 hari, ditambah satu cadangan mingguan
disimpan 3 bulan. Angka ini cukup untuk menutupi kasus paling umum — kesalahan yang baru
disadari beberapa hari kemudian — tanpa menumpuk penyimpanan tanpa batas.

Tempat penyimpanannya **harus di luar mesin aplikasi**. TC-42 menetapkan topologi v1 sebagai
satu instance: satu mesin menjalankan aplikasi dan menyimpan berkas lokal di baliknya. Itu
membuat mesin itu satu-satunya titik kegagalan — bila disknya rusak atau instance-nya hilang,
cadangan yang tersimpan di mesin yang sama hilang bersamaan dengan data yang seharusnya
diselamatkannya. Cadangan disalin ke penyimpanan terpisah (mis. object storage atau mesin lain)
segera setelah `pg_dump` selesai, bukan dibiarkan menumpuk di disk lokal.

## Arsip WAL untuk pemulihan titik waktu

Cadangan harian saja tidak cukup: bila kegagalan terjadi 20 jam setelah cadangan terakhir,
seluruh jawaban ujian yang masuk di rentang itu — auto-save `session_answer`, `result` yang
sudah difinalisasi — hilang tanpa bisa diganti. Kehilangan satu hari jawaban ujian bukan
kehilangan data yang bisa direproduksi; siswa tidak bisa diminta mengulang ujian yang sudah
lewat waktunya. Karena itu cadangan harian dilengkapi arsip Write-Ahead Log (WAL) agar
pemulihan bisa dilakukan sampai titik waktu tertentu (point-in-time recovery), bukan hanya
sampai cadangan penuh terakhir.

Setelan `postgresql.conf` yang dibutuhkan:

```
wal_level = replica
archive_mode = on
archive_command = 'test ! -f /path/wal-archive/%f && cp %p /path/wal-archive/%f'
```

`archive_command` di atas adalah contoh minimal (salin ke direktori lokal); pada penyiapan
sungguhan, perintah ini menyalin ke penyimpanan di luar mesin aplikasi dengan alasan yang sama
seperti cadangan penuh (TC-42) — arsip WAL yang hanya hidup di mesin yang sama dengan
database-nya tidak menambah perlindungan apa pun.

## Uji pemulihan terjadwal

Kadensi: **bulanan**.

Langkah:

1. Siapkan instans PostgreSQL 16 terpisah (bukan mesin aplikasi produksi/demo).
2. Pulihkan cadangan penuh terbaru:
   ```bash
   pg_restore -h "$RESTORE_TEST_HOST" -U "$RESTORE_TEST_USER" \
     -d eduscreen_restore_test --clean --if-exists "eduscreen_YYYYMMDD_HHMMSS.dump"
   ```
3. Jalankan aplikasi terhadap instans hasil pulihan dengan `spring.jpa.hibernate.ddl-auto:
   validate` (setelan baku di `application.yml`, TC-17) dan Flyway aktif tanpa migrasi baru
   dijalankan — aplikasi harus start bersih.

Kriteria lulus, seluruhnya harus terpenuhi:

- [ ] Jumlah baris tabel `result` di database hasil pulihan sama dengan jumlah baris di sumber
      pada waktu cadangan diambil.
- [ ] Jumlah baris tabel `session_answer` di database hasil pulihan sama dengan jumlah baris di
      sumber pada waktu cadangan diambil.
- [ ] `flyway_schema_history` di database hasil pulihan berada pada versi migrasi yang sama
      dengan sumber (baris terakhir cocok, tidak ada migrasi yang tertinggal atau gagal).
- [ ] Aplikasi berhasil start terhadap skema hasil pulihan dengan `ddl-auto: validate` — ini
      sendiri sudah membuktikan skemanya utuh, karena Hibernate menolak start bila satu kolom
      atau constraint saja tidak cocok dengan entity yang dipetakannya (TC-17).

Uji yang gagal pada kriteria mana pun dicatat sebagai temuan dan ditindaklanjuti sebelum uji
bulan berikutnya — bukan diabaikan sampai cadangan sungguhan dibutuhkan.

## Larangan tegas

Hasil pemulihan produksi **tidak pernah** dipulihkan ke `demo` (TC-48). Instans pemulihan-uji di
atas adalah instans terpisah yang dibuang setelah pengujian, bukan jalan pintas untuk mengisi
`demo` dengan data yang "kebetulan sudah ada". Lihat `docs/runbook-demo.md` untuk daftar periksa
data peragaan dan langkah penanganan bila data produksi terlanjur masuk ke `demo`.
