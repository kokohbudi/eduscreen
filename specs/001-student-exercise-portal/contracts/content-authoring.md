# Contract: Taksonomi, Bank Soal, Exercise, Impor & Gambar

**Cerita**: US2, US5, US6 | **Kebutuhan**: FR-012 sampai FR-026

## Bank soal

Taksonomi sekarang tiga lapis: Subject (label) › Paket › Topic › Question. Paket adalah satuan
yang ditulis, dijual, dan diadopsi; Topic milik satu Paket, bukan milik Subject (ADR-0018).

Peran: Client Admin dan Guru. Ruang kerja Eduscreen memakai rute kembar berawalan
`/eduscreen/bank-soal`, template yang sama dengan `basePath` berbeda — pola yang sudah dipakai
`MasterContentController` sekarang. Tombol terbit dan tarik hanya muncul di ruang kerja master
(FR-066, FR-067).

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/bank-soal` | Client Admin, Guru | — | `page` tabel Paket lintas Subject, dengan jumlah soal per Paket, plus formulir buat Paket | — |
| GET | `/bank-soal?subjectId={id}` | Client Admin, Guru | `subjectId` | `page` tabel yang sama tersaring ke satu Subject | `404` |
| POST | `/bank-soal/paket` | Client Admin, Guru | `title`, `subjectId` atau `subjectName` | `302` ke isi Paket | `400` |
| GET | `/bank-soal/paket/{id}` | Client Admin, Guru | — | `page` isi Paket: soal dikelompokkan per Topic | `404` bila di luar Client |
| POST | `/bank-soal/paket/{id}/topic` | Client Admin, Guru | `title` | `fragment` daftar Topic | `404`, `400` |
| GET | `/bank-soal/paket/{id}/soal/baru` | Client Admin, Guru | `topicId` | `page` editor, `topicId` sebagai induk | `404` |
| POST | `/bank-soal/paket/{id}/soal` | Client Admin, Guru | `topicId`, `type`, `bodyHtml`, `explanationHtml`, `options[]` | `302` ke detail | `400` fragmen validasi |
| PUT | `/bank-soal/soal/{id}` | Client Admin, Guru | sama seperti POST | `fragment` detail | `404`, `400` |
| DELETE | `/bank-soal/soal/{id}` | Client Admin, Guru | — | `fragment` konfirmasi | `404` |
| GET | `/bank-soal/paket/{id}/pinjam` | Client Admin, Guru | `q`, `page` | `fragment` panel pinjam: cari soal di Paket lain milik Client | `404` |
| POST | `/bank-soal/paket/{id}/pinjam` | Client Admin, Guru | `questionIds[]` atau `sourceTopicId` | `fragment` ringkasan salinan | `400` |

**Aturan mengikat**

- Paket baru MUST lahir dengan tepat satu Topic bernama `Topik 1` (AC-B01).
- Question MUST menunjuk Topic yang `paketId`-nya sama dengan `paketId` Question itu; kombinasi
  lain MUST ditolak (AC-B02).
- Meminjam soal dari Paket lain MUST menghasilkan Question baru milik Paket tujuan dengan
  `sourceQuestionId` menunjuk soal asal; mengubah salinan MUST NOT mengubah soal asal (AC-B03).
- Soal yang `sourceQuestionId`-nya sudah ada di Paket tujuan MUST disembunyikan dari daftar
  pinjam Paket itu (AC-B04).
- Pencarian `q` MUST menyentuh kolom teks polos turunan, bukan kolom HTML (FR-019, TC-25).
- `bodyHtml`, `explanationHtml`, dan tiap `options[].bodyHtml` MUST melewati sanitasi allowlist
  **saat menulis**; yang tersimpan adalah hasil sanitasi (TC-22, TC-23).
- Rumus MUST diterima sebagai LaTeX berdelimiter dan MUST NOT disimpan sebagai HTML hasil render
  (TC-24).
- Question `MULTIPLE_CHOICE` MUST punya ≥2 Option dan tepat 1 benar; selain itu `400` (FR-016).
- Question MUST melekat pada tepat satu Topic (BR-Q02).
- `DELETE` MUST menghilangkan soal dari pencarian tanpa memutus Exercise, Assignment, atau
  pengerjaan yang sudah memakainya (FR-018).

## Gambar

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| POST | `/gambar` | Client Admin, Guru | berkas | `fragment` berisi `imageId` | `400` tipe atau ukuran ditolak |
| GET | `/gambar/{id}` | terautentikasi & berhak | — | aliran berkas, `Cache-Control: private` | `404` |

**Aturan mengikat**

- Tipe MUST ditentukan dari **magic bytes**, bukan ekstensi maupun `Content-Type` (TC-27).
- Gambar MUST di-encode ulang saat disimpan untuk membuang metadata dan muatan menumpang (TC-27).
- `GET /gambar/{id}` MUST memeriksa `client_id` dan peran pemanggil; MUST NOT ada penyajian
  statis langsung dari direktori penyimpanan (TC-26).
- Penyimpanan MUST lewat `FileStoragePort` (TC-28).

## Exercise

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/exercise` | Client Admin, Guru | `q`, `page` | `page` daftar se-Client (FR-004) | — |
| POST | `/exercise` | Guru | `title` (opsional; kosong → judul bawaan, BR-E06) | `302` ke perakit | — |
| GET | `/exercise/{id}` | Client Admin, Guru | `paketId`, `topicId`, `q`, `page`, `exerciseId`, `type`, `sembunyikanTerpasang` (panel penelusuran) | `page` perakit | `404` |
| POST | `/exercise/{id}/item` | Guru | `questionId` | `fragment` daftar item | `404`; `409` bila terkunci |
| POST | `/exercise/{id}/item/topik` | Guru | `topicId` | `fragment` daftar item | `404`; `409` bila terkunci |
| POST | `/exercise/{id}/item/terpilih` | Guru | `questionIds[]` | `fragment` daftar item | `404`; `409` bila terkunci |
| DELETE | `/exercise/{id}/item/{questionId}` | Guru | — | `fragment` daftar item | `404`; `409` bila terkunci |
| PUT | `/exercise/{id}/urutan` | Guru | `questionIds[]` | `fragment` daftar item | `404`; `409` bila terkunci |
| PUT | `/exercise/{id}/judul` | Guru | `title` | `204` | `404`; `409` bila terkunci |
| GET | `/exercise/{id}/soal/baru` | Guru | — | `fragment` editor soal tanpa Paket (BR-E05) | `404` |
| GET | `/exercise/{id}/soal/{questionId}` | Guru | — | `fragment` editor soal lepas | `404`, termasuk soal berpenempatan |
| POST | `/exercise/{id}/soal` | Guru | `type`, `bodyHtml`, `explanationHtml`, `optionBody[]`, `correctIndex` | `fragment` daftar item | `404`; `409` bila terkunci |
| PUT | `/exercise/{id}/soal/{questionId}` | Guru | sama seperti POST | `fragment` daftar item | `404`, termasuk soal berpenempatan |
| POST | `/exercise/{id}/duplikat` | Guru | — | `302` ke Exercise baru | `404` |

