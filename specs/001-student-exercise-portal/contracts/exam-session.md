# Contract: Portal Siswa & Pengerjaan

**Cerita**: US2, US3, US7 | **Kebutuhan**: FR-034 sampai FR-046, FR-055, FR-058

Ini permukaan paling sensitif di sistem. Setiap endpoint di bawah tunduk penuh pada empat lapis
anti-IDOR (Prinsip I) dan otoritas waktu server (Prinsip II).

## Endpoint

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/siswa` | Siswa | — | `page` Assignment aktif lintas Ruangan + riwayat hasil (FR-058) | — |
| GET | `/siswa/assignment/{id}` | Siswa anggota Ruangan | — | `page` sampul: judul, mode, sisa waktu, jumlah pengerjaan | `404` |
| POST | `/siswa/assignment/{id}/mulai` | Siswa anggota Ruangan | — | `302` ke `/siswa/sesi/{sessionId}` | `404`; `409` batas pengulangan; `410` Assignment tutup |
| GET | `/siswa/sesi/{sessionId}` | pemilik sesi | — | `page` pengerjaan | `404` |
| GET | `/siswa/sesi/{sessionId}/soal/{position}` | pemilik sesi | — | `fragment` satu soal | `404`; `409` pada Practice bila melompat |
| PUT | `/siswa/sesi/{sessionId}/jawaban/{sessionQuestionId}` | pemilik sesi | `selectedOptionId` \| `essayText` | `fragment` status simpan; pada Practice ikut pembahasan | `404`; `409` terkunci dengan jawaban berbeda; `410` lewat waktu |
| POST | `/siswa/sesi/{sessionId}/selesai` | pemilik sesi | — | `302` ke hasil | `404`; `410` |
| GET | `/siswa/sesi/{sessionId}/waktu` | pemilik sesi | — | `fragment` sisa waktu dari server | `404` |
| POST | `/siswa/sesi/{sessionId}/heartbeat` | pemilik sesi | — | `204` | `404` |
| GET | `/siswa/sesi/{sessionId}/hasil` | pemilik sesi | — | `page` hasil sesuai aturan penyingkapan | `404`; `409` bila sesi belum terminal |

## Aturan mengikat

### Pembuatan sesi — `POST .../mulai`

1. Sesi MUST tercipta **hanya** di sini; tidak ada pembuatan massal (FR-034, ADR-0002).
2. Bila ada sesi `IN_PROGRESS` milik Siswa itu untuk Assignment itu, endpoint MUST mengembalikan
   sesi tersebut, **bukan** membuat yang baru (FR-037).
3. `effective_deadline` MUST dihitung dan **dibekukan** saat itu juga (FR-041).
4. Snapshot MUST dibuat sekaligus: urutan `session_question` dan `option_order` per soal, sesuai
   sakelar pengacakan Assignment (FR-035).
5. Untuk `QUIZ`, `attempt_number` berikutnya MUST ≤ `maxAttempts`; selain itu `409` (FR-053).
   Untuk `PRACTICE`, tanpa batas (FR-054).
6. Assignment `CLOSED` atau sudah lewat `expiresAt` MUST dijawab `410`.

### Penyimpanan jawaban — `PUT .../jawaban/{sessionQuestionId}`

1. MUST idempoten: **upsert** berkunci `session_question_id`. Kiriman ulang dengan isi identik
   MUST dijawab sukses sebagai no-op (FR-036, TC-20).
2. Pada `PRACTICE`, `session_question.locked_at` terisi saat penyimpanan pertama. Kiriman
   berikutnya dengan isi **berbeda** MUST `409`; dengan isi **sama** MUST sukses (FR-038).

   Ini yang membuat antrean coba-ulang aman: Siswa yang jaringannya tersendat tidak boleh melihat
   galat untuk jawaban yang sebenarnya sudah tersimpan.
3. Pada `QUIZ`, jawaban MUST bisa diubah sampai `selesai` (FR-039).
4. Bila `now > effective_deadline`, jawaban MUST ditolak `410` **dan** sesi difinalisasi di
   permintaan yang sama (FR-044).
5. Untuk `MULTIPLE_CHOICE`, `is_correct` MUST dihitung dan disimpan saat itu.
6. Isi jawaban MUST NOT masuk log (TC-44).

### Waktu

1. Sisa waktu MUST selalu berasal dari server; `GET .../waktu` adalah satu-satunya sumber
   kebenaran, dan hitung mundur di halaman hanya menampilkannya (FR-042, TC-15).
2. Sisa waktu yang ditampilkan MUST sudah memperhitungkan pemangkasan oleh `expiresAt` sejak
   detik pertama (FR-041).
3. `POST .../heartbeat` MUST hanya menjaga sesi login tetap hidup selama sesi `IN_PROGRESS`, dan
   MUST NOT memperpanjang batas waktu pengerjaan (TC-32).

### Otorisasi

1. `sessionId` milik Siswa lain dan `sessionId` yang tidak ada MUST menghasilkan `404` identik
   (TC-09).
2. Kepemilikan dan `client_id` MUST masuk klausa query; sesi milik orang lain MUST NOT pernah
   termuat ke memori (TC-08).
3. Siswa MUST NOT bisa membaca Question di luar `session_question` miliknya (FR-005).

### Penyingkapan hasil

| Mode | Yang tampil | Kapan |
| --- | --- | --- |
| `PRACTICE` | benar/salah + pembahasan per soal | seketika setelah jawaban tersimpan |
| `QUIZ`, `AFTER_SUBMIT` | skor + kunci + pembahasan | setelah sesi terminal |
| `QUIZ`, `AFTER_EXPIRATION` | skor saja setelah selesai; kunci + pembahasan | setelah `expiresAt` Assignment |

Skor pada Result `PENDING_REVIEW` MUST ditandai sementara (FR-051).

### Ketahanan koneksi

1. Klien MUST mengantre kiriman gagal dan mencobanya ulang, dengan indikator koneksi terlihat
   (FR-046).
2. MUST NOT ada penyimpanan jawaban di peramban untuk disinkronkan belakangan; pengerjaan
   membutuhkan koneksi (ADR-0006).
3. Membuka kembali sesi setelah terputus MUST mengembalikan snapshot dan jawaban utuh, dengan
   sisa waktu yang sudah berkurang (FR-045).
