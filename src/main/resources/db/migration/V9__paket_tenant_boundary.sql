-- Batas tenant Paket, ditegakkan database.
--
--   ADR-0018  Topic milik Paket; kepemilikan konten dibaca dari Paket, bukan dari Topic
--   TC-36     pemisahan tenant adalah aturan, bukan niat baik kode pemanggil
--
-- Sebelum ADR-0018, sebuah Client boleh menggantungkan Question di bawah Topic GLOBAL
-- (aturan lama FR-014). Setelah V8 Topic itu menjadi milik sebuah Paket, sehingga pola lama
-- berubah arti: Question milik satu sekolah duduk di dalam Paket milik Eduscreen. Selama
-- Paket belum pernah disalin utuh hal itu tidak terlihat, tetapi adopsi per Paket menyalin
-- Paket beserta seluruh Question di dalamnya — dan saat itu soal satu sekolah ikut tersalin
-- ke sekolah lain.

-- ---------------------------------------------------------------------------
-- 1. pindahkan Question yang pemiliknya berbeda dari pemilik Paketnya
-- ---------------------------------------------------------------------------
-- Satu Paket baru per pasangan (Client, Paket asal), berisi salinan Topic yang benar-benar
-- dipakai Question itu. Id-nya v4 dari gen_random_uuid(), bukan UuidV7 seperti baris yang
-- ditulis aplikasi; keduanya opaque dan tidak ada urutan yang bergantung padanya.
create temporary table paket_pindah as
select distinct q.client_id, p.id as paket_asal_id, gen_random_uuid() as paket_baru_id
from question q
join paket p on p.id = q.paket_id
where q.client_id is not null and p.client_id is distinct from q.client_id;

insert into paket (id, client_id, title, subject_id, created_at)
select pp.paket_baru_id, pp.client_id, p.title, p.subject_id, p.created_at
from paket_pindah pp
join paket p on p.id = pp.paket_asal_id;

create temporary table topic_pindah as
select distinct t.id as topic_asal_id, pp.paket_baru_id,
       gen_random_uuid() as topic_baru_id, t.title, t.position
from question q
join paket_pindah pp on pp.paket_asal_id = q.paket_id and pp.client_id = q.client_id
join topic t on t.id = q.topic_id;

insert into topic (id, paket_id, title, position, created_at)
select tp.topic_baru_id, tp.paket_baru_id, tp.title, tp.position, t.created_at
from topic_pindah tp
join topic t on t.id = tp.topic_asal_id;

update question q
set paket_id = tp.paket_baru_id, topic_id = tp.topic_baru_id
from paket_pindah pp
join topic_pindah tp on tp.paket_baru_id = pp.paket_baru_id
where pp.client_id = q.client_id
  and pp.paket_asal_id = q.paket_id
  and tp.topic_asal_id = q.topic_id;

drop table topic_pindah;
drop table paket_pindah;

-- ---------------------------------------------------------------------------
-- 2. kunci aturannya di skema
-- ---------------------------------------------------------------------------
alter table paket add constraint paket_id_client_unique unique (id, client_id);

-- Batas yang sebenarnya dijaga, apa adanya: karena client_id boleh null dan foreign key
-- multi-kolom bawaan PostgreSQL memakai MATCH SIMPLE, pasangan yang salah satu kolomnya null
-- TIDAK diperiksa sama sekali. Artinya Question master (client_id null) lolos tanpa diperiksa.
-- Yang tertutup adalah arah berbahayanya: Question milik sebuah Client hanya cocok dengan
-- Paket yang client_id-nya sama persis, sehingga ia tidak bisa masuk ke Paket master maupun
-- ke Paket milik Client lain.
alter table question add constraint question_paket_same_owner
    foreign key (paket_id, client_id) references paket (id, client_id);
