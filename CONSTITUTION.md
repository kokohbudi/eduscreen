# Konstitusi Teknis Eduscreen

Aturan mengikat untuk seluruh kode Eduscreen. `CONTEXT.md` menetapkan bahasa domain, `specs/001-student-exercise-portal/business-rules.md` menetapkan perilaku, dan dokumen ini menetapkan **bentuk teknis** yang mewujudkannya. Alasan di balik tiap pasal tercatat di `docs/adr/`.

Pelanggaran pasal mana pun adalah alasan sah untuk menolak sebuah pull request.

---

## Pasal 1 — Arsitektur Hibrida

Aplikasi terbagi dua segmen dengan gaya arsitektur berbeda. Pembagiannya berdasarkan **siapa yang mengendalikan hal yang dituju**, bukan berdasarkan ukuran modul.

**Hexagonal — untuk yang dikendalikan pihak ketiga.**
Identity & Access Management, Notification Engine, File Storage. Ketergantungan pada sistem luar diisolasi di balik interface `port.out`; implementasi konkret tinggal di `adapter.out`. Layer `service` tidak pernah mengetahui nama vendor.

**Layered — untuk inti bisnis yang kita kendalikan penuh.**
Bank soal, perakitan Exercise, penerbitan Assignment, exam engine, dan persistensi. Alurnya lurus: `controller` → `service` → `repository`. Tidak ada port, tidak ada adapter, tidak ada interface beranggota tunggal.

```text
com.eduscreen.app
├── config/                 # Spring Security, Database & System Config
├── modules/
│   ├── identity/           # HEXAGONAL — IAM & Auth
│   │   ├── port/in/        # Login & User Management Interfaces
│   │   ├── port/out/       # IdentityProviderPort (abstraksi)
│   │   └── adapter/out/    # DummyIdentityProviderAdapter (kelak: KeycloakAdapter)
│   │
│   └── assessment/         # LAYERED — Domain Core & DB
│       ├── controller/     # Thymeleaf / Web Endpoint
│       ├── service/        # Exam Engine & Session Management
│       ├── repository/     # Spring Data JPA Entities & Data Access
│       └── dto/            # Data Transfer Objects
```

**Aturan:**

- **TC-01** — Kode di `assessment` tidak boleh membuat port dan adapter untuk hal yang tidak melintasi batas sistem. Hexagonal adalah biaya yang dibayar untuk batas eksternal, bukan gaya penulisan default.
- **TC-02** — Kode di `service` tidak boleh menyebut nama vendor mana pun. `KeycloakUserDto` di dalam `service` adalah pelanggaran.
- **TC-03** — Modul `assessment` tidak boleh mengimpor kelas dari `identity.adapter`. Ia hanya boleh menyentuh `identity.port.in`.

Lihat `docs/adr/0007-arsitektur-hibrida.md`.

---

## Pasal 2 — Identity di Balik Port

Seluruh autentikasi dan manajemen kredensial berada di balik satu interface, agar migrasi ke Keycloak tidak menyentuh satu baris pun di layer `service`.

```java
// Port Interface (Hexagonal Outbound)
public interface IdentityProviderPort {
    boolean authenticate(String username, String rawPassword);
    UserIdentity createUser(CreateUserCommand command);
    void updatePassword(String userId, String newPassword);
}
```

Sampai Keycloak masuk, implementasinya adalah adapter sementara. Adapter itu **wajib** dikurung profil dan **wajib** menolak untuk hidup di luar `local` dan `demo`:

```java
@Component
@Profile({"local", "demo"})
@Qualifier("dummyIdentityProvider")
public class DummyIdentityProviderAdapter implements IdentityProviderPort {

    private static final Set<String> ALLOWED_ENVS = Set.of("local", "demo");

    @PostConstruct
    void refuseToRunOutsideAllowedEnvs() {
        // Sabuk pengaman kedua: profil bisa salah pasang, ini tidak.
        String env = System.getenv("EDUSCREEN_ENV");
        if (env == null || !ALLOWED_ENVS.contains(env)) {
            throw new IllegalStateException(
                "DummyIdentityProviderAdapter aktif di environment '" + env + "'. Menolak start.");
        }
        log.warn("=== IDENTITY DUMMY AKTIF ({}) — SATU PASSWORD BERLAKU UNTUK SEMUA AKUN ===", env);
    }

    @Override
    public boolean authenticate(String username, String rawPassword) {
        return "password123".equals(rawPassword);
    }
}
```

