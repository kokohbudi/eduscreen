-- Akses sekolah ke Paket master lewat referensi, bukan salinan (ADR-0021, Fase 3).
--
--   FR-067  hanya Paket master terbit yang bisa diberikan ke sekolah
--   FR-068  menarik Paket dari peredaran tidak menyentuh yang sudah dibaca sekolah
--   TC-36   pemisahan tenant: satu baris per (sekolah, Paket), diberi Eduscreen Admin
--
-- Satu baris = "Paket P milik sekolah S, membaca versi V, sampai tanggal T". Sekolah tidak
-- pernah mendapat baris paket/topic/question dari sini; Guru merakit Exercise langsung dari
-- soal master lewat versi yang ditunjuk. Menggantikan adopsi salinan (ADR-0001), yang datanya
-- dibiarkan apa adanya: salinan lama sudah milik sekolah masing-masing.

-- Prasyarat FK komposit: versi yang ditunjuk wajib milik Paket yang sama.
alter table paket_version add constraint paket_version_id_paket_unique unique (id, paket_id);

create table paket_access (
    id          uuid        primary key,
    client_id   uuid        not null references client (id),
    paket_id    uuid        not null references paket (id),
    version_id  uuid        not null references paket_version (id),
    granted_by  uuid        references app_user (id),
    granted_at  timestamptz not null default now(),
    -- Null berarti tanpa batas.
    valid_until timestamptz,
    revoked_at  timestamptz,

    constraint paket_access_version_of_paket foreign key (version_id, paket_id)
        references paket_version (id, paket_id)
);

-- Paling banyak satu akses aktif per (sekolah, Paket); yang dicabut tinggal sebagai jejak.
create unique index paket_access_active_unique on paket_access (client_id, paket_id) where revoked_at is null;
create index paket_access_by_client on paket_access (client_id) where revoked_at is null;
create index paket_access_by_paket on paket_access (paket_id) where revoked_at is null;
