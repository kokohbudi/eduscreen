-- Taksonomi, bank soal, dan Exercise.
--
--   ADR-0004  taksonomi dua lapis; jenjang melekat di nama Subject, bukan entitas terpisah
--   ADR-0001  konten master yang diadopsi Client menjadi SALINAN, ditandai source_question_id
--   TC-22/25  kolom *_html berisi HTML tersanitasi; *_text turunan teks polos untuk pencarian
--   TC-35     penghapusan bersifat soft delete

-- ---------------------------------------------------------------------------
-- subject — dua asal: GLOBAL milik Eduscreen, CLIENT milik satu sekolah
-- ---------------------------------------------------------------------------
create table subject (
    id          uuid        primary key,
    -- Memuat jenjang: 'Matematika Kelas 4'. Tanpa itu, bank soal berisi Kelas 1 sampai 12
    -- menjadi rimba (ADR-0004).
    name        text        not null,
    origin      text        not null,
    client_id   uuid        references client (id),
    created_at  timestamptz not null default now(),
    deleted_at  timestamptz,

    constraint subject_origin_valid check (origin in ('GLOBAL', 'CLIENT')),
    constraint subject_origin_matches_owner check (
        (origin = 'GLOBAL' and client_id is null) or (origin = 'CLIENT' and client_id is not null)
    ),
    constraint subject_name_not_blank check (length(btrim(name)) > 0)
);

create index subject_by_client on subject (client_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- topic — boleh milik Client meski Subject induknya GLOBAL (FR-014)
-- ---------------------------------------------------------------------------
create table topic (
    id          uuid        primary key,
    subject_id  uuid        not null references subject (id),
    name        text        not null,
    origin      text        not null,
    client_id   uuid        references client (id),
    created_at  timestamptz not null default now(),
    deleted_at  timestamptz,

    constraint topic_origin_valid check (origin in ('GLOBAL', 'CLIENT')),
    constraint topic_origin_matches_owner check (
        (origin = 'GLOBAL' and client_id is null) or (origin = 'CLIENT' and client_id is not null)
    ),
    constraint topic_name_not_blank check (length(btrim(name)) > 0)
);

create index topic_by_subject on topic (subject_id) where deleted_at is null;
create index topic_by_client on topic (client_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- question — client_id null berarti milik Eduscreen (konten master)
-- ---------------------------------------------------------------------------
create table question (
    id                 uuid        primary key,
    client_id          uuid        references client (id),
    topic_id           uuid        not null references topic (id),
    type               text        not null,
    body_html          text        not null,
    body_text          text        not null,
    explanation_html   text,
    explanation_text   text,
    -- Jejak adopsi saja; tidak dipakai untuk sinkronisasi apa pun (ADR-0001).
    source_question_id uuid,
    created_by         uuid        references app_user (id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    deleted_at         timestamptz,

    constraint question_type_valid check (type in ('MULTIPLE_CHOICE', 'ESSAY')),
    constraint question_body_not_blank check (length(btrim(body_text)) > 0)
);

create index question_by_client_topic on question (client_id, topic_id) where deleted_at is null;
-- Pencarian menyentuh kolom teks polos, bukan HTML: mencari 'img' di kolom HTML akan
-- memunculkan setiap soal bergambar (TC-25).
create index question_body_text_search on question using gin (to_tsvector('simple', body_text));

-- ---------------------------------------------------------------------------
-- question_option — tepat satu benar per soal pilihan ganda (FR-016)
-- ---------------------------------------------------------------------------
create table question_option (
    id          uuid        primary key,
    question_id uuid        not null references question (id),
    body_html   text        not null,
    body_text   text        not null,
    is_correct  boolean     not null default false,
    position    int         not null,

    constraint question_option_position_positive check (position >= 0)
);

create index question_option_by_question on question_option (question_id, position);
-- Constraint, bukan niat baik kode: dua opsi benar dalam satu soal membuat penilaian
-- kehilangan makna (TC-19).
create unique index question_option_single_correct
    on question_option (question_id) where is_correct;

-- ---------------------------------------------------------------------------
-- exercise — netral terhadap mode; terkunci begitu Assignment pertamanya lahir (FR-026)
-- ---------------------------------------------------------------------------
create table exercise (
    id          uuid        primary key,
    client_id   uuid        references client (id),
    title       text        not null,
    created_by  uuid        references app_user (id),
    -- Terisi saat penerbitan pertama. Setelah itu Exercise read-only; perubahan dilakukan
    -- dengan menduplikasinya.
    locked_at   timestamptz,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz,

    constraint exercise_title_not_blank check (length(btrim(title)) > 0)
);

create index exercise_by_client on exercise (client_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- exercise_item — urutan yang disusun Guru; boleh lintas Subject dan Topic (FR-024)
-- ---------------------------------------------------------------------------
create table exercise_item (
    id          uuid        primary key,
    exercise_id uuid        not null references exercise (id),
    question_id uuid        not null references question (id),
    position    int         not null,

    constraint exercise_item_position_positive check (position >= 0)
);

create unique index exercise_item_unique on exercise_item (exercise_id, question_id);
create index exercise_item_ordered on exercise_item (exercise_id, position);
