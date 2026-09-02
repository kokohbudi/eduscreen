# Contract: Ruang Kerja Konten Master Eduscreen

**Cerita**: US1, US2, US3 | **Kebutuhan**: FR-060 sampai FR-073

Seluruh rute di bawah berada di bawah `/eduscreen/**`, yang sudah dibatasi
`hasRole("EDUSCREEN_ADMIN")` oleh `SecurityConfig`. Tidak ada perubahan aturan otorisasi rute.

## Topic Eduscreen

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/eduscreen/subject/{id}/topic` | Eduscreen Admin | — | `fragment` daftar Topic `GLOBAL` | `404` |
| POST | `/eduscreen/subject/{id}/topic` | Eduscreen Admin | `name` | `fragment` baris Topic; `origin = GLOBAL` | `404`, `400` |

**Aturan mengikat**

- Topic yang lahir di sini MUST ber-`origin = GLOBAL` dan tanpa pemilik Client (FR-061).
- Subject induk MUST ber-`origin = GLOBAL`; Subject milik sebuah Client MUST dijawab `404` (TC-09).

## Question master

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/eduscreen/soal` | Eduscreen Admin | `subjectId`, `topicId`, `q`, `page` | `fragment` hasil berhalaman, draf dan terbit bercampur dengan penanda | — |
| GET | `/eduscreen/soal/baru` | Eduscreen Admin | `topicId` | `page` editor | — |
| POST | `/eduscreen/soal` | Eduscreen Admin | `topicId`, `type`, `bodyHtml`, `explanationHtml`, `options[]` | `302` ke detail | `400` fragmen validasi |
| GET | `/eduscreen/soal/{id}` | Eduscreen Admin | — | `page` detail | `404` bila bukan konten master |
| PUT | `/eduscreen/soal/{id}` | Eduscreen Admin | sama seperti POST | `fragment` detail | `404`, `400` |
| DELETE | `/eduscreen/soal/{id}` | Eduscreen Admin | — | `fragment` konfirmasi | `404` |

**Aturan mengikat**

- Question yang lahir di sini MUST tanpa pemilik Client, dan MUST melekat pada Topic `GLOBAL`
  (FR-060, FR-061).
- Aturan bentuk MUST identik dengan bank soal Client: `MULTIPLE_CHOICE` butuh ≥2 Option dengan tepat
  1 benar, `ESSAY` tanpa Option (FR-062). Divalidasi oleh kode yang sama, bukan salinannya.
- `bodyHtml`, `explanationHtml`, dan tiap `options[].bodyHtml` MUST melewati sanitasi allowlist yang
  sama saat menulis; yang tersimpan adalah hasil sanitasi (FR-063, TC-22, TC-23).
- Pencarian `q` MUST menyentuh kolom teks polos turunan, bukan kolom HTML (FR-064, TC-25).
- Rute ini MUST NOT pernah membaca baris yang membawa pemilik Client; permintaan atas Question milik
  sebuah sekolah MUST dijawab `404`, tanpa membedakannya dari yang tidak ada (FR-080, TC-09).
- `DELETE` MUST menghilangkan Question dari ruang kerja dan dari katalog seluruh Client, dan MUST
  NOT menyentuh satu pun salinan hasil adopsi (FR-065).

## Paket master

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/eduscreen/paket` | Eduscreen Admin | `q`, `page` | `page` daftar paket | — |
| POST | `/eduscreen/paket` | Eduscreen Admin | `title` | `302` ke perakit | `400` |
| GET | `/eduscreen/paket/{id}` | Eduscreen Admin | — | `page` perakit | `404` |
| POST | `/eduscreen/paket/{id}/item` | Eduscreen Admin | `questionId` | `fragment` daftar item | `404` |
| DELETE | `/eduscreen/paket/{id}/item/{questionId}` | Eduscreen Admin | — | `fragment` daftar item | `404` |
| PUT | `/eduscreen/paket/{id}/urutan` | Eduscreen Admin | `questionIds[]` | `fragment` daftar item | `404` |

**Aturan mengikat**

- Perakit MUST membolehkan penambahan Question master dari Subject dan Topic mana pun, berpindah
  bebas dalam satu sesi perakitan (FR-071).
- Paket master MUST NOT pernah terkunci; aturan `409` bagi Exercise ber-`locked_at` (FR-026) MUST
  NOT berlaku di sini, karena paket master tidak pernah menjadi Assignment (FR-073).
- Question yang ditambahkan MUST berupa konten master; Question milik sebuah Client MUST dijawab
  `404`.

## Penerbitan

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| POST | `/eduscreen/soal/{id}/terbit` | Eduscreen Admin | — | `fragment` baris dengan penanda terbit | `404` |
| POST | `/eduscreen/soal/{id}/tarik` | Eduscreen Admin | — | `fragment` baris tanpa penanda terbit | `404` |
| POST | `/eduscreen/paket/{id}/terbit` | Eduscreen Admin | — | `fragment` status paket | `404`; `400` memuat Question belum terbit |
| POST | `/eduscreen/paket/{id}/tarik` | Eduscreen Admin | — | `fragment` status paket | `404` |

**Aturan mengikat**

- Waktu terbit MUST berasal dari jam server, tidak pernah dari masukan klien (TC-12).
- Menerbitkan paket MUST ditolak `400` selama masih memuat Question belum terbit, dan pesannya MUST
  menyebut Question penyebabnya (FR-069).
- Paket kosong MUST NOT bisa diterbitkan (FR-072).
- Menarik dari peredaran MUST NOT mengubah, menghapus, maupun menandai satu pun baris milik Client
  (FR-068). Ini MUST dibuktikan tes, bukan diasumsikan dari ketiadaan kode.
- Mengubah konten master yang sudah terbit MUST NOT merambat ke Client yang sudah mengadopsinya
  (FR-070, ADR-0001).
