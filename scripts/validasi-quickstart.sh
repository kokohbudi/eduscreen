#!/bin/bash
#
# Validasi quickstart.md V1..V7 terhadap aplikasi yang sedang berjalan.
#
# Cara memakai, dari akar repo:
#
#   docker exec eduscreen-db psql -U eduscreen -d eduscreen -c 'drop schema public cascade; create schema public;'
#   EDUSCREEN_ENV=local ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
#       -Dspring-boot.run.jvmArguments="-Dserver.port=8099" &
#   ./scripts/validasi-quickstart.sh
#
# Skrip ini MENGANDAIKAN database berisi data awal profil `local` yang masih segar (V900 dan
# V901) dan tidak ada Assignment lain. Ia menulis ke database, jadi jangan pernah menjalankannya
# terhadap environment yang memuat data sungguhan.
# Bukan pengganti tes otomatis; ini pemeriksaan ujung-ke-ujung lewat HTTP seperti yang dilakukan
# manusia, untuk menangkap hal yang hanya muncul saat rangkaian penuh dijalankan.
set -u
B=http://localhost:8099
PSQL="docker exec eduscreen-db psql -U eduscreen -d eduscreen -t -A -c"
OK=0; GAGAL=0

cek() { # cek "nama" "harapan" "nyata"
  if [ "$2" = "$3" ]; then echo "  OK   $1"; OK=$((OK+1));
  else echo "  GAGAL $1 (harap=$2 nyata=$3)"; GAGAL=$((GAGAL+1)); fi
}

masuk() { # masuk email berkas
  rm -f "$2"; curl -s -c "$2" $B/login > /dev/null
  local t; t=$(grep XSRF-TOKEN "$2" | awk '{print $7}')
  curl -s -b "$2" -c "$2" -o /dev/null -d "username=$1&password=password123&_csrf=$t" $B/login
}
tok() { grep XSRF-TOKEN "$1" | awk '{print $7}'; }
kode() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "== V1 — Ruangan dan penggunanya =="
masuk admin@contoh.sch.id ca.txt; CT=$(tok ca.txt)
cek "portal Client Admin terbuka" 200 "$(kode -b ca.txt $B/admin)"
cek "buat Ruangan baru" 200 "$(kode -b ca.txt -H "X-XSRF-TOKEN: $CT" -d 'name=Bimbel Intensif SBMPTN Group B' $B/admin/ruangan)"
R2=$($PSQL "select id from ruangan where name like 'Bimbel Intensif SBMPTN%' limit 1")
S1=$($PSQL "select id from app_user where email='siswa1@contoh.sch.id'")
cek "tambah Siswa ke Ruangan kedua" 200 \
  "$(kode -b ca.txt -H "X-XSRF-TOKEN: $CT" -X POST -d "userIds=$S1&memberRole=SISWA" $B/admin/ruangan/$R2/anggota)"
cek "buat akun Guru baru (undangan terkirim)" 200 \
  "$(kode -b ca.txt -H "X-XSRF-TOKEN: $CT" -d 'email=guru2@contoh.sch.id&fullName=Pak Budi&role=GURU' $B/admin/pengguna)"
cek "undangan tercatat" 1 "$($PSQL "select count(*) from user_invitation where purpose='INVITATION'")"
cek "Ruangan Client lain -> 404" 404 "$(kode -b ca.txt $B/admin/ruangan/00000000-0000-7000-8000-000000000000)"

echo "== V2 — Terbit sampai keluar nilai =="
masuk guru@contoh.sch.id g.txt; GT=$(tok g.txt)
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d 'title=Ulangan Harian Aljabar' $B/exercise
EX=$($PSQL "select id from exercise order by created_at desc limit 1")
for Q in $($PSQL "select id from question where type='MULTIPLE_CHOICE' order by id"); do
  curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "questionId=$Q" $B/exercise/$EX/item
