-- Konten master Eduscreen untuk profil `local` saja.
--
-- Ada supaya langkah 2 sampai 4 quickstart.md bisa dijalankan tanpa mengetik konten master lebih
-- dulu: langkah 2 menuntut ada yang BELUM terbit agar bisa dibuktikan tidak bocor ke katalog,
-- dan langkah 4 menuntut ada yang SUDAH terbit agar bisa ditelusuri dan diadopsi.
--
-- Sama seperti V900 dan V901, berkas ini hanya terdaftar di lokasi Flyway profil `local` dan
-- tidak pernah ikut ke environment lain (TC-48).
--
-- Konten master hidup di baris ber-client_id null; keadaan terbit Paket ada di paket.published_at
-- (V5, V8) dan keadaan terbit soal di question.published_at. Bentuk data mengikuti V11:
-- Subject GLOBAL › Paket master › versi kerja › Topic; soal ditempatkan lewat paket_item.

insert into subject (id, name, origin, client_id) values
    ('01920000-0000-7000-8000-000000009000', 'Matematika Kelas 5', 'GLOBAL', null),
    ('01920000-0000-7000-8000-000000009001', 'IPA Kelas 5', 'GLOBAL', null);

-- Satu Paket terbit (bisa diberikan ke sekolah) dan satu yang masih digarap.
insert into paket (id, client_id, title, subject_id, published_at) values
    ('01920000-0000-7000-8000-000000009400', null, 'Persiapan UTS Matematika Kelas 5',
     '01920000-0000-7000-8000-000000009000', now()),
    ('01920000-0000-7000-8000-000000009401', null, '[DRAF] Paket IPA Kelas 5',
     '01920000-0000-7000-8000-000000009001', null);

insert into paket_version (id, paket_id, client_id, nomor) values
    ('01920000-0000-7000-8000-000000009410', '01920000-0000-7000-8000-000000009400', null, 1),
    ('01920000-0000-7000-8000-000000009411', '01920000-0000-7000-8000-000000009401', null, 1);

insert into topic (id, paket_id, title, position) values
    ('01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009400', 'Pecahan', 0),
    ('01920000-0000-7000-8000-000000009101', '01920000-0000-7000-8000-000000009401', 'Gaya dan Gerak', 0);

-- Empat soal terbit dan dua yang masih digarap (tidak ikut keluar dari ruang kerja master).
insert into question (id, client_id, type, body_html, body_text, explanation_html, explanation_text, published_at) values
    ('01920000-0000-7000-8000-000000009200', null, 'MULTIPLE_CHOICE', '<p>Berapa hasil dari 1/2 + 1/4?</p>', 'Berapa hasil dari 1/2 + 1/4?', '<p>Samakan penyebut menjadi 4: 2/4 + 1/4 = 3/4.</p>', 'Samakan penyebut menjadi 4: 2/4 + 1/4 = 3/4.', now()),
    ('01920000-0000-7000-8000-000000009201', null, 'MULTIPLE_CHOICE', '<p>Pecahan senilai dengan 2/3 adalah</p>', 'Pecahan senilai dengan 2/3 adalah', '<p>Kalikan pembilang dan penyebut dengan bilangan yang sama.</p>', 'Kalikan pembilang dan penyebut dengan bilangan yang sama.', now()),
    ('01920000-0000-7000-8000-000000009202', null, 'MULTIPLE_CHOICE', '<p>Bentuk paling sederhana dari 6/8 adalah</p>', 'Bentuk paling sederhana dari 6/8 adalah', '<p>Bagi keduanya dengan 2.</p>', 'Bagi keduanya dengan 2.', now()),
    ('01920000-0000-7000-8000-000000009203', null, 'MULTIPLE_CHOICE', '<p>Gaya yang membuat benda jatuh ke bawah disebut</p>', 'Gaya yang membuat benda jatuh ke bawah disebut', '<p>Gaya gravitasi menarik benda ke pusat bumi.</p>', 'Gaya gravitasi menarik benda ke pusat bumi.', now()),
    -- Masih digarap: sengaja ada supaya bisa dibuktikan TIDAK ikut terbit (FR-067, ADR-0020).
    ('01920000-0000-7000-8000-000000009204', null, 'MULTIPLE_CHOICE', '<p>[DRAF] Berapa hasil dari 3/5 - 1/5?</p>', '[DRAF] Berapa hasil dari 3/5 - 1/5?', null, null, null),
    ('01920000-0000-7000-8000-000000009205', null, 'ESSAY', '<p>[DRAF] Jelaskan perbedaan gaya gesek dan gaya gravitasi.</p>', '[DRAF] Jelaskan perbedaan gaya gesek dan gaya gravitasi.', null, null, null);

