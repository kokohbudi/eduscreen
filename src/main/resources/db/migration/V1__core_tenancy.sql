-- Skema ketenanan inti Eduscreen.
--
-- Aturan yang ditegakkan di lapisan ini:
--   TC-08/TC-36  setiap tabel milik Client membawa client_id; penyaringannya ditulis eksplisit
--                di repository, bukan disembunyikan di filter otomatis
--   ADR-0009     primary key UUID v7 dibuat aplikasi, bukan database
--   BR-T01       seluruh waktu disimpan UTC (timestamptz)
--   TC-19        invariant yang tidak boleh dilanggar dijaga constraint, bukan niat baik kode

-- ---------------------------------------------------------------------------
-- client — akar isolasi data
-- ---------------------------------------------------------------------------
create table client (
    id          uuid        primary key,
    name        text        not null,
    -- Indonesia punya tiga zona; "Minggu 23:59" berarti waktu Client (BR-T02).
    timezone    text        not null,
    status      text        not null default 'ACTIVE',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    constraint client_timezone_valid
        check (timezone in ('Asia/Jakarta', 'Asia/Makassar', 'Asia/Jayapura')),
    constraint client_status_valid
        check (status in ('ACTIVE', 'SUSPENDED')),
    constraint client_name_not_blank
        check (length(btrim(name)) > 0)
);

-- ---------------------------------------------------------------------------
-- app_user — satu tabel untuk empat peran
--
-- Kredensial TIDAK disimpan di sini. Autentikasi hidup di balik IdentityProviderPort
-- (TC-06, TC-07, ADR-0008).
-- ---------------------------------------------------------------------------
create table app_user (
    id          uuid        primary key,
    client_id   uuid        references client (id),
    -- Disimpan huruf kecil agar keunikan tidak bisa dilewati dengan mengubah kapitalisasi.
    email       text        not null,
    full_name   text        not null,
    role        text        not null,
    status      text        not null default 'INVITED',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    constraint app_user_role_valid
        check (role in ('EDUSCREEN_ADMIN', 'CLIENT_ADMIN', 'GURU', 'SISWA')),
    constraint app_user_status_valid
        check (status in ('INVITED', 'ACTIVE', 'DEACTIVATED')),
    constraint app_user_email_lowercase
        check (email = lower(email)),
    constraint app_user_email_not_blank
        check (length(btrim(email)) > 0),
    -- Hanya Eduscreen Admin yang berdiri di luar Client mana pun. Peran lain tanpa client_id
    -- adalah baris yatim yang akan lolos dari setiap penyaringan tenant.
    constraint app_user_tenant_boundary
        check (
            (role = 'EDUSCREEN_ADMIN' and client_id is null)
            or (role <> 'EDUSCREEN_ADMIN' and client_id is not null)
        )
);

create unique index app_user_email_unique on app_user (email);
create index app_user_by_client_role on app_user (client_id, role);

-- ---------------------------------------------------------------------------
-- ruangan — kelompok belajar; tahun ajaran ditangani lewat pengarsipan (ADR-0004 bertetangga)
-- ---------------------------------------------------------------------------
create table ruangan (
    id          uuid        primary key,
    client_id   uuid        not null references client (id),
    name        text        not null,
    status      text        not null default 'ACTIVE',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    constraint ruangan_status_valid
        check (status in ('ACTIVE', 'ARCHIVED')),
    constraint ruangan_name_not_blank
        check (length(btrim(name)) > 0)
);

create index ruangan_by_client_status on ruangan (client_id, status);

-- ---------------------------------------------------------------------------
-- ruangan_member — many-to-many di kedua sisi (FR-008)
--
-- Satu Siswa boleh berada di banyak Ruangan (kelas reguler dan grup bimbel sekaligus);
-- satu Ruangan boleh dipegang banyak Guru mata pelajaran.
-- ---------------------------------------------------------------------------
create table ruangan_member (
    id          uuid        primary key,
    client_id   uuid        not null references client (id),
    ruangan_id  uuid        not null references ruangan (id),
    user_id     uuid        not null references app_user (id),
    member_role text        not null,
    created_at  timestamptz not null default now(),

    constraint ruangan_member_role_valid
        check (member_role in ('GURU', 'SISWA'))
);

create unique index ruangan_member_unique on ruangan_member (ruangan_id, user_id);
create index ruangan_member_by_user on ruangan_member (user_id, member_role);
create index ruangan_member_by_ruangan on ruangan_member (ruangan_id, member_role);

-- ---------------------------------------------------------------------------
-- user_invitation — undangan akun dan reset password (BR-U04)
--
-- Yang disimpan adalah hash token, bukan tokennya. Bocornya isi tabel ini tidak boleh
-- cukup untuk mengambil alih akun (TC-06).
-- ---------------------------------------------------------------------------
create table user_invitation (
    id          uuid        primary key,
    client_id   uuid        references client (id),
    user_id     uuid        not null references app_user (id),
    token_hash  text        not null,
    purpose     text        not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null default now(),

    constraint user_invitation_purpose_valid
        check (purpose in ('INVITATION', 'PASSWORD_RESET'))
);

create unique index user_invitation_token_unique on user_invitation (token_hash);
create index user_invitation_by_user on user_invitation (user_id, purpose);

-- ---------------------------------------------------------------------------
-- support_access_grant — satu-satunya pengecualian isolasi tenant (BR-P05, ADR-0015)
--
-- Baca-saja, dinyalakan Client Admin, padam sendiri setelah 4 jam. Jalur resmi yang sempit
-- ada supaya jalur tidak resmi — koneksi langsung ke database produksi — tidak punya alasan
-- untuk dipakai.
-- ---------------------------------------------------------------------------
create table support_access_grant (
    id          uuid        primary key,
    client_id   uuid        not null references client (id),
    granted_by  uuid        not null references app_user (id),
    granted_at  timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,

    constraint support_access_window_valid
        check (expires_at > granted_at)
);

create index support_access_by_client on support_access_grant (client_id, expires_at);
