# Verifikasi Index

Pemeriksaan T112: setiap index yang disebut `specs/001-student-exercise-portal/data-model.md`
benar-benar ada di skema, dan query yang menjadi alasannya benar-benar memakainya.

Dijalankan terhadap PostgreSQL 16 lewat `docker-compose.yml`, setelah `V1`–`V4` diterapkan.
`EXPLAIN` dijalankan dengan `set enable_seqscan = off` supaya perencana tidak memilih
sequential scan hanya karena tabelnya masih kecil di lingkungan pengembangan — yang diperiksa
adalah **apakah index bisa dipakai**, bukan apakah ia menang pada 12 baris.

## Keberadaan

| `data-model.md` | Nama index di skema | Ada |
| --- | --- | --- |
| `exam_session (assignment_id, student_id, attempt_number)` unik | `exam_session_attempt_unique` | ya |
| `exam_session (assignment_id, status)` | `exam_session_by_assignment_status` | ya |
| `session_answer (session_question_id)` unik | `session_answer_unique` | ya |
| `session_question (session_id, position)` | `session_question_ordered` | ya |
| `result (session_id)` unik | `result_session_unique` | ya |
| `question (client_id, topic_id)` | `question_by_client_topic` | ya |
| pencarian atas `question.body_text` | `question_body_text_search` (GIN `tsvector`) | ya |
| `ruangan_member (user_id, member_role)` | `ruangan_member_by_user` | ya |
| `ruangan_member (ruangan_id, member_role)` | `ruangan_member_by_ruangan` | ya |
| `assignment (ruangan_id, status, expires_at)` | `assignment_by_ruangan` | ya |

## Pemakaian

| Query | Rencana |
| --- | --- |
| Finalisasi borongan saat rekap dibuka: `exam_session` disaring `assignment_id` + `status` | `Index Scan using exam_session_by_assignment_status` |
| Portal Siswa: `ruangan_member` disaring `user_id` + `member_role` | `Index Scan using ruangan_member_by_user` |
| Penelusuran bank soal: `question` disaring `client_id`, lalu `lower(body_text) like '%…%'` | `Index Scan using question_by_client_topic`, dengan `like` sebagai `Filter` |

## Temuan yang perlu dicatat

`question_body_text_search` adalah index GIN atas `to_tsvector('simple', body_text)`, dan
penelusuran bank soal yang berjalan sekarang **tidak memakainya**. Penelusuran memakai
`lower(body_text) like '%kata%'`; pola berawalan wildcard tidak bisa dilayani index GIN
`tsvector` maupun B-tree mana pun.

Yang benar-benar terjadi pada query itu: `client_id` dilayani `question_by_client_topic`
sehingga pemindaian terbatas pada bank soal satu Client, lalu `like` berjalan sebagai filter di
atas hasilnya. Untuk bank soal berukuran ribuan baris per Client — ukuran yang wajar di v1 —
biayanya kecil dan tidak menjadi masalah.

Index itu tetap dibiarkan ada karena ia yang dituju `data-model.md`, dan karena ia langsung
berguna begitu salah satu dari dua jalan di bawah diambil:

1. **Ganti bentuk pencariannya** menjadi pencocokan kata:
   `to_tsvector('simple', body_text) @@ plainto_tsquery('simple', :q)`. Index langsung terpakai.
   Konsekuensinya, "alja" berhenti mencocokkan "aljabar" — pencarian menjadi per kata, bukan per
   potongan.
2. **Tambahkan `pg_trgm`** dan buat index GIN trigram atas `lower(body_text)`, yang bisa
   melayani `like '%…%'`. Konsekuensinya, produksi harus memasang extension, dan itu butuh hak
   yang tidak selalu dimiliki akun aplikasi.

Catatan yang sengaja tidak dikerjakan: penjelasan ini **tidak** ditambahkan sebagai komentar di
`V2__content.sql`. Migrasi yang sudah pernah dijalankan tidak boleh disunting lagi — Flyway
menghitung checksum atas seluruh isi berkas, termasuk komentarnya, dan satu baris tambahan
membuat setiap database yang sudah menerapkannya menolak start. Penjelasan seperti ini hidup di
sini; migrasi hanya berubah lewat berkas baru bernomor lebih tinggi.

Pemicunya adalah pengukuran, bukan selera: bila uji beban (`docs/load-test-report.md`)
menunjukkan penelusuran bank soal menjadi jalur lambat, ambil jalan 1 lebih dulu — ia tidak
menambah ketergantungan apa pun.

---

# Verifikasi Index — V5 (konten master)

Pemeriksaan T038 untuk `specs/002-master-question-bank`: kedua index parsial yang ditambahkan
`V5__master_publishing.sql` benar-benar dipakai perencana pada volume yang menjadi alasannya
(SC-015: 5.000 Question master).

Dijalankan terhadap PostgreSQL 16 lewat `docker-compose.yml`, pada replika bertabel 25.000 baris
— 5.000 baris master (10% di antaranya masih digarap) dan 20.000 salinan milik 40 Client.
Berbeda dengan pemeriksaan V1–V4 di atas, `enable_seqscan` **tidak** dimatikan: volumenya sudah
cukup besar sehingga perencana memilih index atas kemauannya sendiri, dan itu justru yang ingin
dibuktikan.

## 1. Katalog: Question master terbit per Topic

Index: `question_master_published on question (topic_id) where client_id is null and published_at is not null and deleted_at is null`

```text
Bitmap Heap Scan on q (actual rows=100)
  Recheck Cond: ((topic_id = ...) AND (client_id IS NULL)
                 AND (published_at IS NOT NULL) AND (deleted_at IS NULL))
  Heap Blocks: exact=55
  ->  Bitmap Index Scan on q_master_published (actual rows=100)
        Index Cond: (topic_id = ...)
Execution Time: 0.094 ms
```

Dipakai. Predikat parsialnya diserap seluruhnya, sehingga index hanya memuat baris master terbit
yang belum dihapus — 48 kB untuk 4.500 baris yang memenuhi syarat, bukan seluruh tabel.

## 2. Penanda "sudah pernah diadopsi"

Index: `question_adopted_source on question (client_id, source_question_id) where source_question_id is not null`

```text
Bitmap Index Scan on q_adopted_source (actual rows=500)
  Index Cond: (client_id = ...)
Execution Time: 0.190 ms
```

Dipakai. Baris master tidak pernah masuk index ini karena `source_question_id`-nya null — itu
sebabnya predikat parsialnya ada: 160 kB untuk 20.000 salinan, tanpa satu pun baris master.

## Catatan

`question` yang sesungguhnya membawa lebih banyak kolom, sehingga jumlah heap block per baris
lebih besar daripada replika ini. Yang dibuktikan pemeriksaan ini adalah **pilihan perencana**
dan **selektivitas index**, bukan angka waktu absolutnya.