done
cek "Exercise berisi 10 soal" 10 "$($PSQL "select count(*) from exercise_item where exercise_id='$EX'")"
R1=$($PSQL "select id from ruangan where name like 'Kelas 4B%' limit 1")
EXP=$(TZ=Asia/Jakarta date -v+3H '+%Y-%m-%dT%H:%M' 2>/dev/null || TZ=Asia/Jakarta date -d '+3 hours' '+%Y-%m-%dT%H:%M')
cek "terbitkan QUIZ 60 menit, pengacakan menyala" 302 \
  "$(kode -b g.txt -H "X-XSRF-TOKEN: $GT" -d "exerciseId=$EX" -d "ruanganIds=$R1" \
     --data-urlencode 'title=Ulangan Aljabar' -d 'mode=QUIZ' -d 'timerDurationMinutes=60' \
     -d "expiresAt=$EXP" -d 'maxAttempts=3' -d 'shuffleQuestions=true' -d 'shuffleOptions=true' \
     -d 'revealAnswersAt=AFTER_SUBMIT' $B/guru/assignment)"
A=$($PSQL "select id from assignment order by created_at desc limit 1")
cek "Exercise terkunci saat terbit (AC-M05)" t "$($PSQL "select locked_at is not null from exercise where id='$EX'")"

masuk siswa1@contoh.sch.id s1.txt; T1=$(tok s1.txt)
masuk siswa2@contoh.sch.id s2.txt; T2=$(tok s2.txt)
curl -s -b s1.txt -H "X-XSRF-TOKEN: $T1" -o /dev/null -X POST $B/siswa/assignment/$A/mulai
curl -s -b s2.txt -H "X-XSRF-TOKEN: $T2" -o /dev/null -X POST $B/siswa/assignment/$A/mulai
SES1=$($PSQL "select id from exam_session where student_id='$S1' order by started_at desc limit 1")
S2ID=$($PSQL "select id from app_user where email='siswa2@contoh.sch.id'")
SES2=$($PSQL "select id from exam_session where student_id='$S2ID' order by started_at desc limit 1")
U1=$($PSQL "select string_agg(question_id::text,',' order by position) from session_question where session_id='$SES1'")
U2=$($PSQL "select string_agg(question_id::text,',' order by position) from session_question where session_id='$SES2'")
if [ "$U1" != "$U2" ]; then echo "  OK   urutan dua Siswa berbeda (AC-S02)"; OK=$((OK+1));
else echo "  GAGAL urutan dua Siswa identik"; GAGAL=$((GAGAL+1)); fi

# Siswa 1 menjawab 6 soal benar, lalu "menutup tab" dan membuka kembali
$PSQL "select sq.id||'|'||(select o.id from question_option o where o.question_id=sq.question_id and o.is_correct) from session_question sq where sq.session_id='$SES1' order by sq.position limit 6" > p.txt
while IFS='|' read -r SQ OPT; do
  curl -s -b s1.txt -H "X-XSRF-TOKEN: $T1" -o /dev/null -X PUT -d "selectedOptionId=$OPT" $B/siswa/sesi/$SES1/jawaban/$SQ
done < p.txt
cek "enam jawaban tersimpan" 6 "$($PSQL "select count(*) from session_answer sa join session_question sq on sq.id=sa.session_question_id where sq.session_id='$SES1'")"
curl -s -b s1.txt -H "X-XSRF-TOKEN: $T1" -o /dev/null -X POST $B/siswa/assignment/$A/mulai
cek "membuka kembali mengembalikan sesi yang sama (AC-S03)" 1 \
  "$($PSQL "select count(*) from exam_session where student_id='$S1' and assignment_id='$A'")"
U1B=$($PSQL "select string_agg(question_id::text,',' order by position) from session_question where session_id='$SES1'")
cek "urutan soal tetap setelah dibuka kembali" "$U1" "$U1B"
cek "tekan Selesai" 302 "$(kode -b s1.txt -H "X-XSRF-TOKEN: $T1" -X POST $B/siswa/sesi/$SES1/selesai)"
cek "skor pilihan ganda keluar tanpa campur tangan" 0.6000 "$($PSQL "select score from result where session_id='$SES1'")"
cek "rekap Guru terbuka" 200 "$(kode -b g.txt $B/guru/assignment/$A/rekap)"
cek "rekap memuat seluruh anggota Ruangan" 5 \
  "$(curl -s -b g.txt $B/guru/assignment/$A/rekap | grep -c 'rekap/siswa/')"

echo "== V3 — Practice =="
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d 'title=Latihan Aljabar' $B/exercise
EXP3=$($PSQL "select id from exercise order by created_at desc limit 1")
for Q in $($PSQL "select id from question where type='MULTIPLE_CHOICE' order by id limit 3"); do
  curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "questionId=$Q" $B/exercise/$EXP3/item
