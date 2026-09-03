-- Penempatan soal pindah dari kolom question ke tabel keanggotaan per versi Paket.
--
--   ADR-0021  Paket berversi; soal adalah isi murni yang boleh dibagi banyak versi dan Paket
--   ADR-0018  Topic tetap milik satu Paket, sekarang murni label; isinya ditentukan paket_item
--   TC-35     penghapusan bersifat soft delete; baris tidak pernah hilang
--   TC-36     batas tenant ditegakkan database lewat FK komposit (pola V9)
--
-- Sebelum ini question memikul paket_id/topic_id/position sendiri, sehingga satu soal hanya
-- bisa hidup di satu Paket dan tiap versi atau salinan Paket berarti baris question baru.
-- Sekarang: paket_version memegang "versi mana", paket_item memegang "soal apa, di Topic mana,
-- urutan berapa". question tinggal isi.
--
-- Migrasi ini HANYA memindahkan bentuk data; perilakunya belum berubah. Setiap Paket mendapat
-- tepat satu versi kerja (published_at null), termasuk Paket master yang sudah terbit di katalog:
-- keadaan terbit Paket tetap dibaca dari paket.published_at seperti sebelumnya. Membekukan versi
-- terbit dan melahirkan versi baru adalah langkah berikutnya (Fase 2), bukan di sini.

-- ---------------------------------------------------------------------------
-- 1. paket_version
-- ---------------------------------------------------------------------------
create table paket_version (
    id            uuid        primary key,
    paket_id      uuid        not null references paket (id),
    -- Denormalisasi pemilik Paket, syarat FK komposit batas tenant di paket_item (pola V9).
    client_id     uuid        references client (id),
    nomor         int         not null,
    -- Null berarti versi kerja yang masih boleh diubah; terisi berarti beku.
    published_at  timestamptz,
    superseded_at timestamptz,
    created_by    uuid        references app_user (id),
    created_at    timestamptz not null default now(),

    constraint paket_version_nomor_positive check (nomor >= 1),
    constraint paket_version_nomor_unique unique (paket_id, nomor),
    -- client_id versi wajib sama persis dengan client_id Paketnya (paket_id_client_unique, V9).
    constraint paket_version_same_owner foreign key (paket_id, client_id) references paket (id, client_id),
    -- Prasyarat FK komposit dari paket_item.
    constraint paket_version_id_client_unique unique (id, client_id)
);

-- Paling banyak satu versi kerja per Paket.
create unique index paket_version_single_draft on paket_version (paket_id) where published_at is null;
create index paket_version_by_paket on paket_version (paket_id, nomor desc);

-- ---------------------------------------------------------------------------
-- 2. paket_item
-- ---------------------------------------------------------------------------
-- Prasyarat FK komposit (question_id, client_id): pemilik soal ikut tercatat di item.
alter table question add constraint question_id_client_unique unique (id, client_id);

create table paket_item (
    id               uuid  primary key,
    paket_version_id uuid  not null references paket_version (id),
    -- Pemilik SOAL yang ditempatkan, bukan pemilik versi. Dua FK komposit di bawah yang
    -- menyamakan keduanya.
    client_id        uuid  references client (id),
    topic_id         uuid  not null references topic (id),
    question_id      uuid  not null references question (id),
    position         int   not null,

    constraint paket_item_position_positive check (position >= 0),
    constraint paket_item_unique unique (paket_version_id, question_id),
    -- Batas tenant, apa adanya (MATCH SIMPLE, lihat V9): soal milik sebuah Client hanya bisa
    -- masuk versi Paket yang client_id-nya sama persis — tidak bisa ke versi master maupun ke
    -- versi milik Client lain. Soal master (client_id null) lolos tanpa diperiksa di sini; arah
    -- itu dijaga service (Fase 3: soal master hanya masuk lewat akses Paket, bukan item).
    constraint paket_item_same_owner
        foreign key (paket_version_id, client_id) references paket_version (id, client_id),
    -- client_id di item wajib cocok dengan pemilik soalnya.
    constraint paket_item_question_owner
        foreign key (question_id, client_id) references question (id, client_id)
);

create index paket_item_ordered on paket_item (paket_version_id, topic_id, position);
create index paket_item_by_question on paket_item (question_id);

-- ---------------------------------------------------------------------------
-- 3. question: penanda revisi (dipakai Fase 2), disiapkan sekarang supaya skema stabil
-- ---------------------------------------------------------------------------
alter table question add column superseded_by_id uuid references question (id);

-- ---------------------------------------------------------------------------
-- 4. pindahkan data
-- ---------------------------------------------------------------------------
-- Id versi kerja memakai ulang id Paket (trik yang sama dengan V8 untuk paket<-topic): pemetaan
-- paket->versi awal tidak butuh tabel bantu, dan jejaknya terbaca saat menelusuri data lama.
-- Paket yang sudah ter-soft-delete ikut mendapat versi: question di bawahnya masih dirujuk
-- exercise_item dan session_question (TC-35).
insert into paket_version (id, paket_id, client_id, nomor, published_at, created_by, created_at)
select p.id, p.id, p.client_id, 1, null, p.created_by, p.created_at
from paket p;

-- Id item v4 dari gen_random_uuid(), bukan UuidV7 seperti baris yang ditulis aplikasi; keduanya
-- opaque (preseden V9). Soal yang sudah dihapus lunak tidak mendapat item: ia memang sudah tidak
-- punya tempat di Paket, dan @SQLRestriction question sudah menyembunyikannya dari setiap daftar.
insert into paket_item (id, paket_version_id, client_id, topic_id, question_id, position)
select gen_random_uuid(), q.paket_id, q.client_id, q.topic_id, q.id, q.position
from question q
where q.deleted_at is null;

-- ---------------------------------------------------------------------------
-- 5. question: lepas kolom penempatan
-- ---------------------------------------------------------------------------
alter table question drop constraint question_paket_same_owner;   -- V9
alter table question drop constraint question_paket_fk;           -- V8
alter table question drop constraint question_position_positive;  -- V8
drop index question_by_paket;                                     -- V8
drop index question_master_published;                             -- V8 (ulang dari V5)
drop index question_by_client_topic;                              -- V2

alter table question drop column paket_id;
alter table question drop column topic_id;
alter table question drop column position;