Perhatikan bahwa `env == null` kini **ditolak**, bukan dibiarkan lewat. Variabel yang lupa diset adalah kegagalan konfigurasi yang paling mungkin terjadi saat menyiapkan server baru, dan pada jalur autentikasi ia harus berbunyi keras, bukan diam.

**Aturan:**

- **TC-04** — Adapter identity yang menerima kredensial statis wajib beranotasi `@Profile({"local", "demo"})` **dan** memuat pemeriksaan gagal-cepat di atas, yang menolak start bila `EDUSCREEN_ENV` bukan `local` atau `demo` — termasuk bila variabelnya tidak diset sama sekali. Dua lapis, karena profil yang salah pasang adalah kegagalan diam-diam yang membuka seluruh platform.
- **TC-05** — Aplikasi wajib gagal start bila tidak ada tepat satu `IdentityProviderPort` yang aktif. Tidak boleh ada fallback diam-diam ke dummy.
- **TC-06** — Password mentah tidak pernah masuk log, tidak pernah masuk pesan galat, dan tidak pernah tersimpan di tabel aplikasi. Bila adapter berbasis DB lokal dibuat sebelum Keycloak, password disimpan dengan BCrypt, bukan teks polos.
- **TC-07** — Layer `service` memanggil `IdentityProviderPort`. Ia tidak boleh menyentuh Spring Security `AuthenticationManager` secara langsung.

Lihat `docs/adr/0008-identity-di-balik-port.md`.

---

## Pasal 3 — IDOR Zero Tolerance

Berlaku untuk **setiap** endpoint yang menyentuh Session, SessionAnswer, dan Result. Empat lapis, semuanya wajib, tidak ada yang boleh dianggap cukup sendirian.

### Lapis 1 — Pengenal tak tertebak

Primary key seluruh entitas yang pernah muncul di URL memakai **UUID v7**. Bukan auto-increment, bukan UUID v4.

`/session/102` bisa dijelajahi dengan menambah satu. `/session/b58c42a2-8921-4f11-...` tidak bisa.

UUID v7 dipilih di atas v4 karena ia terurut waktu: `SessionAnswer` adalah tabel dengan tulis paling deras di sistem ini — target beban 10.000 Session serentak (`spec.md` §11) — dan kunci acak v4 memecah lokalitas index B-tree pada beban tulis seperti itu. v7 sama-sama tidak tertebak, tanpa membayar fragmentasi.

### Lapis 2 — Verifikasi principal dan batas tenant

Setiap permintaan pengerjaan atau auto-save wajib mencocokkan pemilik Session dengan pengguna yang login, **dan** Client pemilik data dengan Client pengguna.

### Lapis 3 — Penguncian state di server

Server menolak setiap perubahan bila Session tidak lagi `IN_PROGRESS` atau bila `effectiveDeadline` sudah lewat — tanpa memandang apa yang dikirim klien. Manipulasi lewat Postman menabrak tembok yang sama dengan manipulasi lewat browser.

### Lapis 4 — Kegagalan yang tidak membocorkan apa pun

Session milik orang lain dan Session yang tidak ada menghasilkan respons yang **identik**: `404`. Membalas `403` untuk milik orang lain memberi tahu penyerang bahwa pengenal itu sah — mengubah tembok menjadi oracle yang bisa ditanyai.

```java
@Service
public class AssignmentSessionService {

    public SessionAnswerDto saveAnswer(UUID sessionId, SaveAnswerRequest request, UserPrincipal currentUser) {
        // Lapis 2 — kepemilikan dan batas Client ikut ke dalam query, bukan diperiksa setelahnya.
        // Session milik orang lain tidak pernah termuat ke memori.
        AssignmentSession session = sessionRepository
                .findByIdAndStudentIdAndClientId(sessionId, currentUser.getId(), currentUser.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found")); // Lapis 4 — 404, bukan 403

        // Lapis 3 — penguncian state
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new SessionNotWritableException("Session sudah selesai atau kedaluwarsa");
        }
        if (OffsetDateTime.now().isAfter(session.getEffectiveDeadline())) {
            sessionFinalizer.finalize(session); // BR-T08: jawabannya ditolak, sesinya difinalisasi
            throw new SessionNotWritableException("Session sudah melewati batas waktu");
        }

        // Auto-save...
    }
}
```