**Aturan mengikat**

- Perakit MUST membolehkan penambahan Question dari Paket dan Topic mana pun di dalam Client,
  berpindah bebas dalam satu sesi perakitan (FR-024).
- Perakit MUST membolehkan penambahan beberapa Question terpilih maupun seluruh Question satu
  Topic dalam satu tindakan (BR-E01). Urutan yang masuk MUST mengikuti urutan yang dikirim.
- Panel penelusuran perakit MUST bisa disaring per tipe soal dan MUST bisa menyembunyikan
  Question yang sudah terpasang di Exercise itu. Saringan MUST ikut terbawa antar-halaman
  hasil; penyaringan MUST NOT mengubah aturan penerbitan Practice, yang tetap ditegakkan
  di jalur penerbitan (BR-M04).
  Soal yang sudah terpasang MUST dilewati, dan Topic yang bukan milik Client pemanggil MUST
  menghasilkan 0 soal, bukan kebocoran maupun galat yang membedakannya dari Topic kosong (TC-36).
- Setiap perubahan pada Exercise ber-`locked_at` MUST dijawab `409` disertai tawaran duplikasi
  (FR-026).
- Exercise kosong MUST NOT bisa diterbitkan; divalidasi di jalur penerbitan (FR-025).

## Impor massal

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/admin/impor` | Client Admin | — | `page` unggah + tautan templat | — |
| POST | `/admin/impor/pratinjau` | Client Admin | berkas Excel/CSV | `fragment` pratinjau: baris valid + daftar kegagalan bernomor baris | `400` format tak terbaca; `413` lebih dari 500 baris |
| POST | `/admin/impor/simpan` | Client Admin | token pratinjau | `fragment` ringkasan | `400` token kedaluwarsa |

**Aturan mengikat**

- Impor MUST diproses **sinkron**; MUST NOT ada antrean pekerjaan latar (TC-45, ADR-0014).
- Berkas lebih dari 500 baris MUST ditolak sebelum diproses, dengan pesan yang menyebut batas itu
  dan meminta pengguna memecah berkas (FR-023).
- Kegagalan satu baris MUST NOT membatalkan baris lain; penyimpanan hanya memasukkan yang valid
  (FR-022).
- Konten impor MUST melewati sanitasi yang sama dengan editor (TC-22).

## Konten master & onboarding

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/eduscreen/client` | Eduscreen Admin | — | `page` daftar Client | — |
| POST | `/eduscreen/client` | Eduscreen Admin | `name`, `timezone`, `adminEmail`, `paketIds[]` | `302` ke detail Client | `400` |
| GET | `/katalog` | Client Admin | `subjectId` | `page` katalog master | — |
| POST | `/katalog/adopsi` | Client Admin | `paketIds[]` | `fragment` ringkasan salinan | `400` |

**Aturan mengikat**

- Adopsi MUST membuat **salinan penuh** milik Client; perubahan pada master setelahnya MUST NOT
  merambat (FR-021, ADR-0001).
- Onboarding MUST membuat Client, akun Client Admin pertama beserta undangannya, dan menyalin
  paket yang dipilih (FR-020).
- Subject `GLOBAL` MUST NOT disalin — ia dibaca langsung; yang disalin adalah Paket, Topic,
  Question, dan Option.
