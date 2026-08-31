---
status: accepted
---

# Adapter dummy boleh sampai demo, tidak pernah menyentuh data siswa sungguhan

Kebijakan password ditunda ke Keycloak. Sampai ia terpasang, `IdentityProviderPort` dilayani adapter dummy berkredensial statis. Adapter itu boleh hidup di dua environment: `local` dan `demo`. Batas yang mengikat bukan nama environment melainkan **isi datanya** — adapter dummy tidak boleh berada di sistem yang memuat nama, email, jawaban, atau nilai siswa nyata.

## Alasan

Menuliskan kebijakan password sendiri hari ini berarti membangun sesuatu yang akan dibuang saat Keycloak masuk: panjang minimum, pemeriksaan daftar kata bocor, alur ganti password, penyimpanan hash. Keycloak sudah menyediakan semuanya dan memang sudah direncanakan.

Yang membuat penundaan ini bisa diterima adalah adanya kebutuhan nyata untuk memperagakan produk sebelum Keycloak siap — kepada calon Client, kepada pemangku kepentingan sekolah. Peragaan tidak membutuhkan autentikasi sungguhan; ia membutuhkan aplikasi yang berjalan.

Yang tidak bisa diterima adalah membiarkan batasnya berupa label environment. `DummyIdentityProviderAdapter` menerima satu password untuk **semua** akun: siapa pun yang mengetahuinya masuk sebagai Siswa mana pun, Guru mana pun, Client Admin mana pun — membaca seluruh bank soal dan mengubah nilai. Sebuah server bernama "demo" yang diisi data sekolah sungguhan telah menjadi produksi, dan namanya tidak melindungi siapa-siapa.

## Konsekuensi

- Batas ditegakkan di kode, bukan di kesepakatan: adapter menolak start bila `EDUSCREEN_ENV` bukan `local` atau `demo`, termasuk bila variabelnya tidak diset (TC-04).
- **Jalur kebocoran yang paling mungkin adalah niat baik**: seseorang menyalin data Client sungguhan ke demo supaya peragaannya terasa meyakinkan. Karena itu TC-48 melarang demo dipulihkan dari cadangan produksi dan melarang impor data Client nyata.
- Email transaksional dimatikan di demo (TC-49). Mengirim tautan reset password dari sistem yang autentikasinya palsu mengubah peragaan menjadi insiden.
- Spanduk permanen di setiap halaman demo (TC-47) supaya tidak ada yang keliru menganggapnya sistem sungguhan.
- Client pertama yang membawa data siswa nyata adalah **pemicu keras**: sebelum itu, Keycloak harus terpasang, atau adapter lokal berbasis BCrypt harus dibangun beserta kebijakan passwordnya. Kebijakan itu wajib ada sebelum akun sungguhan pertama dibuat — mengubahnya setelah ribuan akun terbentuk berarti memaksa seluruh sekolah mengganti password serentak.
- Keycloak tidak lagi memblokir rilis, tetapi tetap berada di jalur menuju Client berbayar pertama. Penundaan ini memindahkan tenggatnya, bukan menghapusnya.