**Aturan:**

- **TC-08** — Kepemilikan dan `clientId` masuk ke dalam **klausa query**, bukan diperiksa setelah entitas termuat. Data milik Client lain tidak boleh pernah sampai ke memori proses.
- **TC-09** — Session milik orang lain dan Session yang tidak ada wajib menghasilkan respons identik: `404`, tanpa perbedaan pesan, kode, maupun waktu tanggap.
- **TC-10** — Setiap entitas milik Client membawa kolom `client_id`, dan setiap query yang menyentuhnya menyaring berdasarkan kolom itu. Tidak ada pengecualian untuk endpoint yang "hanya membaca".
- **TC-11** — Setiap endpoint baru yang menyentuh Session wajib disertai tes yang membuktikan permintaan lintas-Siswa dan lintas-Client mendapat `404`. Tanpa tes itu, endpoint tidak boleh digabung.
- **TC-12** — Pemeriksaan batas waktu memakai waktu server (`OffsetDateTime.now()` di JVM aplikasi), tidak pernah nilai yang dikirim klien (BR-T03).

Lihat `docs/adr/0009-uuid-v7-primary-key.md`.

### Penyelarasan nama

Glosarium menyebut tenant sebagai **Client** (`CONTEXT.md`). Kode dan skema database memakai `clientId` / `client_id`, bukan `tenantId` / `tenant_id`. Satu konsep, satu nama, dari glosarium sampai kolom database.

---

## Pasal 4 — Stack & Presentasi

| Lapis | Pilihan |
| --- | --- |
| Runtime | Java 25 LTS |
| Framework | Spring Boot 3.5+ (3.5.x mendukung JDK 17-25) |
| Database | PostgreSQL 16+ |
| Persistensi | Spring Data JPA |
| Render | Thymeleaf, dirender server, berbasis fragment |
| Interaktivitas | HTMX untuk pertukaran fragment dan auto-async; Alpine.js untuk state di klien |
| Styling | Tailwind CSS lewat CLI standalone, terikat ke build Maven (`frontend-maven-plugin`) |

**Aturan:**

- **TC-13** — Tidak ada SPA. Halaman dirender server; HTMX menukar fragment. Tidak ada React, Vue, atau kerangka klien lain yang masuk tanpa ADR baru.
- **TC-14** — Auto-save memanggil endpoint yang mengembalikan **fragment**, bukan JSON, agar satu jalur render melayani muat awal maupun pembaruan parsial.
- **TC-15** — Hitung mundur Timer adalah komponen Alpine yang murni menampilkan; sisa waktu yang berlaku selalu datang dari server dan disinkronkan berkala. Jam klien tidak pernah menjadi rujukan (BR-T03).
- **TC-16** — Kolom bertipe `JSONB` dipakai hanya bila bentuk datanya benar-benar tidak tetap. `SessionAnswer` berbentuk tetap — `selectedOptionId`, `essayText`, `isCorrect`, `essayScore` — jadi ia memakai kolom biasa. JSONB di sana akan membuang integritas referensial dan menyulitkan agregasi nilai tanpa imbalan apa pun.
- **TC-17** — Migrasi skema berjalan lewat **Flyway** dengan berkas SQL murni bernomor, dijalankan otomatis saat start. Migrasi adalah sumber kebenaran skema; entity JPA divalidasi terhadapnya. `ddl-auto` selain `validate` dilarang di luar pengembangan lokal.

Lihat `docs/adr/0010-server-rendered-htmx.md`.

---

## Pasal 5 — Konkurensi & Integritas Transaksi

Exam engine adalah satu-satunya bagian sistem tempat dua pihak bisa menulis objek yang sama pada saat yang sama. Aturan di pasal ini tidak boleh dilonggarkan demi kecepatan.

**Finalisasi Session.** ADR-0002 menetapkan finalisasi terjadi saat Session diakses, tanpa scheduler. Itu berarti Siswa yang memuat ulang halaman dan Guru yang membuka laporan bisa memfinalisasi Session yang sama secara bersamaan.

