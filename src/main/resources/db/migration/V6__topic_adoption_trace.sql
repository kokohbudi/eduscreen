-- Jejak asal untuk Topic hasil adopsi.
--
--   ADR-0001  adopsi adalah salinan penuh; jejak asal ditelusuri, tidak pernah disinkronkan
--   FR-076    katalog menandai konten master yang sudah pernah diadopsi Client yang melihatnya
--   FR-077    adopsi berulang tetap diizinkan, tetapi didahului peringatan
--
-- Question sudah punya source_question_id sejak V2 dan indeksnya sejak V5; Topic tertinggal,
-- sehingga "Topic ini sudah pernah Anda ambil" tidak bisa dijawab tanpa menebak dari nama.
-- Nama bukan identitas: master yang di-rename atau salinan yang dirapikan Guru membuat tebakan
-- itu meleset ke dua arah sekaligus.

alter table topic add column source_topic_id uuid;

-- Jejak, bukan kunci: adopsi berulang MEMANG melahirkan Topic baru (FR-077), jadi indeksnya
-- sengaja tidak unik. Yang dijaga hanya agar pertanyaan "sudah pernah?" murah dijawab.
create index topic_adopted_source on topic (client_id, source_topic_id)
    where source_topic_id is not null and deleted_at is null;

-- Jejak asal hanya bermakna bagi Topic milik Client. Topic GLOBAL milik Eduscreen tidak pernah
-- lahir dari adopsi apa pun (FR-082, aliran konten satu arah).
alter table topic add constraint topic_source_client_only
    check (source_topic_id is null or origin = 'CLIENT');
