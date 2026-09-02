#!/usr/bin/env python3
"""Uji beban jalur pengerjaan terhadap aplikasi yang sedang berjalan.

Menyasar tiga endpoint yang benar-benar deras saat ujian berlangsung:

  POST /siswa/assignment/{id}/mulai        lonjakan serentak di awal ujian
  PUT  /siswa/sesi/{id}/jawaban/{sqId}     auto-save, tabel dengan tulis paling deras
  GET  /siswa/sesi/{id}/waktu              dipanggil berkala oleh tiap sesi berjalan

Hanya memakai pustaka standar: menambah dependensi uji beban ke proyek ini akan menjadi
perkakas yang harus dirawat, sementara yang dibutuhkan hanya beberapa ratus permintaan
bersamaan dan tiga persentil.

Cara memakai (aplikasi harus sudah berjalan dan database berisi data uji beban):

    ./scripts/uji-beban.py --base http://localhost:8099 --siswa 200 --jawaban 10

Skrip ini MENULIS ke database. Jangan pernah menjalankannya terhadap environment yang memuat
data sungguhan.
"""

import argparse
import http.cookiejar
import json
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

PSQL = ["docker", "exec", "eduscreen-db", "psql", "-U", "eduscreen", "-d", "eduscreen", "-t", "-A", "-c"]


def sql(query):
    hasil = subprocess.run(PSQL + [query], capture_output=True, text=True, check=True)
    return [baris for baris in hasil.stdout.strip().split("\n") if baris]


class Klien:
    """Satu sesi peramban: cookie sendiri, token CSRF sendiri."""

    def __init__(self, base):
        self.base = base
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar),
            NoRedirect(),
        )

    def csrf(self):
        for cookie in self.jar:
            if cookie.name == "XSRF-TOKEN":
                return cookie.value
        return ""

    def minta(self, metode, jalur, data=None):
        isi = urllib.parse.urlencode(data).encode() if data else None
        permintaan = urllib.request.Request(self.base + jalur, data=isi, method=metode)
        if isi:
            permintaan.add_header("Content-Type", "application/x-www-form-urlencoded")
        token = self.csrf()
        if token:
            permintaan.add_header("X-XSRF-TOKEN", token)
        mulai = time.perf_counter()
        try:
            with self.opener.open(permintaan, timeout=30) as respons:
                respons.read()
                status = respons.status
        except urllib.error.HTTPError as galat:
            galat.read()
            status = galat.code
        return status, (time.perf_counter() - mulai) * 1000

    def masuk(self, email, password="password123"):
        self.minta("GET", "/login")
        status, _ = self.minta("POST", "/login", {"username": email, "password": password})
        return status


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Pengalihan dihitung sebagai hasil, bukan diikuti — yang diukur latensi endpointnya."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Catatan:
    def __init__(self):
        self.kunci = threading.Lock()
        self.data = {}

    def rekam(self, nama, status, milidetik):
        with self.kunci:
            entri = self.data.setdefault(nama, {"latensi": [], "status": {}})
            entri["latensi"].append(milidetik)
            entri["status"][status] = entri["status"].get(status, 0) + 1

    def laporan(self):
        baris = []
        for nama, entri in sorted(self.data.items()):
            latensi = sorted(entri["latensi"])
            baris.append({
                "endpoint": nama,
                "permintaan": len(latensi),
                "p50": round(persentil(latensi, 50), 1),
                "p95": round(persentil(latensi, 95), 1),
                "p99": round(persentil(latensi, 99), 1),
                "maks": round(latensi[-1], 1),
                "status": entri["status"],
            })
        return baris


def persentil(terurut, p):
    if not terurut:
        return 0.0
    indeks = min(len(terurut) - 1, int(round((p / 100) * (len(terurut) - 1))))
    return terurut[indeks]


