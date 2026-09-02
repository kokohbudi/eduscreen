# Rencana & Laporan Uji Beban

Uji beban **sudah dijalankan sekali**, pada taraf yang jauh di bawah sasaran, di satu mesin
pengembangan. Angka di [Tabel hasil](#tabel-hasil) adalah pengukuran sungguhan; yang belum ada
adalah pengujian pada taraf sasaran SC-006, dan hipotesis 10.000 Session serentak karena itu
**tetap belum terbukti maupun terbantahkan**.

Yang sudah dibuktikan lari pertama ini sempit tetapi bukan nol: jalur pengerjaan bertahan pada
100 permintaan bersamaan tanpa satu pun galat, dan tidak ada tanda kebuntuan kunci pada jalur
finalisasi. Yang belum: perilakunya pada dua sampai tiga orde besaran di atas itu, di perangkat
keras produksi, dengan latensi jaringan nyata.

## Sasaran yang diuji

`spec.md` §11 menyebut **SC-006**: sistem melayani 2.000 siswa mengerjakan serentak dalam satu
Client, dan sekitar 10.000 siswa serentak di seluruh platform, tanpa penurunan mutu layanan yang
dirasakan siswa. Spec sendiri menandai angka ini secara eksplisit sebagai hipotesis yang wajib
dibuktikan uji beban, bukan fakta yang sudah terukur — dan TC-42 mengulang penegasan yang sama:
topologi v1 adalah satu instance yang dipilih dengan asumsi bahwa beban 10.000 Session sebagian
besar jatuh ke database (auto-save adalah tulis kecil, halaman server-rendered nyaris tidak
menyimpan state di aplikasi), bukan ke instance aplikasinya. Uji beban ini ada untuk menguji
asumsi itu, bukan untuk mengonfirmasi apa yang sudah diyakini.

## Skenario uji

Diturunkan dari jalur yang benar-benar deras di sistem ini, bukan dari daftar endpoint yang
lengkap:

1. **`PUT /siswa/sesi/{id}/jawaban/{sessionQuestionId}`** — auto-save jawaban. Ini tabel dengan
   tulis paling deras di seluruh sistem: setiap perubahan jawaban satu Siswa pada satu soal
   memicu satu tulis ke `session_answer`. Dengan 10.000 Session berjalan, ini beban tulis yang
   paling menentukan apakah database sanggup.
2. **`GET /siswa/sesi/{id}/waktu`** — dipanggil tiap 30 detik oleh tiap sesi yang sedang
   berjalan (polling sisa waktu). Ini beban baca paling deras dan paling konstan: tidak
   bergantung pada aktivitas Siswa, hanya pada jumlah Session yang sedang `IN_PROGRESS`.
3. **`POST /siswa/assignment/{id}/mulai`** — lonjakan serentak di awal ujian: 30 Siswa menekan
   Mulai dalam 10 detik yang sama saat guru mengumumkan ujian dimulai. Beban ini bukan beban
   tetap seperti dua di atas, melainkan lonjakan pendek yang menguji apakah pembuatan Session
   (lazy instantiation, ADR-0002) dan penyusunan urutan soal tahan terhadap serbuan bersamaan.
4. **`GET /guru/assignment/{id}/rekap`** — memicu finalisasi borongan. Sesuai ADR-0002, Session
   difinalisasi saat diakses, bukan lewat scheduler; membuka rekap satu Assignment memfinalisasi
   seluruh Session miliknya yang sudah lewat waktu sekaligus. Ini satu-satunya endpoint di daftar
   ini yang bisa memicu banyak tulis (`result`, kemungkinan `score_audit`) dari satu permintaan
   baca, dan yang menyentuh lock pemesanan (lihat metrik lock wait di bawah).

## Metrik yang dicatat

- **Latensi p50/p95/p99** per endpoint di atas, diukur terpisah — bukan digabung, karena keempat
  endpoint punya profil beban yang berbeda (tulis kecil deras, baca deras, lonjakan pendek, baca
  yang memicu banyak tulis).
- **Throughput** (permintaan/detik) tercapai per endpoint pada tiap taraf beban serentak yang
  diuji.
- **Penggunaan koneksi pool** database selama uji — indikator paling awal saat database mulai
  jadi penyempit (bottleneck) sebagaimana diperkirakan ADR-0013.
- **Lock wait pada `exam_session`.** Jalur finalisasi (`ExamSessionRepository`, TC-lihat kode)
  memakai `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) untuk mencegah dua permintaan bersamaan
  atas Session yang sama menghasilkan dua Result (ADR-0002). Lock wait yang memanjang di sini
  adalah sinyal langsung bahwa finalisasi borongan lewat `GET /guru/assignment/{id}/rekap`
  bertabrakan dengan tulis auto-save yang sedang berjalan pada Session yang sama.
- **Pertumbuhan tabel `session_answer`** selama uji — memverifikasi asumsi bahwa auto-save adalah
  tulis kecil dan bukan sumber ledakan ukuran tabel yang tidak terduga.

## Tabel hasil

Satu baris per kombinasi skenario dan taraf beban.

### Lari 1 — 2026-08-31, mesin pengembangan

| Ihwal | Nilai |
| --- | --- |
| Perkakas | `scripts/uji-beban.py` (pustaka standar Python; tanpa dependensi baru) |
| Perintah | `./scripts/uji-beban.py --siswa 300 --jawaban 10 --paralel 100` |
| Lingkungan | satu instance aplikasi + PostgreSQL 16 dalam Docker, satu mesin (macOS, `darwin 25.6`) |
| Beban | 300 Session dimulai, 100 permintaan bersamaan, 10 auto-save + 10 polling waktu per Session |
| Durasi | 18,8 detik total |
| Galat | **0** — seluruh 6.900 permintaan menjawab `200`/`302` |

| Skenario | Permintaan | p50 (ms) | p95 (ms) | p99 (ms) | maks (ms) | Galat (%) |
| --- | --- | --- | --- | --- | --- | --- |
| `POST /siswa/assignment/{id}/mulai` | 300 | 231,5 | 742,4 | 1182,9 | 1343,6 | 0 |
| `PUT /siswa/sesi/{id}/jawaban/{sqId}` | 3.000 | 236,5 | 760,6 | 996,1 | 1394,1 | 0 |
| `GET /siswa/sesi/{id}/waktu` | 3.000 | 53,4 | 162,2 | 262,1 | 593,4 | 0 |
| `POST /siswa/sesi/{id}/selesai` | 300 | 147,1 | 432,8 | 563,6 | 666,6 | 0 |

**Yang bisa dibaca dari angka ini.** Endpoint waktu — yang paling sering dipanggil, tiap 30 detik
per Session berjalan — adalah yang paling murah, dan itu memang bentuk yang diinginkan: ia hanya
membaca satu baris. Auto-save dan pembuatan Session berada di kelas yang sama, dan keduanya
menulis; pembuatan Session menulis paling banyak karena ia membekukan seluruh snapshot sekaligus.
Ekor p99 di bawah 1,4 detik pada 100 permintaan bersamaan di satu mesin pengembangan yang juga
menjalankan databasenya sendiri.

**Yang TIDAK bisa dibaca dari angka ini.** Lari ini memakai 300 Session, bukan 2.000, apalagi
10.000. Klien, aplikasi, dan database berbagi satu mesin, sehingga sebagian latensi di atas
adalah rebutan CPU antar-ketiganya, bukan sifat aplikasinya. Tidak ada latensi jaringan sungguhan.
Angka ini karena itu **tidak boleh** dipakai sebagai bukti SC-006 terpenuhi; ia hanya menunjukkan
tidak ada kegagalan struktural pada taraf yang diuji.

## Kriteria lulus/gagal

**Lulus** bila, pada beban target (2.000 Session serentak dalam satu Client; sekitar 10.000
Session serentak di seluruh platform):

- p95 dan p99 tiap endpoint tetap dalam rentang yang membuat pengerjaan ujian terasa responsif
  bagi Siswa (auto-save dan polling waktu tidak boleh terasa tersendat saat ujian berlangsung).
- Tidak ada galat (error) pada `PUT .../jawaban/...` — kegagalan auto-save berarti jawaban Siswa
  berisiko hilang, dan itu bertentangan langsung dengan SC-003.
- Lock wait pada `exam_session` tidak menumpuk tanpa batas selama finalisasi borongan
  berlangsung.

**Gagal** bila salah satu di atas tidak terpenuhi.

Bila uji beban gagal, langkah berikutnya **bukan** menulis ulang topologi ke arsitektur mendatar.
ADR-0013 dan TC-42 sudah menegaskan ini secara eksplisit: pemicu untuk pindah ke topologi
mendatar adalah kebutuhan deploy tanpa memutus ujian yang sedang berjalan, bukan angka beban
uji ini. Kegagalan uji beban lebih dulu memicu penyetelan pada level yang sama — menambah index
yang relevan dengan pola baca/tulis yang terukur gagal, menaikkan ukuran connection pool, atau
mengelompokkan (batch) tulis auto-save bila lock wait pada `exam_session` terbukti jadi
penyempit. Menulis ulang topologi baru dipertimbangkan bila penyetelan pada level ini terbukti
tidak cukup, dan itu dipertimbangkan lewat proses ADR baru, bukan sebagai reaksi langsung
terhadap satu hasil uji beban.
