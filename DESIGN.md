# Design

Sistem visual Eduscreen. Sumber kebenaran tunggalnya adalah
`src/main/resources/static/css/input.css`; dokumen ini merangkumnya supaya perubahan UI berikutnya
memakai kosakata yang sama, bukan menciptakan varian baru.

## Visual Theme & Atmosphere

Antarmuka produktivitas mengikuti pola Apple Human Interface Guidelines: huruf sistem, ruang kosong
murah hati, satu aksen hangat yang dipakai hemat, permukaan berlapis dengan bayangan lembut, kontrol
standar. Tema punya tiga keadaan — Terang, Gelap, dan Sistem (default, mengikuti
`prefers-color-scheme`); pilihan pengguna disimpan di `localStorage['tema']` dan dipaksa lewat
`data-theme` di `<html>`. Mobile first:
ukuran dasar adalah ukuran sentuh (44 px), mengecil ke kepadatan meja kerja hanya di ≥768 px.

## Color Palette

Semua warna OKLCH, didefinisikan sebagai variabel mentah di `:root` lalu dipetakan ke namespace
Tailwind lewat `@theme inline`. Templat memakai `bg-bg`, `text-ink`, `border-line`, dst.; tidak
pernah `dark:`. Satu token membawa kedua nilainya sekaligus lewat `light-dark(terang, gelap)`,
bukan blok `@media (prefers-color-scheme)` terpisah; yang memilih di antara keduanya adalah
properti `color-scheme` — `light dark` di `:root` (ikut OS), dikunci oleh
`:root[data-theme="light"|"dark"]`. Tuas yang sama membuat kontrol form dan scrollbar bawaan
peramban ikut tema yang dipaksa.

| Token | Terang | Gelap | Peran |
| --- | --- | --- | --- |
| `bg` | `oklch(1 0 0)` | `oklch(0.13 0 0)` | latar halaman |
| `surface` | `oklch(1 0 0)` | `oklch(0.18 0.004 60)` | kartu, panel, input |
| `surface-2` | `oklch(0.975 0.004 60)` | `oklch(0.155 0.004 60)` | sidebar, segmented, disabled |
| `line` / `line-2` | `0.91` / `0.85` | `0.27` / `0.34` | pemisah, border kontrol |
| `ink` / `ink-2` / `ink-3` | `0.20` / `0.45` / `0.58` | `0.95` / `0.74` / `0.62` | teks utama / sekunder / meta ≤12 px |
| `accent` / `accent-fill` | `oklch(0.62 0.17 36)` / `oklch(0.55 0.17 36)` | `oklch(0.74 0.15 40)` / `oklch(0.72 0.16 40)` | teks aksen / isi tombol utama |
| `accent-soft` | `oklch(0.96 0.03 36)` | `oklch(0.25 0.05 40)` | nav aktif, opsi terpilih |
| `success` / `warn` / `danger` (+ `-soft`) | 0.50 / 0.52 / 0.53 | 0.78 / 0.82 / 0.75 | status saja, bukan dekorasi |

Strategi: **restrained**. Aksen hanya untuk aksi utama, item nav aktif, seleksi, dan indikator.

## Typography

Satu keluarga: system stack bawaan Tailwind (`ui-sans-serif, system-ui, …` → SF Pro di Apple,
Segoe UI di Windows, Roboto di Android). Skala rem tetap: 12 (meta), 13/14 (bodi kompak), 15
(prosa soal), 16 (judul kartu), 18–20 (h2), 24 (h1 halaman), 30 (`.stat`), 48 (skor hasil).
`h1–h3` memakai `letter-spacing: -0.02em` dan `text-wrap: balance`. Angka memakai `tabular-nums`.

## Components

Kelas di `@layer components`. Setiap kontrol punya keadaan default, hover, focus-visible (ring
aksen), active (`scale(.97)`), disabled/`htmx-request` (opacity .55).