done
cek "terbitkan PRACTICE" 302 \
  "$(kode -b g.txt -H "X-XSRF-TOKEN: $GT" -d "exerciseId=$EXP3" -d "ruanganIds=$R1" \
     --data-urlencode 'title=Latihan Aljabar' -d 'mode=PRACTICE' -d "expiresAt=$EXP" \
     -d 'maxAttempts=1' -d 'shuffleQuestions=false' -d 'shuffleOptions=false' $B/guru/assignment)"
AP=$($PSQL "select id from assignment where mode='PRACTICE' order by created_at desc limit 1")
masuk siswa3@contoh.sch.id s3.txt; T3=$(tok s3.txt)
curl -s -b s3.txt -H "X-XSRF-TOKEN: $T3" -o /dev/null -X POST $B/siswa/assignment/$AP/mulai
S3ID=$($PSQL "select id from app_user where email='siswa3@contoh.sch.id'")
SESP=$($PSQL "select id from exam_session where student_id='$S3ID' order by started_at desc limit 1")
read -r SQP OPTP <<< "$($PSQL "select sq.id||' '||(select o.id from question_option o where o.question_id=sq.question_id and o.is_correct) from session_question sq where sq.session_id='$SESP' order by sq.position limit 1")"
cek "jawaban Practice tersimpan" 200 "$(kode -b s3.txt -H "X-XSRF-TOKEN: $T3" -X PUT -d "selectedOptionId=$OPTP" $B/siswa/sesi/$SESP/jawaban/$SQP)"
cek "soal terkunci setelah dijawab (AC-S04)" t "$($PSQL "select locked_at is not null from session_question where id='$SQP'")"
cek "kiriman ulang identik tetap sukses (TC-20)" 200 "$(kode -b s3.txt -H "X-XSRF-TOKEN: $T3" -X PUT -d "selectedOptionId=$OPTP" $B/siswa/sesi/$SESP/jawaban/$SQP)"
OPTW=$($PSQL "select id from question_option where question_id=(select question_id from session_question where id='$SQP') and not is_correct limit 1")
cek "jawaban berbeda pada soal terkunci ditolak 409" 409 "$(kode -b s3.txt -H "X-XSRF-TOKEN: $T3" -X PUT -d "selectedOptionId=$OPTW" $B/siswa/sesi/$SESP/jawaban/$SQP)"
cek "hanya satu baris jawaban" 1 "$($PSQL "select count(*) from session_answer where session_question_id='$SQP'")"

curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d 'title=Latihan Beressay' $B/exercise
EXE=$($PSQL "select id from exercise order by created_at desc limit 1")
for Q in $($PSQL "select id from question where type='ESSAY' order by id limit 1"); do
  curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "questionId=$Q" $B/exercise/$EXE/item
done
cek "PRACTICE beressay ditolak 422 (AC-M01)" 422 \
  "$(kode -b g.txt -H "X-XSRF-TOKEN: $GT" -d "exerciseId=$EXE" -d "ruanganIds=$R1" \
     --data-urlencode 'title=Latihan Essay' -d 'mode=PRACTICE' -d "expiresAt=$EXP" \
     -d 'maxAttempts=1' -d 'shuffleQuestions=false' -d 'shuffleOptions=false' $B/guru/assignment)"

echo "== V4 — Essay =="
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d 'title=Ulangan Campuran' $B/exercise
EXM=$($PSQL "select id from exercise order by created_at desc limit 1")
for Q in $($PSQL "select id from question where type='MULTIPLE_CHOICE' order by id limit 9"); do
  curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "questionId=$Q" $B/exercise/$EXM/item
done
QE=$($PSQL "select id from question where type='ESSAY' order by id limit 1")
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "questionId=$QE" $B/exercise/$EXM/item
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -d "exerciseId=$EXM" -d "ruanganIds=$R1" \
  --data-urlencode 'title=Ulangan Campuran' -d 'mode=QUIZ' -d 'timerDurationMinutes=60' \
  -d "expiresAt=$EXP" -d 'maxAttempts=1' -d 'shuffleQuestions=false' -d 'shuffleOptions=false' \
  -d 'revealAnswersAt=AFTER_SUBMIT' $B/guru/assignment
