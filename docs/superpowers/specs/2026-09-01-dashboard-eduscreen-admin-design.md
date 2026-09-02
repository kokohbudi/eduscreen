# Dashboard Eduscreen Admin

**Tanggal**: 2026-09-01
**Status**: disetujui, menunggu rencana implementasi

## Masalah

`/eduscreen` hari ini memikul tiga urusan sekaligus: dua tombol nav, daftar Client, dan form
onboarding yang panjang. Ia juga satu-satunya tempat navigasi hidup — begitu Eduscreen Admin masuk
ke `/eduscreen/soal` atau `/eduscreen/paket`, tidak ada jalan pindah menu selain mengklik logo di
pojok kiri atas.

Akibat yang paling terasa muncul setelah pengelolaan Subject global dipindahkan ke ruang kerja
konten master: tidak ada satu pun petunjuk di layar utama bahwa Subject dibuat di balik tombol
bernama "Question". Orang yang mencarinya harus sudah tahu.

Selain navigasi, ada kelas pekerjaan yang tidak pernah terlihat sampai gagal. Paket master yang
memuat Question belum terbit pasti ditolak gerbang FR-069, tapi itu baru ketahuan saat tombol
Terbit ditekan. Subject global tanpa Topic adalah jalan buntu — di `/eduscreen/soal` tombol
"+ Soal baru" hanya muncul setelah Topic dipilih — dan layar tempat Subject itu dibuat tidak
mengatakan apa-apa soal itu.

## Hasil yang dituju

`/eduscreen` menjadi dashboard: papan pekerjaan yang macet, dengan navigasi tetap yang berlaku di
seluruh `/eduscreen/**`.

## Batas yang mengunci desain

**FR-080 + BR-P04**: Eduscreen Admin tidak boleh membaca data operasional Client mana pun; bukan
sekadar tidak ditampilkan, tidak boleh ada jalannya. Pengecualiannya hanya akses dukungan berizin
Client Admin, berbatas waktu, teraudit (BR-P05, ADR-0015).

Konsekuensinya dashboard ini tidak memuat jumlah Siswa, Assignment berjalan, nilai, maupun
keaktifan sekolah. Yang halal: data Client sebagai entitas (nama, zona, status) dan konten master
milik Eduscreen sendiri.

## Keputusan

| # | Keputusan |
|---|---|
| K1 | `/eduscreen` menjadi dashboard; daftar Client dan form onboarding pindah utuh ke `/eduscreen/client`. |
| K2 | Tugas dashboard: pekerjaan tertunda lebih dulu, navigasi peran kedua (susunan C). |
| K3 | Nav tetap di header `layout/base.html`, berlaku di seluruh halaman `/eduscreen/**`. |
| K4 | Antrean "butuh perhatian" memuat empat baris, seluruhnya berbasis keadaan, bukan umur. |
| K5 | Antrean kosong → bloknya tidak dirender sama sekali; kartu naik jadi isi utama. |
| K6 | `/eduscreen/soal` mendapat penyaring `status`, supaya baris antrean draf mendarat tepat sasaran. |

Ambang berbasis umur ("draf lebih dari 7 hari") ditolak: angkanya karangan dan akan diperdebatkan
terus. Keempat baris antrean adalah keadaan struktural — sesuatu yang memang macet, bukan sesuatu
yang kebetulan lama.

## Rute

| Rute | Sekarang | Sesudah |
|---|---|---|
| `GET /eduscreen` | alias, render `eduscreen/client.html` | dashboard, render `eduscreen/dashboard.html` |
| `GET /eduscreen/client` | alias yang sama | daftar Client + form onboarding |
| `POST /eduscreen/client` | redirect `/eduscreen/client?dibuat=…` | tak berubah |
| `GET /eduscreen/soal` | filter `subjectId`, `topicId`, `q`, `page` | tambah `status` bernilai `DRAF` atau `TERBIT`; kosong berarti semua |
| `/eduscreen/paket**`, `/eduscreen/subject**` | — | tak berubah |

`EduscreenAdminController.java:45` yang memetakan dua jalur sekaligus dipecah jadi dua method.
Dashboard mendapat controller sendiri, `EduscreenDashboardController`: ia membaca dari tiga tempat
(Client, Question, Exercise) sementara `EduscreenAdminController` urusannya onboarding. Menumpuk
keduanya memberi satu kelas dua alasan untuk berubah.

"Dashboard" **tidak** masuk `CONTEXT.md`. Glosarium itu bahasa domain — Subject, Ruangan,
Assignment. Nama halaman bukan konsep domain.

## Data

Satu service baru, `EduscreenDashboardService`, mengembalikan satu record ringkasan. Controller
tidak menghitung apa pun.

**Tiga kartu:**

| Kartu | Tulisan di layar | Sumber |
|---|---|---|
| Client | "3 sekolah &rarr;" | `ClientRepository.count()` |
| Konten master | "12 Question &rarr;" | hitungan Question `client_id is null` |
| Paket | "2 terbit &rarr;" | hitungan Exercise `client_id is null` dengan `published_at is not null` |

Kartu pintasan hanya membawa satu angka masing-masing (susunan C). Hitungan draf tidak muncul di
kartu — tempatnya di antrean, dan mengulangnya di dua tempat membuat dua sumber kebenaran untuk
angka yang sama.

**Empat baris antrean:**

