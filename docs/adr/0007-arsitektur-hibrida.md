---
status: accepted
---

# Hexagonal di batas eksternal, Layered di inti bisnis

Eduscreen memakai dua gaya arsitektur sekaligus dan itu disengaja. Modul yang bergantung pada sistem yang **tidak kami kendalikan** — Identity & Access Management, Notification Engine, File Storage — dibangun hexagonal: interface di `port.out`, implementasi di `adapter.out`. Modul inti bisnis — bank soal, exam engine, persistensi — dibangun berlapis lurus: `controller` → `service` → `repository` dengan Spring Data JPA.

## Alasan

Hexagonal membayar biaya nyata: satu interface, satu implementasi, satu lapis pemetaan objek, untuk setiap hal yang diisolasi. Biaya itu masuk akal ketika yang di seberang batas bisa berubah tanpa izin kami — Keycloak menggantikan adapter dummy, penyedia email berganti, penyimpanan berkas pindah. Biaya itu tidak masuk akal untuk `AssignmentSessionService` yang berbicara ke tabel PostgreSQL milik sendiri; di sana port hanya menambah lapisan yang harus dibaca orang tanpa memberi kelenturan yang akan pernah dipakai.

Memilih satu gaya untuk seluruh aplikasi berarti salah di salah satu ujung: hexagonal di mana-mana membanjiri inti dengan interface beranggota tunggal, sementara layered di mana-mana menanam nama vendor ke dalam logika bisnis.

## Konsekuensi

- Pembagiannya berdasarkan kepemilikan batas, bukan ukuran modul. Pertanyaan yang menentukan: "apakah yang di seberang sini bisa berubah tanpa izin kami?"
- Inti `assessment` bekerja langsung dengan entitas JPA. Ini disengaja; menambahkan lapisan domain terpisah di sana adalah pekerjaan yang tidak dibayar siapa pun.
- Batas antar modul ditegakkan lewat aturan impor: `assessment` hanya boleh menyentuh `identity.port.in`, tidak pernah `identity.adapter`. Tanpa penegakan otomatis (uji arsitektur atau pemeriksa dependensi), aturan ini akan luntur dalam beberapa bulan.
- Pendatang baru akan melihat dua gaya dan menyangka salah satunya adalah warisan yang belum sempat dirapikan. Dokumen ini dan `CONSTITUTION.md` Pasal 1 ada untuk mencegah "perapian" itu terjadi.