AM=$($PSQL "select id from assignment order by created_at desc limit 1")
masuk siswa4@contoh.sch.id s4.txt; T4=$(tok s4.txt)
curl -s -b s4.txt -H "X-XSRF-TOKEN: $T4" -o /dev/null -X POST $B/siswa/assignment/$AM/mulai
S4ID=$($PSQL "select id from app_user where email='siswa4@contoh.sch.id'")
SESM=$($PSQL "select id from exam_session where student_id='$S4ID' and assignment_id='$AM'")
$PSQL "select sq.id||'|'||coalesce((select o.id::text from question_option o where o.question_id=sq.question_id and o.is_correct),'ESSAY') from session_question sq where sq.session_id='$SESM' order by sq.position" > m.txt
while IFS='|' read -r SQ OPT; do
  if [ "$OPT" = "ESSAY" ]; then
    curl -s -b s4.txt -H "X-XSRF-TOKEN: $T4" -o /dev/null -X PUT --data-urlencode 'essayText=Kurangi 3 lalu bagi 2.' $B/siswa/sesi/$SESM/jawaban/$SQ
  else
    curl -s -b s4.txt -H "X-XSRF-TOKEN: $T4" -o /dev/null -X PUT -d "selectedOptionId=$OPT" $B/siswa/sesi/$SESM/jawaban/$SQ
  fi
done < m.txt
curl -s -b s4.txt -H "X-XSRF-TOKEN: $T4" -o /dev/null -X POST $B/siswa/sesi/$SESM/selesai
cek "hasil menunggu penilaian (AC-C02)" PENDING_REVIEW "$($PSQL "select status from result where session_id='$SESM'")"
cek "skor sementara 0.9 dari 9 MCQ benar" 0.9000 "$($PSQL "select score from result where session_id='$SESM'")"
SA=$($PSQL "select sa.id from session_answer sa join session_question sq on sq.id=sa.session_question_id where sq.session_id='$SESM' and sa.essay_text is not null")
cek "beri nilai essay 75" 200 "$(kode -b g.txt -H "X-XSRF-TOKEN: $GT" -X PUT -d 'essayScore=75' $B/guru/jawaban/$SA/nilai)"
cek "Result menjadi FINAL" FINAL "$($PSQL "select status from result where session_id='$SESM'")"
cek "skor (9 + 0,75) / 10" 0.9750 "$($PSQL "select score from result where session_id='$SESM'")"
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -X PUT -d 'essayScore=90' $B/guru/jawaban/$SA/nilai
cek "skor setelah diubah ke 90" 0.9900 "$($PSQL "select score from result where session_id='$SESM'")"
cek "dua perubahan tercatat di audit (AC-G02)" 2 "$($PSQL "select count(*) from score_audit")"

echo "== V5 — Onboarding =="
masuk admin@eduscreen.id ea.txt; ET=$(tok ea.txt)
cek "portal Eduscreen Admin" 200 "$(kode -b ea.txt $B/eduscreen)"
cek "daftarkan Client baru" 302 "$(kode -b ea.txt -H "X-XSRF-TOKEN: $ET" \
  --data-urlencode 'name=SMP Harapan' -d 'timezone=Asia/Makassar' \
  -d 'adminEmail=admin@harapan.sch.id' --data-urlencode 'adminFullName=Admin Harapan' $B/eduscreen/client)"
cek "Client baru berzona Makassar" Asia/Makassar "$($PSQL "select timezone from client where name='SMP Harapan'")"
cek "Client Admin pertama terbuat" 1 "$($PSQL "select count(*) from app_user where email='admin@harapan.sch.id'")"
cek "onboarding tidak membuat Ruangan (BR-O01)" 0 \
  "$($PSQL "select count(*) from ruangan where client_id=(select id from client where name='SMP Harapan')")"

echo "== V6 — Impor =="
python3 - <<'PY'
import io
head = "topic,tipe,soal,opsi_a,opsi_b,opsi_c,opsi_d,kunci,pembahasan\n"
baris = []
for i in range(1, 21):
    kunci = "" if i > 17 else "B"
    baris.append(f'Aljabar Dasar,PG,"Soal impor {i}",A{i},B{i},C{i},D{i},{kunci},"Pembahasan {i}"')
open("impor20.csv","w").write(head + "\n".join(baris) + "\n")
open("impor2000.csv","w").write(head + "\n".join(
    [f'Aljabar Dasar,PG,"Soal besar {i}",A,B,C,D,B,"Pembahasan"' for i in range(1, 2001)]) + "\n")