```java
@Transactional
public Result finalizeIfExpired(UUID sessionId) {
    // Kunci pesimistis: pembaca kedua menunggu, bukan menghitung ulang.
    AssignmentSession session = sessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

    if (session.getStatus() != SessionStatus.IN_PROGRESS) {
        return session.getResult(); // Sudah difinalisasi pihak lain. Idempoten.
    }
    // ... hitung dan simpan Result
}
```

- **TC-18** — Finalisasi Session mengambil kunci pesimistis (`SELECT … FOR UPDATE`) pada baris Session di dalam satu transaksi. Pemeriksaan status dilakukan **setelah** kunci didapat, bukan sebelumnya.
- **TC-19** — `result.session_id` memiliki unique constraint. Kunci pesimistis mencegah balapan; constraint memastikan kelalaian di jalur mana pun tidak sanggup melanggar aturan. Aturan yang tidak boleh dilanggar dijaga database, bukan niat baik kode.
- **TC-20** — Auto-save adalah **upsert** berkunci `sessionQuestionId`. Kiriman ulang berisi jawaban identik adalah no-op yang mengembalikan keadaan terkini dengan status sukses. Hanya jawaban **berbeda** untuk SessionQuestion terkunci (Practice, BR-S07) yang ditolak.

  Ini bukan kenyamanan, melainkan koreksi terhadap BR-S08: antrean coba-ulang di klien menjamin server akan menerima kiriman ganda. Server yang menolak kiriman ulang mengubah mekanisme pemulihan menjadi sumber kerusakan — Siswa melihat galat untuk jawaban yang sebenarnya sudah tersimpan.
- **TC-21** — Laporan Guru memfinalisasi Session kedaluwarsa dalam transaksi terpisah per Session, bukan satu transaksi panjang yang mengunci seluruh Ruangan sekaligus.

---

## Pasal 6 — Konten, Berkas & Pencarian

`Question.body` dan `Option.body` berisi konten kaya yang ditulis Guru dan ditayangkan ke seluruh Ruangan. Ini adalah jalur data dengan tingkat kepercayaan paling rendah di dalam sistem.

- **TC-22** — Konten kaya disanitasi dengan **allowlist** saat **menulis**. Database hanya berisi HTML yang sudah bersih; template merendernya dengan `th:utext`. Satu tempat pembersihan, bukan tersebar di setiap template.

  Alasannya bukan estetika: Thymeleaf `th:utext` merender HTML mentah, dan satu akun Guru yang jebol atau satu berkas impor bermuatan jahat menjadi stored XSS yang menyapu seluruh Client — di halaman tempat ujian sedang berlangsung.
- **TC-23** — Allowlist memuat tag format dasar, gambar, dan tabel. Tag `<script>`, `<style>`, `<iframe>`, `<object>`, atribut `on*`, serta URL berskema `javascript:` dilarang tanpa pengecualian.
- **TC-24** — Rumus matematika disimpan sebagai LaTeX di dalam delimiter dan dirender KaTeX di klien. Ia tidak pernah disimpan sebagai HTML hasil render, sehingga tidak pernah melewati jalur sanitasi sebagai markup.
- **TC-25** — Setiap kolom konten kaya berpasangan dengan kolom turunan berisi **teks polos** hasil pengupasan HTML, diperbarui pada operasi tulis yang sama. Pencarian bank soal hanya menyentuh kolom turunan itu.

  Mencari langsung ke kolom HTML akan mencocokkan nama tag dan atribut — kata kunci `img` memunculkan setiap soal bergambar — dan kata yang terpotong markup di tengahnya tidak akan pernah ketemu.
- **TC-26** — Gambar hanya dilayani lewat endpoint aplikasi yang memeriksa `client_id` dan peran pemanggil. Tidak ada penyajian berkas statis langsung dari direktori penyimpanan. Nama berkas adalah UUID, dan responsnya memakai header cache privat.

  Tanpa aturan ini, empat lapis anti-IDOR di Pasal 3 bisa dilewati dengan membagikan satu URL `.png`: soal ujian besok bocor lewat berkas, bukan lewat endpoint Session.
