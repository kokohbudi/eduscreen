-- Jejak yang tidak boleh bisa dihapus.
--
--   TC-37    setiap perubahan essay_score dan setiap perhitungan ulang Result dicatat
--   TC-46    setiap pembacaan Eduscreen Admin selama akses dukungan dicatat
--
-- Kedua tabel di bawah hanya-sisip. Tidak ada jalur di aplikasi yang meng-update atau
-- menghapus barisnya: nilai yang berubah adalah bahan sengketa di sekolah, dan sistem
-- harus bisa menjawab siapa yang mengubahnya.

-- ---------------------------------------------------------------------------
-- score_audit — riwayat nilai (BR-G03)
--
-- session_answer_id null berarti barisnya mencatat perhitungan ulang Result secara
-- keseluruhan, bukan perubahan satu jawaban essay.
-- ---------------------------------------------------------------------------
create table score_audit (
    id                uuid         primary key,
    result_id         uuid         not null references result (id),
    client_id         uuid         not null references client (id),
    session_answer_id uuid         references session_answer (id),
    changed_by        uuid         not null references app_user (id),
    changed_at        timestamptz  not null default now(),
    old_value         numeric(6,4),
    new_value         numeric(6,4)
);

create index score_audit_by_result on score_audit (result_id, changed_at);
create index score_audit_by_client on score_audit (client_id, changed_at);

-- ---------------------------------------------------------------------------
-- support_access_read — pembacaan selama jendela dukungan (BR-P05, ADR-0015)
--
-- Bisa ditunjukkan kepada Client. Tanpa catatan yang bisa diperlihatkan, "baca-saja
-- berbatas waktu" hanyalah janji.
-- ---------------------------------------------------------------------------
create table support_access_read (
    id          uuid        primary key,
    grant_id    uuid        not null references support_access_grant (id),
    client_id   uuid        not null references client (id),
    read_by     uuid        not null references app_user (id),
    resource    text        not null,
    read_at     timestamptz not null default now()
);

create index support_access_read_by_client on support_access_read (client_id, read_at);
