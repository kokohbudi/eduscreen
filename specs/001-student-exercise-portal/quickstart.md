# Quickstart & Validation Guide

**Date**: 2026-08-31 | **Plan**: [plan.md](./plan.md)

Panduan menjalankan dan **membuktikan** fitur ini bekerja ujung ke ujung. Detail entitas ada di
[data-model.md](./data-model.md); permukaan endpoint di [contracts/](./contracts/).

## Prasyarat

| Kebutuhan | Versi |
| --- | --- |
| JDK | 25 LTS |
| Maven | 3.9+ |
| Docker | berjalan — dipakai Testcontainers (TC-38) dan PostgreSQL lokal |
| PostgreSQL | 16+ (lewat Docker) |

## Menjalankan secara lokal

```bash
# 1. PostgreSQL untuk pengembangan
docker compose up -d          # PostgreSQL 16 di host port 5433

# 2. Variabel environment wajib — aplikasi menolak start tanpa ini (TC-04)
export EDUSCREEN_ENV=local

# 3. Jalankan; Flyway memigrasi skema saat start (TC-17)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Buka `http://localhost:8080`. Password untuk semua akun di profil `local` adalah `password123`
(adapter dummy, ADR-0016).

> **Peringatan keamanan.** Adapter dummy menerima satu password untuk **semua** akun: siapa pun
> yang mengetahuinya masuk sebagai Siswa, Guru, atau Client Admin mana pun. Ia hanya boleh hidup
> di `local` dan `demo`, dan tidak boleh berada di sistem yang memuat data siswa sungguhan
> (TC-34). Aplikasi menolak start bila `EDUSCREEN_ENV` bernilai lain atau tidak diset.

## Menjalankan tes

```bash
./mvnw test                                   # seluruh tes; Testcontainers menyalakan PostgreSQL
./mvnw test -Dtest=ArchUnitRulesTest          # batas arsitektur (TC-40)
./mvnw test -Dtest='*IdorTest'                # isolasi lintas-Siswa & lintas-Client (TC-41)
```

Nama tes merujuk pengenal `AC-*` dari spesifikasi sumber (TC-39), sehingga cakupan kriteria
penerimaan bisa diperiksa mesin:

```bash
./mvnw test | grep -oE 'AC-[A-Z]+[0-9]+' | sort -u
```

## Data awal untuk profil `local`

Migrasi Flyway khusus `local` menyiapkan satu tenant yang bisa langsung dipakai:

| Objek | Isi |
| --- | --- |
| Client | `SD Contoh` — zona `Asia/Jakarta` |
| Akun | `admin@contoh.sch.id`, `guru@contoh.sch.id`, `siswa1@…` sampai `siswa5@contoh.sch.id` |
| Ruangan | `Kelas 4B 2026/2027` berisi kelima Siswa dan satu Guru |
| Bank soal | 12 soal `Matematika Kelas 4` / `Aljabar Dasar`, 10 pilihan ganda dan 2 essay |

## Skenario validasi

Tiap skenario memetakan langsung ke cerita pengguna di [spec.md](./spec.md).

Ketujuhnya beserta tabel kasus tepi di bawah juga dijalankan otomatis lewat
[`scripts/validasi-quickstart.sh`](../../scripts/validasi-quickstart.sh), yang memanggil aplikasi
lewat HTTP persis seperti yang dilakukan manusia. Skrip itu melengkapi `./mvnw test`, bukan
menggantikannya: tes otomatis memeriksa aturan satu per satu, skrip ini memeriksa apakah
rangkaian penuhnya benar-benar bisa dijalani dari awal sampai nilai keluar.

### V1 — Ruangan dan penggunanya (US1)

1. Masuk sebagai `admin@contoh.sch.id`, buat Ruangan baru, tambahkan tiga Siswa.
2. Buat akun Guru baru; periksa undangan di penampung email pengembangan.
3. Masuk sebagai Siswa yang berada di dua Ruangan.

**Berhasil bila**: kedua Ruangan tampil di portal Siswa; Guru yang hanya ditugaskan di satu
Ruangan tidak melihat Ruangan lain; membuka Ruangan milik Client lain menghasilkan `404`.

### V2 — Terbit sampai keluar nilai (US2) — **jalur MVP**

1. Sebagai Guru, susun Exercise berisi 10 soal pilihan ganda dari dua Topic berbeda.
2. Terbitkan sebagai `QUIZ` ke `Kelas 4B`, durasi 60 menit, pengacakan menyala.
3. Sebagai dua Siswa berbeda, tekan Mulai dan bandingkan urutan soalnya.
4. Jawab enam soal sebagai Siswa pertama, tutup tab, buka kembali.
5. Tekan Selesai; buka rekap sebagai Guru.

