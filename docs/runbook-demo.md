# Runbook — Environment Demo

## Apa itu environment demo, dan di mana batasnya

`demo` adalah environment peragaan: dipakai untuk memperlihatkan Eduscreen kepada calon Client
tanpa menyentuh data sekolah sungguhan. Ia berbagi kode dengan `local` dan `production` — yang
membedakannya hanyalah konfigurasi (`application-demo.yml`) dan, secara eksplisit lewat TC-34,
**data**, bukan nama environment. Sebuah server bernama "demo" yang diam-diam diisi data sekolah
sungguhan — nama siswa, jawaban, nilai — sudah menjadi produksi, apa pun labelnya, dan warisan
tanggung jawabnya (kerahasiaan data, hak Client atas datanya) ikut serta meski labelnya tidak
berubah. Aturan-aturan di bawah ini ada untuk menjaga garis itu tetap tegas, bukan sekadar
kosmetik.

Adapter identity yang dipakai di `demo` adalah `DummyIdentityProviderAdapter` (TC-04, TC-34):
satu password berlaku untuk semua akun, dan autentikasinya karena itu tidak nyata. Itu sebabnya
tiga pengaman di bawah — spanduk, email mati, larangan pemulihan — semuanya wajib menyala
bersamaan. Menghidupkan satu tanpa yang lain membiarkan celah yang seharusnya sudah ditutup yang
lain.

## Spanduk permanen (TC-47)

Setiap halaman di `demo` menampilkan spanduk peringatan yang tidak bisa ditutup pengguna.
Dikendalikan properti `eduscreen.demo-banner: true` di `src/main/resources/application-demo.yml`,
dirender di fragment `page(...)` pada `src/main/resources/templates/layout/base.html` — dicek
lewat `@environment.getProperty(...)`, bukan flag statis, sehingga profil apa pun yang menyalakan
properti ini otomatis mendapat spanduknya. Untuk memeriksa spanduk masih hidup: buka environment
`demo` sungguhan (bukan `local`) dan pastikan pita kuning "LINGKUNGAN PERAGAAN" tampil di atas
setiap halaman, termasuk halaman login.

Bila spanduk hilang dari satu halaman, itu bug rilis yang menunda peragaan berikutnya sampai
diperbaiki — bukan sesuatu yang ditoleransi "untuk sekarang".

## Email transaksional dimatikan (TC-49)

`eduscreen.outbound-email-enabled: false` di `application-demo.yml`. Undangan akun dan tautan
reset password (BR-U04) tidak pernah dikirim ke alamat sungguhan di `demo` — mereka dialihkan ke
penampung uji atau tidak terkirim sama sekali, tergantung adapter email yang aktif.

Alasannya bukan kehati-hatian berlebih: mengirim tautan reset password yang sah dari sistem yang
autentikasinya palsu (satu password untuk semua akun) adalah cara tercepat mengubah peragaan
menjadi insiden keamanan. Siapa pun yang tahu alamat email seseorang bisa memicu reset, dan
sistem otentikasi `demo` tidak dirancang untuk menahan serangan seperti itu.

## Larangan pemulihan dari cadangan produksi (TC-48)

Ini bagian paling penting dari runbook ini, karena ini jalur kebocoran data yang paling mungkin
terjadi dalam praktik — bukan lewat celah teknis, melainkan lewat niat baik yang salah arah:
seseorang ingin peragaan terasa meyakinkan, lalu menyalin data nyata dari produksi (atau dari
cadangannya) ke `demo` supaya kelihatan "hidup". Begitu itu terjadi, data siswa, daftar kelas,
atau bank soal milik Client sungguhan berada di sistem yang autentikasinya adalah satu password
untuk semua orang. Database `demo` **tidak pernah** dipulihkan dari cadangan produksi, dan tidak
pernah menerima impor apa pun yang berisi data siswa, daftar kelas, atau bank soal milik Client
sungguhan. Tidak ada pengecualian "hanya sekali", "hanya sebagian", atau "sudah dianonimkan buru-
buru" — cara amannya adalah data karangan yang dibuat khusus untuk demo (lihat bagian di bawah).