- **TC-27** — Unggahan gambar divalidasi dengan tiga langkah: batas ukuran tegas per berkas, tipe ditentukan dari **magic bytes** (bukan ekstensi maupun `Content-Type`, keduanya ditentukan pengunggah), dan gambar di-**encode ulang** saat disimpan untuk membuang metadata serta muatan yang menumpang di dalam berkas.
- **TC-28** — Akses ke penyimpanan berkas melewati `FileStoragePort` (Pasal 1, hexagonal). Layer `service` tidak pernah menyentuh API filesystem atau SDK penyimpanan objek secara langsung.

Lihat `docs/adr/0011-sanitasi-konten-saat-tulis.md`.

---

## Pasal 7 — Sesi & Autentikasi

- **TC-29** — Pengguna tetap login lewat sesi di sisi server dengan cookie `HttpOnly; Secure; SameSite=Lax`. Perlindungan session fixation aktif. Saat Keycloak masuk, yang berubah hanya langkah autentikasi awal menjadi OIDC authorization code; mekanisme sesinya tetap.
- **TC-30** — Permintaan HTMX yang tidak terautentikasi dibalas `401` berisi header `HX-Redirect`, **bukan** `302`. HTMX mengikuti pengalihan biasa dan akan menempelkan halaman login ke dalam slot fragmen — misalnya ke tengah lembar soal yang sedang dikerjakan.
- **TC-31** — Satu `@ControllerAdvice` merender seluruh galat sebagai fragmen kecil dengan status HTTP yang benar. Tidak ada penanganan galat yang hidup di JavaScript klien.
- **TC-32** — Selama Session berstatus `IN_PROGRESS`, halaman pengerjaan mengirim heartbeat ringan berkala agar sesi login tidak mati di tengah ujian. Timeout global tetap pendek; hanya halaman pengerjaan yang memperpanjang, sehingga sesi Client Admin dan Guru tidak ikut berumur panjang.

  Tanpa ini, Siswa yang membaca teks bacaan panjang selama 35 menit tanpa menekan apa pun tidak mengirim satu permintaan pun, dan sesinya mati meski Timer ujiannya masih tersisa.
- **TC-33** — Login dibatasi lajunya per akun **dan** per alamat IP, dengan penundaan menaik lalu penguncian sementara. Penghitung boleh tinggal di memori selama topologi masih satu instance (TC-42). Setiap percobaan gagal masuk log (dengan batasan TC-44).
- **TC-34** — **Adapter dummy tidak boleh menyentuh data siswa sungguhan.** Ia boleh hidup di `local` dan `demo`; ia tidak boleh hidup di environment mana pun yang memuat nama, email, jawaban, atau nilai siswa nyata. Kebijakan password ditunda ke Keycloak, sehingga tidak ada penyimpan kredensial layak produksi sampai Keycloak — atau adapter lokal sungguhan berbasis BCrypt — terpasang.

  Batasnya adalah **data**, bukan nama environment. Sebuah server demo yang diam-diam diisi data sekolah sungguhan telah menjadi produksi, apa pun labelnya.
- **TC-47** — Setiap halaman di environment `demo` menampilkan spanduk permanen yang menyatakan bahwa ini lingkungan peragaan berisi data karangan dan autentikasinya tidak nyata. Tidak boleh bisa ditutup pengguna.
- **TC-48** — Database `demo` tidak pernah dipulihkan dari cadangan produksi, dan tidak pernah menerima impor berisi data siswa, daftar kelas, atau bank soal milik Client sungguhan. Ini jalur kebocoran yang paling mungkin terjadi: seseorang menyalin data nyata ke demo agar peragaannya terasa meyakinkan.
- **TC-49** — Pengiriman email transaksional (BR-U04) **dinonaktifkan** di `demo`; undangan dan tautan reset dialihkan ke penampung uji, tidak pernah ke alamat sungguhan. Mengirim tautan reset password dari sistem yang autentikasinya palsu adalah cara tercepat mengubah peragaan menjadi insiden.

Lihat `docs/adr/0016-batas-pemakaian-adapter-dummy.md`.

---

## Pasal 8 — Persistensi & Skema