**Berhasil bila**: urutan kedua Siswa berbeda dan masing-masing tetap sama setelah dibuka
kembali; enam jawaban utuh; sisa waktu berkurang sesuai waktu yang berlalu; nilai pilihan ganda
keluar tanpa campur tangan; rekap menampilkan seluruh anggota Ruangan termasuk yang belum mulai.

### V3 — Practice (US3)

1. Terbitkan Exercise pilihan ganda yang sama sebagai `PRACTICE`.
2. Kerjakan sebagai Siswa; perhatikan pembahasan tiap soal.
3. Coba terbitkan Exercise yang memuat essay sebagai `PRACTICE`.

**Berhasil bila**: pembahasan muncul seketika dan jawaban terkunci; mengirim ulang jawaban yang
sama tetap sukses (bukan galat); penerbitan Exercise beressay ditolak dengan menyebut soal
penyebabnya.

### V4 — Essay (US4)

1. Terbitkan Exercise berisi 9 pilihan ganda dan 1 essay sebagai `QUIZ`; kerjakan sampai selesai.
2. Sebagai Guru, buka antrean penilaian, beri nilai 75, lalu ubah menjadi 90.

**Berhasil bila**: hasil awal bertanda menunggu penilaian; setelah dinilai menjadi final dengan
skor `(benar + skor_essay/100) ÷ 10`; kedua perubahan nilai tercatat di jejak audit.

### V5 — Onboarding (US5)

Sebagai Eduscreen Admin, daftarkan Client baru dengan satu paket master, lalu masuk sebagai
Client Admin baru itu.

**Berhasil bila**: bank soal sudah terisi; mengubah soal master setelahnya tidak mengubah salinan
Client.

### V6 — Impor (US6)

Unggah berkas 500 baris yang tujuh barisnya tanpa kunci jawaban; lalu coba berkas 2.000 baris.

**Berhasil bila**: pratinjau menampilkan 493 valid dan 7 kegagalan bernomor baris; berkas 2.000
baris ditolak dengan pesan yang menyebut batas 500.

### V7 — Pengulangan (US7)

Terbitkan `QUIZ` dengan `maxAttempts = 3`; kerjakan tiga kali dengan hasil berbeda; coba yang
keempat.

**Berhasil bila**: tiap pengerjaan punya urutan soal baru; skor resmi adalah yang tertinggi;
pengerjaan keempat ditolak.

## Memeriksa kasus tepi

| Kasus tepi | Cara memeriksa | Diharapkan |
| --- | --- | --- |
| Waktu terpangkas batas akhir | Terbitkan dengan `expiresAt` 10 menit dari sekarang dan durasi 60 menit; tekan Mulai | Hitung mundur menampilkan 10 menit sejak awal |
| Sesi ditinggalkan | Mulai lalu tutup tab; tunggu melewati batas | Rekap Guru memunculkan hasilnya saat dibuka |
| Tidak pernah mulai | Biarkan satu Siswa tidak mengerjakan | `NOT_STARTED`, skor `0`, tanpa baris sesi di database |
| Perpanjangan setelah kedaluwarsa | Perpanjang `expiresAt` setelah beberapa sesi `EXPIRED` | Yang sudah kedaluwarsa tetap kedaluwarsa |
| Jam klien dimundurkan | Ubah jam sistem lalu muat ulang halaman | Sisa waktu tidak bertambah |
| Soal dihapus saat berjalan | Hapus soal yang dipakai Assignment berjalan | Siswa yang sedang mengerjakan tidak melihat perubahan |
| Kiriman ulang auto-save | Kirim muatan `PUT` yang sama dua kali | Keduanya sukses; hanya satu baris jawaban |
| Sesi milik orang lain | `GET /siswa/sesi/{id}` milik Siswa lain | `404`, pesan dan waktu tanggap identik dengan id yang tidak ada |
| Balapan finalisasi | Panggil rekap Guru dan halaman Siswa bersamaan pada sesi kedaluwarsa | Tepat satu baris `result` |

## Pemeriksaan kepatuhan konstitusi

```bash
# Prinsip VII — batas arsitektur ditegakkan mesin
./mvnw test -Dtest=ArchUnitRulesTest

# Prinsip I — isolasi lintas Siswa dan lintas Client
./mvnw test -Dtest='*IdorTest'

# Prinsip IV — adapter dummy menolak environment di luar local/demo
EDUSCREEN_ENV=production ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Diharapkan: gagal start dengan IllegalStateException

# TC-17 — skema hanya dari migrasi
grep -r 'ddl-auto' src/main/resources/    # harus 'validate' di luar profil local
```
