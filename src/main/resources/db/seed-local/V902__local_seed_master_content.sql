-- Konten master Eduscreen untuk profil `local` saja.
--
-- Ada supaya langkah 2 sampai 4 quickstart.md bisa dijalankan tanpa mengetik konten master lebih
-- dulu: langkah 2 menuntut ada yang BELUM terbit agar bisa dibuktikan tidak bocor ke katalog,
-- dan langkah 4 menuntut ada yang SUDAH terbit agar bisa ditelusuri dan diadopsi.
--
-- Sama seperti V900 dan V901, berkas ini hanya terdaftar di lokasi Flyway profil `local` dan
-- tidak pernah ikut ke environment lain (TC-48).
--
-- Konten master hidup di baris ber-client_id null; keadaan terbit ada di published_at (V5).

insert into subject (id, name, origin, client_id) values
    ('01920000-0000-7000-8000-000000009000', 'Matematika Kelas 5', 'GLOBAL', null),
    ('01920000-0000-7000-8000-000000009001', 'IPA Kelas 5', 'GLOBAL', null);

insert into topic (id, subject_id, name, origin, client_id) values
    ('01920000-0000-7000-8000-000000009100', '01920000-0000-7000-8000-000000009000', 'Pecahan', 'GLOBAL', null),
    ('01920000-0000-7000-8000-000000009101', '01920000-0000-7000-8000-000000009001', 'Gaya dan Gerak', 'GLOBAL', null);

-- Empat soal terbit (terlihat di katalog Client) dan dua yang masih digarap (tidak terlihat).
insert into question (id, client_id, topic_id, type, body_html, body_text, explanation_html, explanation_text, published_at) values
    ('01920000-0000-7000-8000-000000009200', null, '01920000-0000-7000-8000-000000009100', 'MULTIPLE_CHOICE', '<p>Berapa hasil dari 1/2 + 1/4?</p>', 'Berapa hasil dari 1/2 + 1/4?', '<p>Samakan penyebut menjadi 4: 2/4 + 1/4 = 3/4.</p>', 'Samakan penyebut menjadi 4: 2/4 + 1/4 = 3/4.', now()),
    ('01920000-0000-7000-8000-000000009201', null, '01920000-0000-7000-8000-000000009100', 'MULTIPLE_CHOICE', '<p>Pecahan senilai dengan 2/3 adalah</p>', 'Pecahan senilai dengan 2/3 adalah', '<p>Kalikan pembilang dan penyebut dengan bilangan yang sama.</p>', 'Kalikan pembilang dan penyebut dengan bilangan yang sama.', now()),
    ('01920000-0000-7000-8000-000000009202', null, '01920000-0000-7000-8000-000000009100', 'MULTIPLE_CHOICE', '<p>Bentuk paling sederhana dari 6/8 adalah</p>', 'Bentuk paling sederhana dari 6/8 adalah', '<p>Bagi keduanya dengan 2.</p>', 'Bagi keduanya dengan 2.', now()),
    ('01920000-0000-7000-8000-000000009203', null, '01920000-0000-7000-8000-000000009101', 'MULTIPLE_CHOICE', '<p>Gaya yang membuat benda jatuh ke bawah disebut</p>', 'Gaya yang membuat benda jatuh ke bawah disebut', '<p>Gaya gravitasi menarik benda ke pusat bumi.</p>', 'Gaya gravitasi menarik benda ke pusat bumi.', now()),
    -- Masih digarap: sengaja ada supaya bisa dibuktikan TIDAK muncul di katalog Client (FR-067).
    ('01920000-0000-7000-8000-000000009204', null, '01920000-0000-7000-8000-000000009100', 'MULTIPLE_CHOICE', '<p>[DRAF] Berapa hasil dari 3/5 - 1/5?</p>', '[DRAF] Berapa hasil dari 3/5 - 1/5?', null, null, null),
    ('01920000-0000-7000-8000-000000009205', null, '01920000-0000-7000-8000-000000009101', 'ESSAY', '<p>[DRAF] Jelaskan perbedaan gaya gesek dan gaya gravitasi.</p>', '[DRAF] Jelaskan perbedaan gaya gesek dan gaya gravitasi.', null, null, null);

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

-- Satu paket terbit (bisa dipilih saat onboarding) dan satu yang masih digarap.
insert into exercise (id, client_id, title, published_at) values
    ('01920000-0000-7000-8000-000000009400', null, 'Persiapan UTS Matematika Kelas 5', now()),
    ('01920000-0000-7000-8000-000000009401', null, '[DRAF] Paket IPA Kelas 5', null);

insert into exercise_item (id, exercise_id, question_id, position) values
    ('01920000-0000-7000-8000-000000009500', '01920000-0000-7000-8000-000000009400', '01920000-0000-7000-8000-000000009200', 0),
    ('01920000-0000-7000-8000-000000009501', '01920000-0000-7000-8000-000000009400', '01920000-0000-7000-8000-000000009201', 1),
    ('01920000-0000-7000-8000-000000009502', '01920000-0000-7000-8000-000000009400', '01920000-0000-7000-8000-000000009202', 2),
    -- Paket draf sengaja memuat soal yang juga masih digarap: menerbitkannya harus menawarkan
    -- pilihan ikut/tidak ikut menerbitkan drafnya (AC-B12, ADR-0020), bisa dicoba di layar.
    ('01920000-0000-7000-8000-000000009503', '01920000-0000-7000-8000-000000009401', '01920000-0000-7000-8000-000000009203', 0),
    ('01920000-0000-7000-8000-000000009504', '01920000-0000-7000-8000-000000009401', '01920000-0000-7000-8000-000000009205', 1);