def siapkan(jumlah_siswa):
    """Membuat Siswa, Ruangan, Exercise, dan Assignment untuk uji beban."""
    client = sql("select id from client order by created_at limit 1")[0]
    guru = sql("select id from app_user where role='GURU' and client_id='%s' limit 1" % client)[0]
    soal = sql("select id from question where client_id='%s' and type='MULTIPLE_CHOICE' "
               "and deleted_at is null order by id limit 10" % client)
    if len(soal) < 10:
        sys.exit("Bank soal uji beban kurang dari 10 soal; muat ulang data awal profil local.")

    sql("""
        insert into ruangan (id, client_id, name)
        values ('01920000-0000-7000-8000-0000000009ff', '%s', 'Ruangan Uji Beban')
        on conflict do nothing
    """ % client)
    ruangan = "01920000-0000-7000-8000-0000000009ff"

    sql("""
        insert into app_user (id, client_id, email, full_name, role, status)
        select ('01920000-0000-7000-8000-0000000a' || lpad(i::text, 4, '0'))::uuid,
               '%s', 'beban' || i || '@uji.sch.id', 'Siswa Beban ' || i, 'SISWA', 'ACTIVE'
        from generate_series(1, %d) i
        on conflict do nothing
    """ % (client, jumlah_siswa))
    sql("""
        insert into ruangan_member (id, client_id, ruangan_id, user_id, member_role)
        select ('01920000-0000-7000-8000-0000000b' || lpad(i::text, 4, '0'))::uuid,
               '%s', '%s', ('01920000-0000-7000-8000-0000000a' || lpad(i::text, 4, '0'))::uuid, 'SISWA'
        from generate_series(1, %d) i
        on conflict do nothing
    """ % (client, ruangan, jumlah_siswa))

    exercise = "01920000-0000-7000-8000-0000000009fe"
    sql("""
        insert into exercise (id, client_id, title, created_by)
        values ('%s', '%s', 'Exercise Uji Beban', '%s') on conflict do nothing
    """ % (exercise, client, guru))
    for posisi, q in enumerate(soal):
        sql("""
            insert into exercise_item (id, exercise_id, question_id, position)
            values (gen_random_uuid(), '%s', '%s', %d) on conflict do nothing
        """ % (exercise, q, posisi))

    assignment = "01920000-0000-7000-8000-0000000009fd"
    sql("""
        insert into assignment (id, client_id, exercise_id, ruangan_id, published_by, mode, status,
                                title, timer_duration_minutes, expires_at, max_attempts,
                                shuffle_questions, shuffle_options, reveal_answers_at, published_at)
        values ('%s', '%s', '%s', '%s', '%s', 'QUIZ', 'PUBLISHED', 'Ulangan Uji Beban',
                180, now() + interval '3 hours', 1, true, true, 'AFTER_SUBMIT', now())
        on conflict do nothing
    """ % (assignment, client, exercise, ruangan, guru))
    return assignment, jumlah_siswa


def satu_siswa(base, nomor, assignment, jumlah_jawaban, catatan):
    klien = Klien(base)
    status = klien.masuk("beban%d@uji.sch.id" % nomor)
    catatan.rekam("POST /login", status, 0.0)

    status, ms = klien.minta("POST", "/siswa/assignment/%s/mulai" % assignment)
    catatan.rekam("POST /siswa/assignment/{id}/mulai", status, ms)
    if status not in (200, 302):
        return

    baris = sql("""
        select sq.id || ' ' || (select o.id from question_option o
                                where o.question_id = sq.question_id and o.is_correct)
        from session_question sq
        join exam_session s on s.id = sq.session_id
        where s.assignment_id = '%s'
          and s.student_id = ('01920000-0000-7000-8000-0000000a' || lpad(%d::text, 4, '0'))::uuid
        order by sq.position limit %d
    """ % (assignment, nomor, jumlah_jawaban))
    if not baris:
        return
    sesi = sql("""
        select id from exam_session where assignment_id = '%s'
          and student_id = ('01920000-0000-7000-8000-0000000a' || lpad(%d::text, 4, '0'))::uuid
    """ % (assignment, nomor))[0]

    for entri in baris:
        sq, opsi = entri.split(" ")
        status, ms = klien.minta(
            "PUT", "/siswa/sesi/%s/jawaban/%s" % (sesi, sq), {"selectedOptionId": opsi})
        catatan.rekam("PUT /siswa/sesi/{id}/jawaban/{sqId}", status, ms)

        status, ms = klien.minta("GET", "/siswa/sesi/%s/waktu" % sesi)
        catatan.rekam("GET /siswa/sesi/{id}/waktu", status, ms)

    status, ms = klien.minta("POST", "/siswa/sesi/%s/selesai" % sesi)
    catatan.rekam("POST /siswa/sesi/{id}/selesai", status, ms)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8099")
    parser.add_argument("--siswa", type=int, default=200)
    parser.add_argument("--jawaban", type=int, default=10)
    parser.add_argument("--paralel", type=int, default=50)
    argumen = parser.parse_args()

    assignment, jumlah = siapkan(argumen.siswa)
    catatan = Catatan()

    mulai = time.perf_counter()
    with ThreadPoolExecutor(max_workers=argumen.paralel) as kolam:
        for nomor in range(1, jumlah + 1):
            kolam.submit(satu_siswa, argumen.base, nomor, assignment, argumen.jawaban, catatan)
    durasi = time.perf_counter() - mulai

    laporan = {
        "siswa": jumlah,
        "paralel": argumen.paralel,
        "jawaban_per_siswa": argumen.jawaban,
        "durasi_detik": round(durasi, 1),
        "endpoint": catatan.laporan(),
    }
    print(json.dumps(laporan, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
