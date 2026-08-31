# Contract: Penerbitan & Siklus Hidup Assignment

**Cerita**: US2, US3, US7 | **Kebutuhan**: FR-027 sampai FR-033

## Endpoint

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/guru/assignment` | Guru | `ruanganId`, `status` | `page` daftar | — |
| GET | `/guru/assignment/baru` | Guru | `exerciseId` | `page` formulir penerbitan | `404` |
| POST | `/guru/assignment` | Guru | lihat **Muatan** | `302` ke detail (status `DRAFT`) | `400`, `404` |
| GET | `/guru/assignment/{id}` | Guru | — | `page` detail + rekap | `404` |
| PUT | `/guru/assignment/{id}` | Guru | muatan lengkap | `fragment` detail | `404`; `409` bila bukan `DRAFT` |
| POST | `/guru/assignment/{id}/terbit` | Guru | — | `fragment` detail berstatus `PUBLISHED` | `404`; `409`; `422` validasi mode |
| PATCH | `/guru/assignment/{id}/perpanjang` | Guru | `expiresAt` | `fragment` detail | `404`; `422` bila lebih awal dari nilai sekarang |
| POST | `/guru/assignment/{id}/tutup` | Guru | — | `fragment` detail berstatus `CLOSED` | `404`; `409` |
| DELETE | `/guru/assignment/{id}` | Guru | — | `302` ke daftar | `404`; `409` bila bukan `DRAFT` |

## Muatan penerbitan

| Field | Wajib | Aturan |
| --- | --- | --- |
| `exerciseId` | ya | Exercise milik Client yang sama, berisi ≥1 Question (FR-025) |
| `ruanganId` | ya | Ruangan `ACTIVE` tempat Guru ditugaskan (FR-003) |
| `mode` | ya | `QUIZ` \| `PRACTICE` (FR-028) |
| `timerDurationMinutes` | ya untuk `QUIZ` | boleh kosong untuk `PRACTICE` (FR-030) |
| `expiresAt` | ya | harus di masa depan saat penerbitan; ditafsirkan dalam zona waktu Client |
| `maxAttempts` | ya untuk `QUIZ` | ≥1; diabaikan untuk `PRACTICE` (FR-054) |
| `shuffleQuestions` | ya | boolean (FR-031) |
| `shuffleOptions` | ya | boolean (FR-031) |
| `revealAnswersAt` | ya untuk `QUIZ` | `AFTER_SUBMIT` \| `AFTER_EXPIRATION` (FR-055) |

## Validasi saat penerbitan

Dijalankan pada `POST /guru/assignment/{id}/terbit`, bukan saat perakitan Exercise (ADR-0003).

| Pemeriksaan | Kegagalan |
| --- | --- |
| Exercise memuat ≥1 Question | `422` menyebut Exercise kosong (FR-025) |
| `mode = PRACTICE` → tidak ada Question `ESSAY` | `422` **menyebut Question mana** penyebabnya (FR-029) |
| `mode = PRACTICE` → setiap Question punya `explanationHtml` | `422` menyebut Question mana (FR-029) |
| `mode = QUIZ` → `timerDurationMinutes` terisi | `422` (FR-030) |
| `expiresAt` di masa depan | `422` |
| Guru ditugaskan di Ruangan itu dan Ruangan `ACTIVE` | `404` (FR-003, FR-010) |

**Efek samping penerbitan**: mengisi `exercise.locked_at` bila masih kosong (FR-026).

## Aturan sesudah terbit

- Hanya `expiresAt` yang MUST bisa diubah, dan hanya **diperpanjang**; memajukannya `422`
  (FR-032).
- `mode`, `exerciseId`, `timerDurationMinutes`, `maxAttempts`, dan kedua sakelar pengacakan MUST
  terkunci (FR-032).
- Assignment `PUBLISHED` atau `CLOSED` MUST NOT bisa dihapus; hanya `DRAFT` (FR-032).
- Perpanjangan MUST menghitung ulang `effective_deadline` **sekali** untuk sesi yang masih
  `IN_PROGRESS`, dan MUST NOT menyentuh sesi yang sudah terminal (FR-043).
- `POST .../tutup` MUST memfinalisasi seluruh sesi `IN_PROGRESS` di Assignment itu dengan
  `terminal_reason = EXPIRATION_REACHED` (FR-033).

## Penerbitan borongan

| Metode | Jalur | Masukan | Keluaran |
| --- | --- | --- | --- |
| POST | `/guru/assignment/borongan` | `exerciseId`, `ruanganIds[]`, muatan waktu | `fragment` ringkasan Assignment yang terbentuk |

Satu Assignment tetap menyasar tepat satu Ruangan; endpoint ini hanya kemudahan antarmuka yang
menghasilkan N Assignment terpisah (FR-027).
