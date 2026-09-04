# Contract: Autentikasi, Akun & Ruangan

**Cerita**: US1 | **Kebutuhan**: FR-001 sampai FR-011

## Autentikasi

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/login` | publik | — | `page` formulir login | — |
| POST | `/login` | publik | `email`, `password` | `302` ke portal sesuai peran | `401` fragmen galat seragam; `429` bila terkena batas laju |
| POST | `/logout` | terautentikasi | — | `302` ke `/login` | — |
| GET | `/undangan/{token}` | publik | token undangan | `page` penetapan password | `404` bila token tidak sah atau kedaluwarsa |
| POST | `/undangan/{token}` | publik | `password` | `302` ke `/login` | `400` fragmen validasi |
| GET | `/lupa-password` | publik | — | `page` formulir | — |
| POST | `/lupa-password` | publik | `email` | `page` konfirmasi **seragam** | — |
| GET | `/reset/{token}` | publik | token reset | `page` penetapan password | `404` bila tidak sah |
| POST | `/reset/{token}` | publik | `password` | `302` ke `/login` | `400` fragmen validasi |

**Aturan mengikat**

- Kegagalan login MUST seragam: akun tidak ada, password salah, dan akun nonaktif menghasilkan
  pesan dan waktu tanggap yang sama.
- `POST /lupa-password` MUST selalu menjawab konfirmasi yang sama, ada atau tidak ada akunnya —
  agar tidak menjadi alat memeriksa keberadaan email.
- Batas laju per akun **dan** per alamat IP, dengan penundaan menaik lalu penguncian sementara
  (TC-33).
- Password mentah MUST NOT masuk log maupun pesan galat (TC-06).
- Seluruh pemeriksaan kredensial lewat `IdentityProviderPort`; controller MUST NOT menyentuh
  `AuthenticationManager` langsung (TC-07).

## Portal per peran

| Metode | Jalur | Peran | Keluaran |
| --- | --- | --- | --- |
| GET | `/` | terautentikasi | `302` ke portal sesuai peran |
| GET | `/siswa` | Siswa | `page` — Assignment aktif dari seluruh Ruangan + riwayat hasil (FR-058) |
| GET | `/guru` | Guru | `page` — Ruangan yang ditugaskan + Assignment terbaru |
| GET | `/admin` | Client Admin | `page` — ringkasan Ruangan, pengguna, bank soal |
| GET | `/eduscreen` | Eduscreen Admin | `page` — daftar Client dan konten master |

## Manajemen pengguna (Client Admin)

| Metode | Jalur | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- |
| GET | `/admin/pengguna` | filter peran, pencarian, halaman | `page` daftar | — |
| POST | `/admin/pengguna` | `email`, `fullName`, `role` | `fragment` baris baru; undangan terkirim (FR-009) | `400` email duplikat atau tidak sah |
| GET | `/admin/pengguna/{id}` | — | `page` detail | `404` bila di luar Client |
| PUT | `/admin/pengguna/{id}` | `fullName`, `role` | `fragment` baris diperbarui | `404` |
| POST | `/admin/pengguna/{id}/nonaktif` | — | `fragment` baris; riwayat tetap utuh (FR-011) | `404` |
| POST | `/admin/pengguna/{id}/undang-ulang` | — | `fragment` konfirmasi | `404` |

## Ruangan

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/admin/ruangan` | Client Admin | — | `page` daftar | — |
| GET | `/admin/ruangan/baru` | Client Admin | — | `page` formulir Ruangan baru: nama, Guru, Siswa (BR-U05) | — |
| POST | `/admin/ruangan` | Client Admin | `name`, `guruIds[]`, `siswaIds[]` | `302` ke detail Ruangan | `400` |
| GET | `/admin/ruangan/{id}` | Client Admin | — | `page` anggota | `404` bila di luar Client |
| POST | `/admin/ruangan/{id}/anggota` | Client Admin | `userIds[]`, `memberRole` | `fragment` daftar anggota | `404`; `409` bila Ruangan `ARCHIVED` |
| DELETE | `/admin/ruangan/{id}/anggota/{userId}` | Client Admin | — | `fragment` daftar anggota | `404` |
| POST | `/admin/ruangan/{id}/arsip` | Client Admin | — | `fragment` baris berstatus `ARCHIVED` | `404` |

**Aturan mengikat**

- Satu Siswa MUST bisa menjadi anggota banyak Ruangan; satu Ruangan MUST bisa punya banyak Guru
  (FR-008).
- Ruangan `ARCHIVED` MUST menolak anggota baru dan Assignment baru, tetapi riwayatnya tetap
  terbaca (FR-010).
- Seluruh query MUST menyaring `client_id`; Ruangan milik Client lain menghasilkan `404`
  (FR-001, TC-36).

## Akses dukungan

| Metode | Jalur | Peran | Keluaran |
| --- | --- | --- | --- |
| POST | `/admin/akses-dukungan` | Client Admin | `fragment` status; berlaku 4 jam (FR-006) |
| DELETE | `/admin/akses-dukungan` | Client Admin | `fragment` status dicabut |
| GET | `/admin/akses-dukungan/jejak` | Client Admin | `page` daftar pembacaan Eduscreen Admin |

**Aturan mengikat**

- Selama jendela aktif, Eduscreen Admin MUST hanya bisa **membaca**; setiap upaya menulis
  ditolak (FR-006, ADR-0015).
- Setiap pembacaan MUST tercatat dan bisa ditampilkan kepada Client.
