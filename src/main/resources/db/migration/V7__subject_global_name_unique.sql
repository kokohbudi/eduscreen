-- Nama Subject GLOBAL wajib tunggal.
--
--   ADR-0004  Subject global ada justru agar tiap sekolah tidak menulis variasi ejaannya sendiri
--   FR-013    Subject GLOBAL dibaca seluruh Client
--
-- Sebelum ini tabel subject tidak punya unique index apa pun, sehingga dua "Matematika Kelas 4"
-- GLOBAL bisa hidup berdampingan dan tak terbedakan di dropdown mana pun. Duplikat di taksonomi
-- bersama merusak alasan keberadaan Subject global itu sendiri.
--
-- Dinormalkan lower(btrim(...)): "Matematika Kelas 4" dan " matematika kelas 4 " adalah nama
-- yang sama bagi manusia yang membaca dropdown, jadi harus sama pula bagi indeksnya.
--
-- Lingkupnya hanya GLOBAL. Dua Client berhak punya Subject lokal senama — taksonomi mereka
-- terisolasi satu sama lain (TC-36).
create unique index subject_global_name_unique
    on subject (lower(btrim(name)))
    where origin = 'GLOBAL' and deleted_at is null;