- Tombol: `btn` + `btn-primary` (aksen), `btn-secondary` (surface + border), `btn-ghost`,
  `btn-danger` (hanya untuk aksi destruktif utama; aksi destruktif dalam daftar memakai
  `btn-ghost text-danger` supaya halaman tidak penuh merah), `btn-sm`, `btn-lg`, `btn-icon`.
- Permukaan: `card` (+ `card-title`, `card-hover`, `p-0` untuk tabel/daftar penuh), `list-inset`.
- Form: `label`, `input`, `select`, `textarea`, `input-sm`, `hint`, `check`, `opt` (kartu opsi yang
  membungkus radio/checkbox, tersorot saat `:has(:checked)`).
- Tabel: `table` (+ `table-hover`); di dalam `card p-0` sel tepi mendapat padding kartu. Tabel
  menggulir sendiri di layar sempit (aturan global).
- Status: `badge` (+ `badge-success/-warn/-danger/-accent`), `dot`, `alert` (+ varian;
  `.eduscreen-error` ikut `alert-danger`), `empty`.
- Navigasi: `nav-item` (`[aria-current="page"]` = tint aksen), `nav-group`, `crumb`, `seg`
  (segmented control; `.aktif`, `[aria-current]`, atau `:has(:checked)`), `seg-tema` (varian
  ikon-saja; pemilih tema, duduk di kanan `page-header`).
- Menu aksi: `menu` (panel melayang), `menu-item` (+ `menu-item-danger`), `menu-pisah`.
- Editor: `editor-kaya` (bingkai), `editor-isi` (permukaan contenteditable),
  `editor-bar-melayang` (bilah alat tunggal), `editor-tombol`, `editor-pisah`,
  `editor-pegangan` (pegangan ubah ukuran gambar).
- Permukaan terbalik: `terbalik`.
- Lainnya: `page-header`, `section-title`, `meta`, `stat`, `prose` (mini, tanpa plugin).

## Keadaan hover & tekan

Satu mekanisme untuk seluruh kontrol yang bisa diklik — tombol, butir menu, item nav, segmented,
kartu opsi, baris tabel: lapis tinta transparan `--lapis` (10%) saat hover dan `--lapis-kuat`
(18%) saat ditekan, dipasang sebagai `background-image` sehingga latar asli kontrol (`surface`,
`accent-fill`, atau transparan) tetap terlihat di bawahnya.

Jangan memakai `surface-2` sebagai warna hover. Di mode gelap ia lebih gelap dari `surface`,
sehingga hover di atas panel menu justru meredup alih-alih menyala — persis cacat yang melahirkan
aturan ini. Lapis tinta benar di kedua tema tanpa perlu dua nilai.

## Permukaan terbalik

Elemen yang melayang di atas halaman dan harus terbaca sebagai lapisan tersendiri memakai kelas
`terbalik`: palet tema seberang, jadi bilah gelap di mode terang dan bilah terang di mode gelap.
Sekarang dipakai bilah alat editor; tooltip dan snackbar nanti ikut ke sini. Polanya sama dengan
bilah seleksi teks iOS dan snackbar Material — lapisan yang melawan latar terbaca sebagai lapisan,
sementara lapisan sewarna latar tenggelam ke dalamnya.

Nilainya bukan warna baru, melainkan argumen `light-dark()` yang ditukar, sehingga kontrasnya sudah
teruji. Karena token yang ditukar adalah token yang sama (`--ink`, `--line`, `--accent`), komponen
di dalamnya ikut terbalik tanpa aturan tambahan.

Satu jebakan yang wajib diingat kalau menambah token turunan: `var()` di dalam sebuah custom
property disubstitusi di elemen tempat property itu **dideklarasikan**. `--lapis` milik `:root`
karena itu sudah membekukan `--ink` milik `:root` dan tidak ikut terbalik — ia harus dideklarasikan
ulang di dalam `.terbalik`, kalau tidak sorotan hover di atas bilah putih ikut putih.

