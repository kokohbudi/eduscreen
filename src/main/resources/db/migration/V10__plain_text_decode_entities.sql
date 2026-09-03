-- Kolom teks polos (*_text) sempat menyimpan entitas HTML yang tidak didekode ContentSanitizer:
-- daftar replace() buatan sendiri di toPlainText melewatkan empat karakter yang dikodekan
-- sanitizer OWASP demi keamanan atribut — &#43; (+), &#61; (=), &#64; (@), &#96; (`). Soal
-- "2 + 2" tersimpan sebagai "2 &#43; 2", tampil begitu di panel pinjam, dan tidak ketemu saat
-- dicari "2 + 2" (TC-25). Kodenya sudah memakai Encoding.decodeHtml; ini membetulkan baris yang
-- terlanjur ditulis. Kolom *_html tidak disentuh: di sana entitas memang bentuk yang benar.

update question
set body_text = replace(replace(replace(replace(body_text,
        '&#43;', '+'), '&#61;', '='), '&#64;', '@'), '&#96;', '`')
where body_text like '%&#43;%' or body_text like '%&#61;%'
   or body_text like '%&#64;%' or body_text like '%&#96;%';

update question
set explanation_text = replace(replace(replace(replace(explanation_text,
        '&#43;', '+'), '&#61;', '='), '&#64;', '@'), '&#96;', '`')
where explanation_text like '%&#43;%' or explanation_text like '%&#61;%'
   or explanation_text like '%&#64;%' or explanation_text like '%&#96;%';

update question_option
set body_text = replace(replace(replace(replace(body_text,
        '&#43;', '+'), '&#61;', '='), '&#64;', '@'), '&#96;', '`')
where body_text like '%&#43;%' or body_text like '%&#61;%'
   or body_text like '%&#64;%' or body_text like '%&#96;%';
