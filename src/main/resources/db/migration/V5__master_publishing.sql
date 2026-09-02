-- Keadaan terbit untuk konten master Eduscreen.
--
--   FR-066   dua keadaan saja: belum terbit (hanya Eduscreen Admin) dan terbit (katalog Client)
--   FR-067   yang belum terbit tidak pernah muncul di katalog dan tidak bisa diadopsi
--   FR-068   menarik dari peredaran tidak menyentuh satu pun salinan yang sudah diadopsi
--   ADR-0001 adopsi adalah salinan, bukan tautan — penarikan karena itu aman by construction
--
-- Satu kolom waktu, bukan enum status: ia menyimpan "apakah terbit" dan "sejak kapan" sekaligus,
-- dan mengikuti pola locked_at / deleted_at / closed_at yang sudah dipakai di seluruh skema ini.

alter table question add column published_at timestamptz;
alter table exercise add column published_at timestamptz;

-- Keadaan terbit hanya bermakna bagi konten master. Konten milik sebuah sekolah tidak pernah
-- "diterbitkan" ke siapa pun: ia sudah terlihat seluruh Guru di Client itu sejak ditulis (FR-004).
-- Ditegakkan database, bukan layanan: satu jalur tulis yang lupa memeriksa tidak akan terlihat
-- sampai ada yang menghitung (Prinsip VII).
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
