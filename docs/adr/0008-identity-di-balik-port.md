---
status: accepted
---

# Identity diisolasi di balik port, dengan adapter dummy sampai Keycloak masuk

Autentikasi dan manajemen kredensial tidak ditulis langsung ke dalam layer `service`, melainkan diakses lewat satu interface `IdentityProviderPort`. Implementasi awalnya adalah adapter sementara berkredensial statis untuk pengembangan lokal; Keycloak akan masuk kelak sebagai `KeycloakIdentityProviderAdapter` tanpa mengubah satu baris pun di `service`.

## Alasan

Keycloak adalah pekerjaan penyiapan yang besar dan tidak menghasilkan nilai apa pun bagi Client di minggu-minggu pertama pembangunan. Menundanya masuk akal. Yang tidak masuk akal adalah menunda sambil menanam pemanggilan autentikasi ke seluruh layer `service`, karena migrasinya kemudian menjadi penyisiran ke setiap sudut aplikasi. Satu interface hari ini menukar penundaan itu dengan biaya yang hampir nol.

## Konsekuensi

- **Adapter dummy adalah pintu belakang berjalan.** `return "password123".equals(rawPassword)` menerima siapa pun yang mengetahui satu kata. Karena itu ia dikurung dua lapis: `@Profile({"local", "demo"})` dan pemeriksaan gagal-cepat pada `@PostConstruct` yang menolak start di environment lain. Satu lapis tidak cukup — profil Spring yang salah pasang adalah kegagalan diam-diam, dan kegagalan diam-diam pada jalur autentikasi berarti seluruh platform terbuka tanpa ada yang menyadarinya. Batas pemakaiannya diatur ADR-0016.
- Aplikasi wajib gagal start bila `IdentityProviderPort` yang aktif tidak tepat satu. Fallback diam-diam ke dummy adalah mode kegagalan yang paling ingin kami hindari.
- Bila kebutuhan pengujian menuntut akun berbeda-beda sebelum Keycloak siap, adapter berbasis DB lokal dibuat dengan BCrypt — bukan dengan menambah kredensial statis kedua.
- Bentuk `IdentityProviderPort` dirancang dari kebutuhan aplikasi, bukan dari API Keycloak. Bila kelak terbukti ada ketidakcocokan, yang menyesuaikan adalah adapter, bukan port.
