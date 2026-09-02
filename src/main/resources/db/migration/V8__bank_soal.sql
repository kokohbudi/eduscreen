-- Topic turun dari taksonomi global menjadi milik Paket.
--
--   ADR-0018  Topic milik Paket; Paket adalah satuan jual dan satuan adopsi
--   ADR-0001  adopsi dan pinjam adalah salinan penuh, ditandai source_*_id
--   TC-35     penghapusan bersifat soft delete
--
-- Id Paket sengaja memakai ulang id Topic lama yang menurunkannya. Itu membuat pemetaan
-- topic->paket tidak perlu tabel bantu, dan jejaknya tetap terbaca saat menelusuri data lama.

-- ---------------------------------------------------------------------------
-- paket
-- ---------------------------------------------------------------------------
create table paket (
    id              uuid        primary key,
    client_id       uuid        references client (id),
    title           text        not null,
    subject_id      uuid        not null references subject (id),
    created_by      uuid        references app_user (id),
    published_at    timestamptz,
    source_paket_id uuid,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz,

    constraint paket_title_not_blank check (length(btrim(title)) > 0),
    -- Keadaan terbit hanya bermakna bagi konten master, sama seperti question dan exercise (FR-066).
    constraint paket_publish_master_only check (published_at is null or client_id is null),
    -- Jejak adopsi hanya ada pada salinan milik Client; Paket master tidak lahir dari adopsi.
    constraint paket_source_client_only check (source_paket_id is null or client_id is not null)
);

create index paket_by_client_subject on paket (client_id, subject_id) where deleted_at is null;
create index paket_master_published on paket (subject_id)
    where client_id is null and published_at is not null and deleted_at is null;
create index paket_adopted_source on paket (client_id, source_paket_id)
    where source_paket_id is not null and deleted_at is null;

-- ---------------------------------------------------------------------------
-- 1. tiap Topic lama menjadi satu Paket, id-nya dipakai ulang
-- ---------------------------------------------------------------------------
-- Topic yang sudah ter-soft-delete ikut dipindahkan, Paketnya lahir ter-soft-delete juga.
-- Membuangnya secara fisik tidak bisa: question.topic_id menunjuknya lewat foreign key, dan
-- question itu sendiri masih dirujuk exercise_item dan session_question. Membawanya serta juga
-- yang benar menurut TC-35 — penghapusan di sistem ini tidak pernah menghapus baris.
insert into paket (id, client_id, title, subject_id, created_at, deleted_at)
select t.id, t.client_id, t.name, t.subject_id, t.created_at, t.deleted_at
from topic t;

-- ---------------------------------------------------------------------------
-- 2. topic: kehilangan taksonomi, mendapat induk Paket
-- ---------------------------------------------------------------------------
alter table topic rename column name to title;
alter table topic add column paket_id uuid;
alter table topic add column position int not null default 0;

update topic set paket_id = id;

alter table topic alter column paket_id set not null;
alter table topic add constraint topic_paket_fk foreign key (paket_id) references paket (id);
alter table topic add constraint topic_position_positive check (position >= 0);
alter table topic add constraint topic_title_not_blank check (length(btrim(title)) > 0);

drop index topic_by_subject;
drop index topic_by_client;
drop index topic_adopted_source;
alter table topic drop constraint topic_origin_valid;
alter table topic drop constraint topic_origin_matches_owner;
alter table topic drop constraint topic_name_not_blank;
alter table topic drop constraint topic_source_client_only;
alter table topic drop column source_topic_id;
alter table topic drop column origin;
alter table topic drop column client_id;
alter table topic drop column subject_id;

create index topic_by_paket on topic (paket_id, position) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- 3. question: induk Paket dan urutan di dalam Topic
-- ---------------------------------------------------------------------------
alter table question add column paket_id uuid;
alter table question add column position int not null default 0;

-- Setiap question wajib punya topic (foreign key sejak V2), dan setiap topic kini punya paket,
-- jadi tidak ada baris yang tertinggal tanpa induk.
update question q set paket_id = t.paket_id from topic t where q.topic_id = t.id;

alter table question alter column paket_id set not null;
alter table question add constraint question_paket_fk foreign key (paket_id) references paket (id);
alter table question add constraint question_position_positive check (position >= 0);

update question q
set position = s.rn - 1
from (
    select id, row_number() over (partition by topic_id order by created_at, id) as rn
    from question
    where deleted_at is null
) s
where q.id = s.id;

create index question_by_paket on question (paket_id, topic_id, position) where deleted_at is null;

-- Katalog sekarang menyaring per Paket, bukan per Topic.
drop index question_master_published;
create index question_master_published on question (paket_id)
    where client_id is null and published_at is not null and deleted_at is null;
