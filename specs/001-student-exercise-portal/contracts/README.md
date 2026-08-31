# Interface Contracts

**Date**: 2026-08-31 | **Plan**: [../plan.md](../plan.md)

## Bentuk kontrak di proyek ini

Eduscreen adalah aplikasi web yang dirender server (ADR-0010). Tidak ada REST API bermuatan JSON
untuk dikonsumsi klien lain, sehingga kontraknya adalah **kontrak endpoint HTTP** dengan respons
berupa halaman atau fragmen HTML.

Setiap berkas di direktori ini mendokumentasikan satu kelompok endpoint: metode, jalur, peran
yang berhak, masukan, keluaran, dan kode status kegagalan.

## Aturan yang berlaku untuk seluruh endpoint

| Aturan | Isi |
| --- | --- |
| **Autentikasi** | Sesi server berbasis cookie `HttpOnly; Secure; SameSite=Lax` (TC-29) |
| **HTMX tak terautentikasi** | `401` + header `HX-Redirect: /login` — **bukan** `302` (TC-30) |
| **Galat** | Satu `@ControllerAdvice` merender fragmen kecil dengan status HTTP yang benar (TC-31) |
| **Isolasi** | `client_id` dan kepemilikan masuk klausa query, bukan diperiksa setelah muat (TC-08) |
| **Tidak ditemukan vs bukan milik Anda** | Keduanya `404` identik — pesan, kode, dan waktu tanggap sama (TC-09) |
| **CSRF** | Aktif; token dikirim HTMX lewat `hx-headers` |
| **Fragmen** | Endpoint yang dipanggil HTMX mengembalikan fragmen, bukan JSON (TC-14) |

## Notasi

- **Peran** menyebut siapa yang boleh; selain itu `404` (bukan `403`) untuk objek bersasaran,
  atau `403` untuk aksi yang tidak menyasar objek tertentu.
- **`{id}`** selalu UUID v7.
- **Respons** menyebut `page` (dokumen penuh) atau `fragment` (potongan untuk ditukar HTMX).

## Daftar kontrak

| Berkas | Cakupan | Cerita |
| --- | --- | --- |
| [auth-and-accounts.md](./auth-and-accounts.md) | Login, undangan, reset, akun, Ruangan | US1 |
| [content-authoring.md](./content-authoring.md) | Subject, Topic, Question, Exercise, impor, gambar | US2, US5, US6 |
| [assignment-publishing.md](./assignment-publishing.md) | Penerbitan dan siklus hidup Assignment | US2, US3, US7 |
| [exam-session.md](./exam-session.md) | Portal Siswa dan pengerjaan | US2, US3, US7 |
| [grading-and-reports.md](./grading-and-reports.md) | Penilaian essay dan rekap Ruangan | US2, US4 |