PY
CT=$(tok ca.txt)
cek "berkas 2.000 baris ditolak (AC-Q06)" 422 \
  "$(kode -b ca.txt -H "X-XSRF-TOKEN: $CT" -F berkas=@impor2000.csv $B/admin/impor/pratinjau)"
PRA=$(curl -s -b ca.txt -H "X-XSRF-TOKEN: $CT" -F berkas=@impor20.csv $B/admin/impor/pratinjau)
VALID=$(echo "$PRA" | grep -oE '>[0-9]+</span>[[:space:]]*baris valid' | grep -oE '[0-9]+' | head -1)
cek "pratinjau 17 baris valid (AC-Q03)" 17 "${VALID:-kosong}"
TOKENP=$(echo "$PRA" | grep -oE 'name="token" value="[^"]+"' | sed 's/.*value="//;s/"//')
SEBELUM=$($PSQL "select count(*) from question")
curl -s -b ca.txt -H "X-XSRF-TOKEN: $CT" -o /dev/null -d "token=$TOKENP" $B/admin/impor/simpan
SESUDAH=$($PSQL "select count(*) from question")
cek "17 soal tersimpan" 17 "$((SESUDAH - SEBELUM))"

echo "== V7 — Pengulangan =="
masuk siswa5@contoh.sch.id s5.txt; T5=$(tok s5.txt)
S5ID=$($PSQL "select id from app_user where email='siswa5@contoh.sch.id'")
for i in 1 2 3; do
  curl -s -b s5.txt -H "X-XSRF-TOKEN: $T5" -o /dev/null -X POST $B/siswa/assignment/$A/mulai
  SS=$($PSQL "select id from exam_session where student_id='$S5ID' and assignment_id='$A' order by attempt_number desc limit 1")
  N=$((i * 2))
  $PSQL "select sq.id||'|'||(select o.id from question_option o where o.question_id=sq.question_id and o.is_correct) from session_question sq where sq.session_id='$SS' order by sq.position limit $N" > a.txt
  while IFS='|' read -r SQ OPT; do
    curl -s -b s5.txt -H "X-XSRF-TOKEN: $T5" -o /dev/null -X PUT -d "selectedOptionId=$OPT" $B/siswa/sesi/$SS/jawaban/$SQ
  done < a.txt
  curl -s -b s5.txt -H "X-XSRF-TOKEN: $T5" -o /dev/null -X POST $B/siswa/sesi/$SS/selesai
done
cek "tiga pengerjaan terbentuk" 3 "$($PSQL "select count(*) from exam_session where student_id='$S5ID' and assignment_id='$A'")"
cek "pengerjaan keempat ditolak 409 (AC-S05)" 409 "$(kode -b s5.txt -H "X-XSRF-TOKEN: $T5" -X POST $B/siswa/assignment/$A/mulai)"
cek "skor tertinggi 0.6 (AC-L02)" 0.6000 \
  "$($PSQL "select max(r.score) from result r join exam_session s on s.id=r.session_id where s.student_id='$S5ID' and s.assignment_id='$A'")"

echo
echo "== Kasus tepi =="
cek "Siswa tak pernah mulai: tanpa baris sesi" 0 \
  "$($PSQL "select count(*) from exam_session s join app_user u on u.id=s.student_id where u.email='siswa2@contoh.sch.id' and s.assignment_id='$AM'")"
cek "sesi milik Siswa lain -> 404" 404 "$(kode -b s5.txt $B/siswa/sesi/$SESM)"
cek "sesi tidak ada -> 404 yang sama" 404 "$(kode -b s5.txt $B/siswa/sesi/01920000-0000-7000-8000-999999999999)"
QDEL=$($PSQL "select question_id from session_question where session_id='$SESP' limit 1")
curl -s -b g.txt -H "X-XSRF-TOKEN: $GT" -o /dev/null -X DELETE $B/soal/$QDEL
cek "soal dihapus saat sesi berjalan: sesi tetap utuh" 200 "$(kode -b s3.txt $B/siswa/sesi/$SESP)"
cek "soal hilang dari bank soal" 404 "$(kode -b g.txt $B/soal/$QDEL)"

echo
echo "RINGKASAN: $OK lulus, $GAGAL gagal"
[ "$GAGAL" -eq 0 ]
