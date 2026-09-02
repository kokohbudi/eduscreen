# Contract: Taksonomi, Bank Soal, Exercise, Impor & Gambar

**Cerita**: US2, US5, US6 | **Kebutuhan**: FR-012 sampai FR-026

## Taksonomi

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/subject` | Client Admin, Guru | — | `fragment` daftar gabungan `GLOBAL` + lokal, asal ditandai (FR-013) | — |
| POST | `/admin/subject` | Client Admin | `name` | `fragment` baris; `origin = CLIENT` | `400` |
| POST | `/eduscreen/subject` | Eduscreen Admin | `name` | `fragment` `<option>`; `origin = GLOBAL` | `400` bila kosong atau nama sudah dipakai Subject `GLOBAL` lain |
| POST | `/eduscreen/subject/{id}/nama` | Eduscreen Admin | `name` | `302` ke `/eduscreen/soal?subjectId={id}` | `404` bila Subject bukan `GLOBAL`, `400` bila kosong atau bentrok |
| GET | `/subject/{id}/topic` | Client Admin, Guru | — | `fragment` daftar Topic | `404` |
| POST | `/subject/{id}/topic` | Client Admin, Guru | `name` | `fragment` baris Topic lokal (FR-014) | `404`, `400` |

## Bank soal

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/soal` | Client Admin, Guru | `subjectId`, `topicId`, `q`, `page`, `exerciseId`, `type`, `sembunyikanTerpasang` | `fragment` hasil berhalaman | — |
| GET | `/soal/baru` | Client Admin, Guru | `topicId` | `page` editor | — |
| POST | `/soal` | Client Admin, Guru | `topicId`, `type`, `bodyHtml`, `explanationHtml`, `options[]` | `302` ke detail | `400` fragmen validasi |
| GET | `/soal/{id}` | Client Admin, Guru | — | `page` detail | `404` bila di luar Client |
| PUT | `/soal/{id}` | Client Admin, Guru | sama seperti POST | `fragment` detail | `404`, `400` |
| DELETE | `/soal/{id}` | Client Admin, Guru | — | `fragment` konfirmasi | `404` |

**Aturan mengikat**

- Pencarian `q` MUST menyentuh kolom teks polos turunan, bukan kolom HTML (FR-019, TC-25).
- `bodyHtml`, `explanationHtml`, dan tiap `options[].bodyHtml` MUST melewati sanitasi allowlist
  **saat menulis**; yang tersimpan adalah hasil sanitasi (TC-22, TC-23).
- Rumus MUST diterima sebagai LaTeX berdelimiter dan MUST NOT disimpan sebagai HTML hasil render
  (TC-24).
- Question `MULTIPLE_CHOICE` MUST punya ≥2 Option dan tepat 1 benar; selain itu `400` (FR-016).
- Question MUST melekat pada tepat satu Topic (FR-015).
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
| POST | `/exercise` | Guru | `title` | `302` ke perakit | `400` |
| GET | `/exercise/{id}` | Client Admin, Guru | — | `page` perakit | `404` |
| POST | `/exercise/{id}/item` | Guru | `questionId` | `fragment` daftar item | `404`; `409` bila terkunci |
| POST | `/exercise/{id}/item/topik` | Guru | `topicId` | `fragment` daftar item | `404`; `409` bila terkunci |
| POST | `/exercise/{id}/item/terpilih` | Guru | `questionIds[]` | `fragment` daftar item | `404`; `409` bila terkunci |
| DELETE | `/exercise/{id}/item/{questionId}` | Guru | — | `fragment` daftar item | `404`; `409` bila terkunci |
| PUT | `/exercise/{id}/urutan` | Guru | `questionIds[]` | `fragment` daftar item | `404`; `409` bila terkunci |
| POST | `/exercise/{id}/duplikat` | Guru | — | `302` ke Exercise baru | `404` |

**Aturan mengikat**

- Perakit MUST membolehkan penambahan Question dari Subject dan Topic mana pun di dalam Client,
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
| POST | `/katalog/adopsi` | Client Admin | `questionIds[]` atau `exerciseIds[]` | `fragment` ringkasan salinan | `400` |

**Aturan mengikat**

- Adopsi MUST membuat **salinan penuh** milik Client; perubahan pada master setelahnya MUST NOT
  merambat (FR-021, ADR-0001).
- Onboarding MUST membuat Client, akun Client Admin pertama beserta undangannya, dan menyalin
  paket yang dipilih (FR-020).
- Subject `GLOBAL` MUST NOT disalin — ia dibaca langsung; yang disalin adalah Topic, Question,
  dan Exercise.