- **TC-35** — Soft delete ditegakkan otomatis lewat `@SQLRestriction`. Risiko kelalaiannya kecil: melewatkannya berarti Guru melihat soal yang sudah dihapus.
- **TC-36** — Penyaringan `client_id` ditulis **eksplisit** di tanda tangan setiap method repository. Filter otomatis Hibernate dan Row-Level Security tidak dipakai untuk ini.

  Kedua risiko itu tidak setara. Melewatkan filter soft delete menampilkan konten basi; melewatkan filter `client_id` membocorkan bank soal satu sekolah ke sekolah lain. Aturan terpenting layak terlihat di kode dan di tinjauan, bukan tersembunyi di anotasi yang lama-lama berhenti dipikirkan orang.
- **TC-37** — Setiap perubahan `essayScore` dan setiap perhitungan ulang Result dicatat ke tabel audit **hanya-sisip**: siapa, kapan, dari nilai berapa ke berapa. Terbatas pada nilai; bukan audit seluruh sistem.

  BR-G02 membolehkan Guru mengubah nilai yang sudah `FINAL`. Di sekolah, nilai yang berubah adalah bahan sengketa, dan sistem harus bisa menjawab siapa yang mengubahnya.

Lihat `docs/adr/0012-client-id-eksplisit.md`.

---

## Pasal 9 — Tes & Penegakan Aturan

Aturan yang tidak dijalankan apa pun akan luntur. Pasal ini menjadikan Pasal 1 sampai 8 bisa gagal di CI.

- **TC-38** — Tes service dan repository berjalan terhadap PostgreSQL sungguhan lewat **Testcontainers**. H2 dilarang: ia berbeda perilaku pada UUID, `timestamptz`, dan constraint khas PostgreSQL, sehingga tes hijau di H2 tidak membuktikan apa pun tentang produksi.
- **TC-39** — Nama tes merujuk pengenal `AC-*` dari `spec.md`, sehingga cakupan kriteria penerimaan bisa diperiksa mesin, bukan ditaksir manusia.
- **TC-40** — Satu kelas tes ArchUnit menegakkan TC-01 sampai TC-03: larangan impor lintas modul, batas paket, dan larangan nama vendor di layer `service`. Gagal di CI, bukan di tinjauan manusia.
- **TC-41** — Setiap endpoint yang menyentuh Session, SessionAnswer, Result, atau berkas wajib disertai tes yang membuktikan permintaan lintas-Siswa dan lintas-Client mendapat `404`. Ini penegakan konkret dari TC-11.

---

## Pasal 10 — Operasi

- **TC-42** — Topologi v1 adalah **satu instance**: sesi di memori, berkas di filesystem lokal di balik `FileStoragePort`. Konsekuensi yang diterima secara sadar: setiap deploy memutus Session yang sedang berjalan. Angka 10.000 Session serentak di `spec.md` §11 diperlakukan sebagai hipotesis yang wajib dibuktikan uji beban, bukan sebagai fakta.

  Pemicu pindah ke topologi mendatar adalah kebutuhan **deploy tanpa memutus ujian**, bukan angka bebannya.
- **TC-43** — Pencadangan penuh harian **plus** arsip WAL untuk pemulihan titik waktu, dengan uji pemulihan terjadwal. Cadangan yang belum pernah dipulihkan bukan cadangan, melainkan asumsi. Yang dipertaruhkan adalah pekerjaan ujian yang tidak bisa diulang.
- **TC-44** — Log berformat terstruktur dan wajib membawa `clientId`, `userId`, serta `sessionId` untuk penelusuran. Dilarang masuk log dalam bentuk apa pun: password, isi jawaban Siswa, isi soal, dan alamat email.
- **TC-45** — Impor Excel/CSV diproses **sinkron** dengan batas tegas 500 baris per berkas; berkas yang lebih besar ditolak dengan pesan untuk memecahnya. Batas ini menjaga ADR-0002 tetap utuh — ia ada supaya tidak ada yang "memperbaikinya" dengan memasukkan kembali infrastruktur pekerjaan latar.
- **TC-46** — Akses dukungan Eduscreen ke data Client bersifat **baca-saja**, dinyalakan Client Admin, padam otomatis setelah beberapa jam, dan setiap pembacaan tercatat di audit. Tidak ada akses permanen, dan tidak ada koneksi langsung ke database produksi untuk keperluan dukungan.

