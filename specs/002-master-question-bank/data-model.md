# Phase 1: Data Model — Question Bank Master Eduscreen

**Feature**: `002-master-question-bank` | **Date**: 2026-08-31

Tidak ada tabel baru dan tidak ada entitas baru. Seluruh konsep di spesifikasi ini memakai tabel yang
sudah berdiri sejak `V2__content.sql`, dibedakan oleh kepemilikan (`client_id` null berarti milik
Eduscreen) dan oleh satu kolom keadaan terbit yang ditambahkan di sini.

Skema dasar tabel `subject`, `topic`, `question`, `question_option`, `exercise`, dan `exercise_item`
tidak diulang di sini; ia sudah terdokumentasi di
[specs/001-student-exercise-portal/data-model.md](../001-student-exercise-portal/data-model.md).

## Perubahan skema — `V5__master_publishing.sql`

```sql
alter table question add column published_at timestamptz;
alter table exercise add column published_at timestamptz;

-- Keadaan terbit hanya bermakna bagi konten master. Konten milik sebuah sekolah tidak pernah
-- "diterbitkan" ke siapa pun: ia sudah terlihat seluruh Guru di Client itu sejak ditulis (FR-004).
alter table question add constraint question_publish_master_only
    check (published_at is null or client_id is null);
alter table exercise add constraint exercise_publish_master_only
    check (published_at is null or client_id is null);

-- Jalur baca terpanas: katalog Client menyaring konten master terbit per Topic (FR-074, SC-015).
create index question_master_published on question (topic_id)
    where client_id is null and published_at is not null and deleted_at is null;

-- Penanda "sudah pernah diadopsi" (FR-076): dibaca per halaman katalog, bukan per katalog penuh.
create index question_adopted_source on question (client_id, source_question_id)
    where source_question_id is not null;
```

## Entitas

### Question master

Baris `question` ber-`client_id` null. Bentuknya identik dengan Question milik Client — kolom yang
sama, validasi yang sama, sanitasi yang sama.

| Kolom | Perilaku pada baris master |
| --- | --- |
| `client_id` | Selalu `null`. Inilah penanda kepemilikan Eduscreen |
| `topic_id` | Menunjuk Topic ber-`origin = GLOBAL` |
| `published_at` | `null` selama digarap; terisi jam server saat diterbitkan (FR-066) |
| `source_question_id` | Selalu `null` — konten master tidak pernah lahir dari adopsi (FR-082) |
| `deleted_at` | Soft delete; menghilangkan dari ruang kerja dan katalog, tidak menyentuh salinan Client (FR-065) |

**Aturan bentuk** tidak berubah dari Question milik Client dan tetap ditegakkan di
`QuestionService.validateOptions`: `MULTIPLE_CHOICE` butuh ≥2 Option dengan tepat 1 benar, `ESSAY`
tidak boleh punya Option sama sekali (FR-062).

### Topic Eduscreen

Baris `topic` ber-`origin = GLOBAL` dan `client_id` null. Sudah didukung penuh oleh check constraint
`topic_origin_matches_owner` dan factory `TopicEntity.forGlobal(...)` yang sudah ada; yang belum ada
hanyalah jalan membuatnya, karena `TaxonomyService` baru punya `createClientTopic`.

Asimetri yang wajib dijaga: Subject `GLOBAL` **tidak pernah** disalin saat adopsi, Topic **selalu**
disalin (BR-O02, AC-O02). `ContentAdoptionService.copyTopic` sudah melakukannya dengan benar.

### Exercise master (paket)

Baris `exercise` ber-`client_id` null, beserta `exercise_item`-nya.

| Kolom | Perilaku pada baris master |
| --- | --- |
| `client_id` | Selalu `null` |
| `published_at` | Gerbang FR-069 dievaluasi sebelum diisi |
| `locked_at` | Selalu `null` selamanya. Exercise master tidak pernah menjadi Assignment, jadi tidak pernah terkunci (FR-073) |

### Keadaan terbit

Bukan entitas — sifat yang melekat pada dua tabel di atas lewat `published_at`.

**Transisi:**

```text
                 terbitkan (gerbang FR-069 untuk Exercise)
   belum terbit ─────────────────────────────────────────► terbit
   (published_at null)  ◄─────────────────────────────────  (published_at terisi)
                 tarik dari peredaran (FR-068)
```

- **terbitkan**: `published_at := clock.now()`. Untuk Exercise master, ditolak bila masih memuat
  Question ber-`published_at` null; pesan penolakan menyebut Question penyebabnya (FR-069).
- **tarik**: `published_at := null`. Tidak menyentuh satu pun baris milik Client (FR-068) — salinan
  hasil adopsi adalah baris terpisah yang tidak punya tautan hidup ke master (ADR-0001).
- **hapus**: `deleted_at := clock.now()`, lewat mekanisme soft delete yang sudah ada. Konsekuensinya
  bagi Client identik dengan penarikan (FR-065).

### Katalog

Bukan entitas dan bukan tabel — sebuah pandangan yang dibentuk dua query:

1. Question master terbit, disaring per Subject dan Topic serta dicari pada `body_text` (FR-074).
2. Himpunan `source_question_id` yang sudah dimiliki Client yang sedang melihat, dibatasi pada id
   yang tampil di halaman itu saja (FR-076).

Katalog adalah satu-satunya tempat data ber-`client_id` null terbaca dari dalam konteks sebuah
Client. Setiap query yang membentuknya menyebut `client_id is null` secara harfiah di JPQL-nya,
bukan lewat parameter yang kebetulan kosong — `= null` tidak pernah cocok di SQL, dan repo ini sudah
memisahkan `search` dari `searchMaster` untuk alasan itu.

## Method repository baru

| Repository | Method | Untuk |
| --- | --- | --- |
| `QuestionRepository` | `searchPublishedMaster(subjectId, topicId, pattern, pageable)` | Katalog Client (FR-074) |
| `QuestionRepository` | `findPublishedMasterById(id)` | Gerbang adopsi (FR-067) |
| `QuestionRepository` | `findAdoptedSourceIds(clientId, ids)` | Penanda sudah diadopsi (FR-076, FR-077) |
| `QuestionRepository` | `findUnpublishedInExercise(exerciseId)` | Gerbang penerbitan paket (FR-069) |
| `ExerciseRepository` | `searchPublishedMaster(pattern, pageable)` | Katalog Client (FR-074) |
| `ExerciseRepository` | `findPublishedMasterById(id)` | Gerbang adopsi (FR-067) |

`searchMaster` yang sudah ada tetap dipakai apa adanya untuk ruang kerja Eduscreen Admin, yang justru
harus melihat draf. Filter `subjectId` ditambahkan lewat subquery ke `topic`, karena `question` tidak
membawa `subject_id` sendiri.

## Invarian yang dijaga

| Invarian | Dijaga oleh |
| --- | --- |
| Hanya konten master yang boleh punya keadaan terbit | Check constraint `*_publish_master_only` |
| Konten belum terbit tidak pernah teradopsi | `findPublishedMasterById` di `ContentAdoptionService`; onboarding lewat jalur yang sama |
| Exercise master terbit tidak memuat Question belum terbit | Gerbang `MasterPublishingService` (FR-069) |
| Subject `GLOBAL` tidak pernah disalin | `ContentAdoptionService.copyTopic` yang sudah ada |
| Adopsi tidak pernah menjadi tautan hidup | Tidak ada kode yang membaca `source_question_id` untuk menulis; ia hanya dibaca untuk penanda |
| Konten Client tidak pernah menjadi konten master | Tidak ada jalur tulis yang menyetel `client_id` menjadi null |