| Baris | Query |
|---|---|
| Question master masih draf | hitungan yang sama dengan kartu; nol query tambahan |
| Paket macet di gerbang FR-069 | Exercise draf yang punya item ber-`published_at is null`. Satu `@Query` join `exercise_item` → `question`, `distinct` — **bukan** perulangan `findUnpublishedInExercise` per paket, yang N+1 |
| Paket siap terbit | Exercise draf, jumlah item bukan nol, nol item belum terbit |
| Subject global buntu | `SubjectEntity` `origin = GLOBAL` dengan `not exists` Topic di bawahnya |

Lima query hitung dan tiga query daftar, seluruhnya `@Query` di repository yang sudah ada. Nol
tabel baru, nol kolom baru, nol migrasi.

Setiap query menyaring `client_id is null` kecuali `ClientRepository.count()` yang membaca tabel
`client` itu sendiri. Tidak ada yang menyentuh Question, Exercise, Ruangan, atau Session milik
sekolah.

Tiap baris antrean menyebut hitungan plus maksimal lima nama; sisanya "…dan N lainnya" yang
menautkan ke halaman terkait. Tanpa batas, satu paket macet yang terlupakan berbulan-bulan membuat
dashboard menggulir sepuluh layar.

## Tata letak

```
Dashboard
├─ Butuh perhatian ──────────────── dirender HANYA bila ada isinya
│   2 Question master masih draf                    [draf]
│   Paket IPA Kelas 5 memuat soal belum terbit      [macet]
│   Paket Latihan Pecahan siap diterbitkan          [siap]
│   Subject "Kimia Kelas 11" belum punya Topic      [buntu]
└─ tiga kartu pintasan: Client · Konten master · Paket
```

Nav header dipasang di dalam blok `sec:authorize` di `layout/base.html` — polanya sudah ada di sana
untuk nama pengguna dan tombol Keluar (`base.html:41`). Empat butir: Dashboard, Client, Konten
master, Paket.

Penanda menu aktif tidak bisa membaca URL langsung; Thymeleaf 3.1 mencabut `#httpServletRequest`.
Tiap controller menaruh `menuAktif` di model. `MasterContentController` sudah punya tempatnya:
method `isiJalur()` yang mengisi `basePath` dan kawan-kawan.

Templat: satu baru `eduscreen/dashboard.html`; `eduscreen/client.html` kehilangan blok nav dua
tombolnya (`client.html:13-21`) karena nav sudah di header.

## Aturan baru

`business-rules.md` mendapat satu entri, sejalan dengan `BR-O03`/`BR-O04`:

> **BR-O05** — Pekerjaan konten master yang macet karena aturan penerbitan harus terlihat tanpa
> dicari. Paket yang isinya belum terbit (FR-069), paket kosong (FR-072), dan Subject global tanpa
> Topic adalah jalan buntu yang tidak menjelaskan dirinya sendiri di layar tempat ia dibuat.

Tanpa ini, tes antrean tidak punya pengenal aturan yang bisa disebut, dan `AcceptanceCriteriaCoverageTest`
(TC-39) menolaknya.

## Tes

**`EduscreenDashboardIT`** — di atas `PostgresTestBase`, PostgreSQL sungguhan (TC-38).

- Tiap baris antrean muncul persis saat keadaannya benar dan hilang saat tidak: paket macet berhenti
  macet setelah isinya diterbitkan; paket siap terbit hilang setelah diterbitkan; Subject buntu
  hilang begitu Topic pertama lahir.
- Antrean kosong → seluruh blok absen, bukan blok kosong.
- **Pengunci FR-080**: satu Client disiapkan dengan Question draf, paket draf yang macet, dan
  Subject lokal tanpa Topic — persis tiga keadaan yang memicu antrean. Tidak satu pun boleh muncul,
  dan tidak satu pun boleh ikut terhitung di kartu. Tanpa tes ini, satu `@Query` yang lupa
  `client_id is null` lolos diam-diam dan membocorkan pekerjaan sekolah ke layar Eduscreen.

**`EduscreenNavRenderTest`**

- Nav header muncul untuk `EDUSCREEN_ADMIN` di keempat halaman `/eduscreen/**`.
- Nav header tidak muncul untuk Guru, Siswa, dan Client Admin di halaman mereka masing-masing.

**`ContentIdorTest`** — `/eduscreen` sebagai dashboard tetap `403` bagi ketiga peran Client. Rutenya
berubah isi, pagarnya dibuktikan ulang.

**`MasterContentIT`** — penyaring `status=draf` menghasilkan hanya soal draf; soal terbit tidak ikut.

`base.html` dipakai keempat peran. Salah pasang `sec:authorize` membuat Guru atau Siswa melihat
tautan `/eduscreen/**`. Diklik memang tetap `403` — `SecurityConfig:52` yang menjaga, bukan templat
— tapi menampilkan pintu yang tidak boleh dibuka adalah cacat tersendiri. Karena itu tes nav
menguji keempat peran, bukan hanya jalur positif.

## Di luar lingkup

- Angka apa pun yang menghitung data operasional Client (Siswa, Assignment, nilai, keaktifan) —
  ditolak FR-080, bukan ditunda.
- Angka agregat adopsi ("paket ini ditarik 12 sekolah") — menghitung baris milik Client; keputusan
  tersendiri yang belum diambil.
- Antrean berbasis umur — ditolak, ambangnya karangan.
- Nav serupa untuk peran Guru, Client Admin, dan Siswa. Portal mereka juga tanpa nav, tapi itu
  pekerjaan lain.
