# Contract: Penilaian Essay & Rekap Ruangan

**Cerita**: US2, US4 | **Kebutuhan**: FR-047 sampai FR-057

## Penilaian essay

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/guru/assignment/{id}/penilaian` | Guru Ruangan itu | — | `page` antrean jawaban essay belum dinilai | `404` |
| PUT | `/guru/jawaban/{sessionAnswerId}/nilai` | Guru Ruangan itu | `essayScore` (0–100) | `fragment` baris + skor Result terbarui | `404`; `400` di luar rentang |

**Aturan mengikat**

- Guru yang tidak ditugaskan di Ruangan Assignment itu MUST mendapat `404` (FR-004 batasnya,
  BR-G01).
- `essayScore` MUST bilangan bulat 0–100; poin soal itu menjadi `essayScore / 100` (FR-050).
- Saat essay terakhir di satu sesi dinilai, Result MUST berpindah `PENDING_REVIEW` → `FINAL`
  (FR-051).
- Setiap perubahan nilai MUST menulis satu baris `score_audit` hanya-sisip berisi pelaku, waktu,
  nilai lama, dan nilai baru — termasuk perubahan atas Result yang sudah `FINAL` (FR-052).
- Perhitungan ulang Result MUST terjadi pada permintaan yang sama, bukan ditunda.

## Rekap Ruangan

| Metode | Jalur | Peran | Masukan | Keluaran | Kegagalan |
| --- | --- | --- | --- | --- | --- |
| GET | `/guru/assignment/{id}/rekap` | Guru Ruangan itu | — | `page` rekap | `404` |
| GET | `/guru/assignment/{id}/rekap/siswa/{studentId}` | Guru Ruangan itu | — | `page` seluruh pengerjaan Siswa itu | `404` |
| GET | `/guru/ruangan/{id}/latihan` | Guru Ruangan itu | — | `page` aktivitas Practice, terpisah dari rekap nilai | `404` |

### Kolom rekap

| Kolom | Isi |
| --- | --- |
| Siswa | nama anggota Ruangan |
| Status | `NOT_STARTED` \| `IN_PROGRESS` \| `COMPLETED` \| `EXPIRED` |
| Pengerjaan | jumlah sesi |
| Skor resmi | tertinggi di antara Result-nya; `0` bila `NOT_STARTED` |
| Penilaian | `FINAL` \| `PENDING_REVIEW` |

**Aturan mengikat**

- Rekap MUST dibangun dari **daftar anggota Ruangan**, bukan dari daftar sesi. Siswa tanpa sesi
  MUST tampil `NOT_STARTED` dengan skor `0` **tanpa** membuat baris sesi (FR-056).
- Membuka rekap MUST memfinalisasi seluruh sesi Assignment itu yang sudah lewat
  `effective_deadline` (FR-057, ADR-0002).
- Finalisasi borongan MUST berjalan **satu transaksi per sesi**, bukan satu transaksi panjang
  yang mengunci seluruh Ruangan (TC-21).
- Finalisasi MUST idempoten; dua pembaca bersamaan MUST menghasilkan tepat satu Result (TC-18,
  TC-19).
- Skor resmi seorang Siswa MUST yang **tertinggi** di antara seluruh pengerjaannya; seluruh
  pengerjaan tetap bisa dibuka (FR-053).
- Result ber-`kind = PRACTICE` MUST NOT muncul di rekap nilai; ia tampil di halaman aktivitas
  latihan terpisah (FR-054).

### Perhitungan skor

```text
poin(MCQ)   = is_correct ? 1 : 0
poin(essay) = essay_score / 100          -- null → 0 selama PENDING_REVIEW
score       = Σ poin ÷ total_questions
```

- Setiap soal bernilai sama; tidak ada bobot per soal dan tidak ada pengurangan nilai (FR-047).
- Soal tidak terjawab dihitung salah saat finalisasi dan masuk `unanswered_count` (FR-049).
- `score` MUST disimpan sebagai hasil hitung dan MUST NOT dihitung ulang saat dibaca, agar angka
  historis tidak bergeser bila aturan skoring berubah.
