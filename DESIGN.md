# Design

Sistem visual Eduscreen. Sumber kebenaran tunggalnya adalah
`src/main/resources/static/css/input.css`; dokumen ini merangkumnya supaya perubahan UI berikutnya
memakai kosakata yang sama, bukan menciptakan varian baru.

## Visual Theme & Atmosphere

Antarmuka produktivitas mengikuti pola Apple Human Interface Guidelines: huruf sistem, ruang kosong
murah hati, satu aksen hangat yang dipakai hemat, permukaan berlapis dengan bayangan lembut, kontrol
standar. Terang adalah default; gelap mengikuti `prefers-color-scheme` tanpa tombol. Mobile first:
ukuran dasar adalah ukuran sentuh (44 px), mengecil ke kepadatan meja kerja hanya di ≥768 px.

## Color Palette

Semua warna OKLCH, didefinisikan sebagai variabel mentah di `:root` lalu dipetakan ke namespace
Tailwind lewat `@theme inline`. Templat memakai `bg-bg`, `text-ink`, `border-line`, dst.; tidak
pernah `dark:`.

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
  (segmented control; `.aktif`, `[aria-current]`, atau `:has(:checked)`).
- Lainnya: `page-header`, `section-title`, `meta`, `stat`, `prose` (mini, tanpa plugin).

## Layout

`.shell` adalah grid tiga baris (banner demo, topbar, konten). Peran dengan sidebar (Eduscreen
Admin, Client Admin, Guru) mendapat `<aside class="sidebar">`; CSS `:has(> .sidebar)` mengubah grid
jadi dua kolom di ≥768 px, menyembunyikan topbar, dan meletakkan konten di kolom kedua
(`max-width: 76rem`). Tanpa sidebar (Siswa, anonim) konten satu kolom `max-width: 48rem`; mode
fokus (`.shell-fokus`, pengerjaan) menyempitkan ke `44rem`. Di bawah 768 px sidebar off-canvas
dengan scrim; `.sidebar-rail` (diingat di `localStorage`) menciutkan ke ikon saja.

Keadaan nav aktif datang dari model attribute `jalurAktif` (`JalurAktifAdvice`), dirender server.

## Motion

Durasi `--dur-1/2/3` = 150/200/250 ms, easing `cubic-bezier(.25,1,.5,1)`; semuanya 0 ms di
`prefers-reduced-motion`. Hanya untuk keadaan: hover/active tombol dan nav, `htmx-added`
memudar masuk, `[role=alert]`/`[role=status]` muncul dengan `muncul`, sidebar geser, blok
`x-show` memakai `x-transition.opacity.duration.200ms`. Tidak ada animasi layout (width/height)
dan tidak ada koreografi muat halaman.

## Larangan

Tanpa side-stripe border, teks gradien, kaca buram dekoratif (blur hanya pada bar waktu sticky
pengerjaan), grid kartu identik, eyebrow uppercase di tiap seksi, hero-metric, dan tanpa
`dark:` di templat.