## Editor konten kaya

Formulir soal punya banyak kolom, jadi bilah alatnya **satu** untuk seluruh halaman, bukan satu
per kolom: ia melayang di bawah layar dan hanya muncul ketika sebuah kolom sedang disorot, seperti
bilah aksesori papan tik iOS. Tiap kolom kaya hanyalah `contenteditable` yang mencerminkan
`textarea` sungguhannya lewat `data-teks`; yang dikirim ke server tetap isi textarea.

Di ponsel bilah menyusut ke yang perlu saja — tebal, miring, daftar berbutir, daftar bernomor,
sisip gambar. Garis bawah, pangkat/indeks, sub-judul, bersihkan format, dan sunting HTML muncul
mulai 768 px.

Perintahnya `document.execCommand`: usang di spesifikasi, tapi didukung setiap peramban sasaran
dan tidak menambah satu pun pustaka rich-text. Keamanannya tidak bergeser: yang tersimpan tetap
HTML yang disanitasi allowlist di sisi tulis (`ContentSanitizer`, TC-22), jadi editor ini murni
urusan tampilan.

Gambar diatur dengan cara yang sama: mengklik gambar memilihnya, dan bilah berganti isi jadi
perkecil / perbesar / selebar kolom / ukuran asli / pindah. Ukurannya ditulis ke **atribut
`width`**, bukan `style` — allowlist sanitizer mengizinkan `width`/`height` pada `<img>` dan
membuang `style`, jadi ukuran lewat CSS inline akan hilang tanpa jejak saat simpan. Karena
tingginya tidak ikut ditulis, `.editor-isi img` dan `.prose img` wajib `height: auto` supaya
rasionya terjaga.

Selain tombol, gambar yang terpilih mendapat satu pegangan seret di sudut kanan bawahnya
(`.editor-pegangan`, `position: fixed` dengan koordinat dihitung Alpine karena `.editor-kaya`
memakai `overflow-hidden`). Memindahkannya dua ketukan — pilih gambar, ketuk "Pindah", ketuk titik
tujuan — bukan seret-dan-jatuhkan: seret bawaan di dalam `contenteditable` hanya andal dengan
tetikus dan praktis tidak bisa dipakai di layar sentuh.

## Aturan aksi baris

Berapa banyak aksi yang boleh berdiri sendiri di sebuah baris tabel atau daftar:

| Jumlah aksi | Bentuk |
| --- | --- |
| 1–2 | tombol ikon sebaris (`btn btn-secondary btn-sm btn-icon`), label lewat `title` + `aria-label` + `sr-only` |
| ≥3 | satu tombol ellipsis (`btn-ghost btn-icon`) yang membuka `.menu` |

Konvensi ini diambil dari PatternFly ("Do not use an overflow menu when there are 2 or fewer
actions available to the user") dan Carbon (kalau menunya berisi kurang dari tiga opsi, biarkan
inline supaya hemat satu klik dan aksinya terlihat sekilas). Material sampai ke angka yang sama
lewat lebar layar: di bawah 360 dp hanya 2 ikon yang muat, sisanya turun ke overflow.

Yang dihitung adalah **aksi maksimum untuk jenis baris itu**, bukan yang kebetulan tampil di
satu baris. Kalau sebagian baris menyembunyikan aksi lewat `th:if`, bentuknya tetap satu supaya
kolom aksi tidak berganti rupa antar baris.

Bentuk menunya mengikuti idiom Apple: pemicu `ellipsis.circle` (lingkaran tiga titik mendatar),
panel kartu membulat dengan bayangan `--sh-3`, tiap butir berupa label di kiri dan ikon di kanan,
aksi merusak berwarna `danger` dan dipisah `menu-pisah`. Panelnya `position: fixed` dengan
koordinat dihitung Alpine (`menuAksi` di `base.html`), sebab tabel menggulir sendiri
(`table { overflow-x: auto }`) dan akan memotong panel absolut; menu membalik ke atas pemicu
kalau ruang di bawah kurang. Tutup lewat klik di luar, `Escape`, gulir, dan ubah ukuran.