-- Penempatan (ADR-0021). Paket terbit memuat satu soal draf di dalamnya: menerbitkan ulang harus
-- menawarkan pilihan ikut/tidak ikut menerbitkan drafnya (AC-B12, ADR-0020), bisa dicoba di layar.
insert into paket_item (id, paket_version_id, client_id, topic_id, question_id, position) values
    ('01920000-0000-7000-8000-000000009500', '01920000-0000-7000-8000-000000009410', null, '01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009200', 0),
    ('01920000-0000-7000-8000-000000009501', '01920000-0000-7000-8000-000000009410', null, '01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009201', 1),
    ('01920000-0000-7000-8000-000000009502', '01920000-0000-7000-8000-000000009410', null, '01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009202', 2),
    ('01920000-0000-7000-8000-000000009503', '01920000-0000-7000-8000-000000009410', null, '01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009204', 3),
    ('01920000-0000-7000-8000-000000009504', '01920000-0000-7000-8000-000000009411', null, '01920000-0000-7000-8000-000000009101', '01920000-0000-7000-8000-000000009203', 0),
    ('01920000-0000-7000-8000-000000009505', '01920000-0000-7000-8000-000000009411', null, '01920000-0000-7000-8000-000000009101', '01920000-0000-7000-8000-000000009205', 1);

insert into question_option (id, question_id, body_html, body_text, is_correct, position) values
    ('01920000-0000-7000-8000-000000009300', '01920000-0000-7000-8000-000000009200', '<p>3/4</p>', '3/4', true, 0),
    ('01920000-0000-7000-8000-000000009301', '01920000-0000-7000-8000-000000009200', '<p>2/6</p>', '2/6', false, 1),
    ('01920000-0000-7000-8000-000000009302', '01920000-0000-7000-8000-000000009200', '<p>1/6</p>', '1/6', false, 2),
    ('01920000-0000-7000-8000-000000009303', '01920000-0000-7000-8000-000000009200', '<p>2/4</p>', '2/4', false, 3),
    ('01920000-0000-7000-8000-000000009310', '01920000-0000-7000-8000-000000009201', '<p>4/6</p>', '4/6', true, 0),
    ('01920000-0000-7000-8000-000000009311', '01920000-0000-7000-8000-000000009201', '<p>3/4</p>', '3/4', false, 1),
    ('01920000-0000-7000-8000-000000009312', '01920000-0000-7000-8000-000000009201', '<p>2/6</p>', '2/6', false, 2),
    ('01920000-0000-7000-8000-000000009313', '01920000-0000-7000-8000-000000009201', '<p>5/6</p>', '5/6', false, 3),
    ('01920000-0000-7000-8000-000000009320', '01920000-0000-7000-8000-000000009202', '<p>3/4</p>', '3/4', true, 0),
    ('01920000-0000-7000-8000-000000009321', '01920000-0000-7000-8000-000000009202', '<p>2/4</p>', '2/4', false, 1),
    ('01920000-0000-7000-8000-000000009322', '01920000-0000-7000-8000-000000009202', '<p>6/4</p>', '6/4', false, 2),
    ('01920000-0000-7000-8000-000000009323', '01920000-0000-7000-8000-000000009202', '<p>1/2</p>', '1/2', false, 3),
    ('01920000-0000-7000-8000-000000009330', '01920000-0000-7000-8000-000000009203', '<p>Gaya gravitasi</p>', 'Gaya gravitasi', true, 0),
    ('01920000-0000-7000-8000-000000009331', '01920000-0000-7000-8000-000000009203', '<p>Gaya gesek</p>', 'Gaya gesek', false, 1),
    ('01920000-0000-7000-8000-000000009332', '01920000-0000-7000-8000-000000009203', '<p>Gaya magnet</p>', 'Gaya magnet', false, 2),
    ('01920000-0000-7000-8000-000000009333', '01920000-0000-7000-8000-000000009203', '<p>Gaya pegas</p>', 'Gaya pegas', false, 3),
    ('01920000-0000-7000-8000-000000009340', '01920000-0000-7000-8000-000000009204', '<p>2/5</p>', '2/5', true, 0),
    ('01920000-0000-7000-8000-000000009341', '01920000-0000-7000-8000-000000009204', '<p>4/5</p>', '4/5', false, 1);
