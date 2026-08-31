---
status: accepted
---

# Filter `client_id` ditulis eksplisit, soft delete ditegakkan otomatis

Dua penyaringan yang menyentuh hampir setiap query diperlakukan berbeda dengan sengaja. Soft delete (`deletedAt`) ditegakkan otomatis lewat `@SQLRestriction`. Batas tenant (`client_id`) ditulis eksplisit di tanda tangan setiap method repository — tanpa filter Hibernate, tanpa Row-Level Security.

## Alasan

Godaannya adalah membuat keduanya otomatis: mustahil lupa memfilter, dan kode pemanggil jadi bersih. Kami menolaknya karena kedua risiko itu tidak setara.

Melewatkan filter soft delete berarti Guru melihat soal yang sudah dihapus — mengganggu, terlihat segera, mudah diperbaiki. Melewatkan filter `client_id` berarti bank soal satu sekolah terbaca oleh sekolah lain, dan kebocoran semacam itu bisa berjalan berbulan-bulan tanpa ada yang menyadarinya.

Untuk risiko sebesar itu, keterlihatan lebih berharga daripada kenyamanan. `findByIdAndStudentIdAndClientId` memaksa penulisnya memikirkan batas tenant, dan memaksa peninjau melihatnya. Anotasi yang bekerja diam-diam justru membuat orang berhenti memikirkannya — dan ketika sesuatu akhirnya gagal aktif pada satu jalur, tidak ada apa pun di kode pemanggil yang memberi petunjuk bahwa perlindungan itu pernah ada.

Row-Level Security PostgreSQL adalah pertahanan terkuat yang tersedia dan tetap ditolak untuk v1: ia menuntut `SET LOCAL` yang bersanding rapi dengan connection pool, dan mengubah setiap bug menjadi query yang mengembalikan kosong tanpa menjelaskan sebabnya.

## Konsekuensi

- Method repository berumur panjang dan bertele-tele. Diterima.
- Penegakannya bersandar pada TC-41: setiap endpoint bersasaran wajib punya tes yang membuktikan permintaan lintas-Client mendapat `404`. Tanpa tes itu, keputusan ini menjadi lebih lemah daripada filter otomatis, bukan lebih kuat.
- Seseorang akan mengusulkan "merapikan" ini menjadi `@Filter` dalam beberapa bulan. Dokumen ini ada untuk menjawabnya.
- Bila kelak jumlah endpoint tumbuh sampai penegakan lewat tes tidak lagi meyakinkan, RLS adalah jalan naik berikutnya — sebagai lapis tambahan, bukan pengganti.