Lihat `docs/adr/0013-topologi-satu-instance.md`, `docs/adr/0014-impor-sinkron-berbatas.md`, dan `docs/adr/0015-akses-dukungan-break-glass.md`.

---

## Indeks aturan

| Kode | Ringkas |
| --- | --- |
| TC-01 | Hexagonal hanya di batas eksternal |
| TC-02 | `service` tidak menyebut nama vendor |
| TC-03 | `assessment` hanya menyentuh `identity.port.in` |
| TC-04 | Adapter dummy: `@Profile({"local","demo"})` + gagal-cepat |
| TC-05 | Gagal start bila `IdentityProviderPort` tidak tunggal |
| TC-06 | Password mentah tidak pernah di-log atau disimpan polos |
| TC-07 | `service` memanggil port, bukan Spring Security langsung |
| TC-08 | Kepemilikan dan `clientId` masuk klausa query |
| TC-09 | Milik orang lain dan tidak ada → `404` identik |
| TC-10 | Setiap entitas Client menyaring `client_id` |
| TC-11 | Endpoint Session wajib punya tes lintas-Siswa dan lintas-Client |
| TC-12 | Batas waktu memakai waktu server |
| TC-13 | Tanpa SPA |
| TC-14 | Auto-save mengembalikan fragment |
| TC-15 | Hitung mundur hanya tampilan |
| TC-16 | JSONB hanya untuk data yang benar-benar tak tetap |
| TC-17 | Flyway SQL murni; `ddl-auto` hanya `validate` |
| TC-18 | Finalisasi mengambil kunci pesimistis sebelum memeriksa status |
| TC-19 | Unique constraint pada `result.session_id` |
| TC-20 | Auto-save upsert idempoten; kiriman ulang identik = no-op |
| TC-21 | Finalisasi borongan: satu transaksi per Session |
| TC-22 | Sanitasi allowlist saat tulis, simpan HTML bersih |
| TC-23 | `<script>`, `<style>`, `<iframe>`, `on*`, `javascript:` dilarang |
| TC-24 | Rumus sebagai LaTeX, dirender KaTeX di klien |
| TC-25 | Kolom teks polos turunan untuk pencarian |
| TC-26 | Gambar hanya lewat endpoint berotorisasi, cache privat |
| TC-27 | Unggahan: batas ukuran + magic bytes + encode ulang |
| TC-28 | Penyimpanan berkas lewat `FileStoragePort` |
| TC-29 | Sesi server; cookie `HttpOnly; Secure; SameSite=Lax` |
| TC-30 | HTMX tak terautentikasi → `401` + `HX-Redirect` |
| TC-31 | Satu `@ControllerAdvice` merender galat sebagai fragmen |
| TC-32 | Heartbeat hanya selama Session `IN_PROGRESS` |
| TC-33 | Rate limit login per akun dan per IP |
| TC-34 | Adapter dummy tidak pernah menyentuh data siswa sungguhan |
| TC-35 | Soft delete otomatis lewat `@SQLRestriction` |
| TC-36 | `client_id` eksplisit di tiap method repository |
| TC-37 | Perubahan nilai tercatat di audit hanya-sisip |
| TC-38 | Tes memakai Testcontainers PostgreSQL; H2 dilarang |
| TC-39 | Nama tes merujuk `AC-*` dari spec |
| TC-40 | ArchUnit menegakkan TC-01 sampai TC-03 di CI |
| TC-41 | Endpoint bersasaran wajib punya tes `404` lintas-Siswa & lintas-Client |
| TC-42 | Satu instance; deploy memutus Session berjalan |
| TC-43 | Cadangan harian + WAL + uji pemulihan |
| TC-44 | Log terstruktur; password, jawaban, soal, email dilarang |
| TC-45 | Impor sinkron, maksimum 500 baris per berkas |
| TC-46 | Akses dukungan: baca-saja, berizin, berbatas waktu, teraudit |
| TC-47 | Spanduk permanen di setiap halaman environment `demo` |
| TC-48 | `demo` tidak pernah dipulihkan dari cadangan produksi |
| TC-49 | Email transaksional dinonaktifkan di `demo` |