### Daftar periksa sebelum memuat data apa pun ke demo

Sebelum menjalankan `INSERT`, mengunggah berkas impor, atau memulihkan berkas `.sql`/`.dump` apa
pun ke database `demo`, pastikan seluruh baris berikut ini "ya":

- [ ] Setiap nama, email, dan nomor di dalamnya dikarang untuk keperluan demo, bukan disalin dari
      Client sungguhan mana pun.
- [ ] Data itu tidak berasal dari `pg_dump` produksi, snapshot produksi, atau ekspor apa pun yang
      diambil dari database `production`.
- [ ] Tidak ada bank soal, kunci jawaban, atau materi yang dimiliki Client tertentu ikut masuk.
- [ ] Sumber berkas bisa ditelusuri kembali ke satu skrip seed yang dikelola di repo (bukan
      "diterima dari seseorang lewat chat").
- [ ] Orang yang memuat data mengerti bahwa `demo` bukan tempat penyimpanan sementara —
      apa pun yang masuk berpotensi terlihat siapa saja yang login dengan password bersama.

### Bila ternyata data nyata sudah terlanjur masuk

Perlakukan ini sebagai **insiden kebocoran data**, bukan sebagai kesalahan konfigurasi yang
cukup diperbaiki diam-diam. Langkahnya:

1. Hentikan akses publik ke `demo` (matikan instance atau cabut aksesnya) sebelum melakukan apa
   pun yang lain — jendela paparan makin lama makin besar selama sistem tetap hidup dengan
   autentikasi palsu di depan data nyata.
2. Catat apa yang masuk, kapan, dan siapa yang memuatnya — ini yang akan ditanyakan saat
   melaporkan ke Client yang datanya terdampak.
3. Kosongkan database `demo` sepenuhnya (bukan menghapus baris yang "kelihatannya" bermasalah)
   dan muat ulang dari data karangan yang bersih.
4. Laporkan ke Client yang datanya terdampak dan ke pihak internal yang menangani insiden
   keamanan, sesuai jalur pelaporan insiden organisasi — jangan menunggu sampai "yakin ada yang
   melihatnya" sebelum melapor.
5. Telusuri jalan masuknya (siapa yang menjalankan pemulihan, dari berkas mana) dan tutup jalur
   itu, misalnya lewat pembatasan siapa yang punya kredensial `DEMO_DB_*`.

## Variabel lingkungan wajib

- `EDUSCREEN_ENV=demo` — tanpa ini aplikasi menolak start. `DummyIdentityProviderAdapter` gagal
  cepat (fail-fast) bila `eduscreen.env` bukan `local` atau `demo` (TC-04), sehingga adapter
  autentikasi palsu ini tidak bisa tersambung tanpa sengaja ke environment yang tidak
  memperbolehkannya.
- `DEMO_DB_URL`, `DEMO_DB_USER`, `DEMO_DB_PASSWORD` — kredensial koneksi ke database `demo`,
  dibaca `application-demo.yml`.

## Cara menyiapkan data peragaan

Data di `demo` harus seluruhnya karangan — nama, email, dan isi soal yang dibuat khusus supaya
peragaan terasa hidup tanpa menyentuh data siapa pun yang sungguhan. Bentuknya bisa dicontoh dari
`src/main/resources/db/seed-local/V900__local_seed.sql`: satu Client contoh, beberapa akun per
peran, beberapa Ruangan, dan keanggotaannya — semuanya nilai karangan dengan pola ID yang sama.

Perlu dicatat apa adanya: seed itu **hanya dimuat di profil `local`**, bukan `demo`. Lokasi
Flyway tambahannya (`classpath:db/seed-local`) didaftarkan di `application-local.yml`, dan
`application-demo.yml` tidak mendaftarkannya. Artinya menjalankan aplikasi dengan
`EDUSCREEN_ENV=demo` tidak otomatis mengisi data apa pun ke database `demo` — data peragaan
harus disiapkan lewat langkah terpisah (skrip seed serupa yang ditujukan khusus untuk `demo`,
atau dimasukkan lewat UI aplikasi sebagai Eduscreen Admin) yang mengikuti daftar periksa di atas.