Pengecualian: aksi manipulasi langsung tidak digulung. Tombol urut naik/turun di perakit
Exercise tetap sebaris walau bersama Hapus jadi tiga — memindahkan urutan lewat menu membuat
tugas yang berulang jadi berkali-kali lipat lebih lambat. Ini sejalan dengan catatan NN/g bahwa
menyembunyikan aksi di balik ikon tiga titik menurunkan keterlihatan dan menambah usaha, jadi
yang digulung hanyalah aksi sekunder.

Penerapannya sekarang: menu di daftar soal Bank Soal master, tabel Pengguna, dan tabel
Assignment Guru; tombol ikon sebaris di tabel Paket (satu aksi), daftar soal Bank Soal Client
(dua aksi), Ruangan (dua), dan perakit Exercise (pengecualian di atas).

## Layout

`.shell` adalah grid tiga baris (banner demo, topbar, konten). Peran dengan sidebar (Eduscreen
Admin, Client Admin, Guru) mendapat `<aside class="sidebar">`; CSS `:has(> .sidebar)` mengubah grid
jadi dua kolom di ≥768 px, menyembunyikan topbar, dan meletakkan konten di kolom kedua
(`max-width: 76rem`). Tanpa sidebar (Siswa, anonim) konten satu kolom `max-width: 48rem`; mode
fokus (`.shell-fokus`, pengerjaan) menyempitkan ke `44rem`. Di bawah 768 px sidebar off-canvas
dengan scrim; `.sidebar-rail` (diingat di `localStorage`) menciutkan ke ikon saja.

Keadaan nav aktif datang dari model attribute `jalurAktif` (`JalurAktifAdvice`), dirender server.

## Motion

Durasi `--dur-1/2/3` = 150/200/250 ms untuk keadaan kontrol, `--dur-4` = 320 ms untuk lapisan
yang masuk dan keluar; semuanya 0 ms di `prefers-reduced-motion`. Easing `--ease`
(`cubic-bezier(.25,1,.5,1)`) untuk keadaan, `--ease-halus` (`cubic-bezier(.4,0,.2,1)`) untuk
lapisan.

Dua kurva itu ada karena `--ease` sangat front-loaded: pada separuh durasi ia sudah ~97% sampai,
jadi gerak sepanjang apa pun tetap terbaca menyentak. Bagus untuk hover dan tekan yang memang harus
terasa langsung, salah untuk sesuatu yang ingin terlihat masuk. `--ease-halus` membagi geraknya
lebih rata — pada 135 ms dari 320 ms baru sekitar setengah jalan.

Lapisan yang datang dari bawah layar memakai `naik` / `naik-mulai` / `naik-akhir` (naik 1 rem
sambil memudar dan membesar dari 0.96) — kebalikan `muncul` yang menurunkan alert dari atas. Tiga
kelas, bukan `@keyframes`, karena Alpine menggerakkan transisi dengan menempel dan melepas kelas;
durasinya tetap token, jadi `prefers-reduced-motion` ikut mematikannya tanpa aturan kedua. Hanya untuk keadaan: hover/active tombol dan nav, `htmx-added`
memudar masuk, `[role=alert]`/`[role=status]` muncul dengan `muncul`, sidebar geser, blok
`x-show` memakai `x-transition.opacity.duration.200ms`. Tidak ada animasi layout (width/height)
dan tidak ada koreografi muat halaman.

## Larangan

Tanpa side-stripe border, teks gradien, kaca buram dekoratif (blur hanya pada bar waktu sticky
pengerjaan), grid kartu identik, eyebrow uppercase di tiap seksi, hero-metric, dan tanpa
`dark:` di templat.
