# Feature Specification: Question Bank Master Eduscreen (v1)

**Feature Branch**: `002-master-question-bank`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "saya untuk membuat bank soal di sisi eduscreen admin, lalu soal itu nanti bisa diambil oleh admin client, lalu guru bisa mengambil soal secara granular untuk diracik menjadio exercise"

> **Sumber**: kosakata domain di `CONTEXT.md`; aturan teknis di `CONSTITUTION.md`; perilaku hilir
> (adopsi, perakitan Exercise, penerbitan Assignment) sudah tertulis sebagai `BR-*` dan `AC-*` di
> `specs/001-student-exercise-portal/business-rules.md`. Rujukan `FR-*` di bawah nomor 060 menunjuk
> ke kebutuhan spesifikasi 001, bukan ke kebutuhan baru.

> **Hubungan dengan spesifikasi 001**: 001 sudah menetapkan bahwa konten master ada, bahwa Client
> Admin mengadopsinya sebagai salinan penuh (FR-020, FR-021), dan bahwa Guru meracik Exercise
> lintas Subject dan Topic (FR-024). Yang belum pernah dispesifikasikan adalah **dari mana konten
> master itu berasal**: tidak ada satu pun kebutuhan yang menjelaskan bagaimana Eduscreen Admin
> menulis, mengurasi, dan menerbitkannya. Spesifikasi ini menutup hulu tersebut dan menyambungkan
> katalog agar penelusurannya berlangsung per Question, bukan hanya per paket.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Eduscreen Admin menulis Question master (Priority: P1)

Eduscreen Admin membuka ruang kerja konten master, memilih sebuah Subject Eduscreen, membuat Topic
di bawahnya, lalu menulis Question pilihan ganda dan Question essay lengkap dengan pembahasannya.
Ia menutup layar, kembali esok hari, dan menemukan kembali pekerjaannya lewat pencarian.

**Why this priority**: Tanpa ini tidak ada konten master sama sekali, sehingga seluruh alur adopsi
di spesifikasi 001 berdiri di atas ruang kosong. Ini satu-satunya cerita yang bila hilang membuat
sisanya tidak bermakna.

**Independent Test**: Eduscreen Admin membuat satu Topic, menulis lima Question di bawahnya, lalu
menemukan kelimanya lewat pencarian kata pada isi soal — seluruhnya tanpa menyentuh Client mana pun.

**Acceptance Scenarios**:

1. **Given** Eduscreen Admin memilih Subject Eduscreen `Matematika Kelas 4`, **When** ia membuat
   Topic `Pecahan` dan menulis satu Question pilihan ganda dengan empat pilihan dan satu kunci,
   **Then** Question itu tersimpan sebagai milik Eduscreen dan muncul di daftar konten master.
2. **Given** Eduscreen Admin menulis Question pilihan ganda dengan dua kunci jawaban benar,
   **When** ia menyimpannya, **Then** penyimpanan ditolak dengan alasan yang menyebut aturan tepat
   satu kunci.
3. **Given** ada 200 Question master, **When** Eduscreen Admin mencari kata yang ada di dalam batang
   soal, **Then** hasilnya memuat Question itu tanpa terganggu format di dalamnya.
4. **Given** Eduscreen Admin sedang berada di ruang kerja konten master, **When** ia mencoba
   menelusuri Question milik sebuah Client, **Then** tidak ada jalan menuju ke sana.

---

### User Story 2 - Menerbitkan dan menarik konten master (Priority: P1)

Eduscreen Admin menggarap satu batch Question selama beberapa hari. Selama masih digarap, tidak ada
Client yang melihatnya. Setelah dirasa matang, ia menerbitkannya sekaligus. Belakangan ia menemukan
kekeliruan pada satu Question dan menariknya dari peredaran.

**Why this priority**: Tanpa keadaan terbit, setiap penekanan simpan langsung menayangkan pekerjaan
setengah jadi ke seluruh sekolah. Ini pasangan tak terpisahkan dari User Story 1, bukan penyempurnaan.

**Independent Test**: Buat dua Question master, terbitkan satu, lalu buka katalog dari sebuah Client:
hanya satu yang terlihat dan hanya satu yang bisa diadopsi.

**Acceptance Scenarios**:

1. **Given** satu Question master masih digarap, **When** Client Admin membuka katalog, **Then**
   Question itu tidak muncul dan tidak bisa diadopsi lewat cara apa pun.
2. **Given** satu Question master sudah terbit dan sudah diadopsi 40 Client, **When** Eduscreen
   Admin menariknya dari peredaran, **Then** Question itu hilang dari katalog seluruh Client
   sementara ke-40 salinan yang sudah ada tetap utuh dan tetap bisa dipakai.
3. **Given** sebuah Exercise master memuat satu Question yang belum terbit, **When** Eduscreen Admin
   menerbitkan Exercise itu, **Then** penerbitan ditolak dengan menyebut Question penyebabnya.
4. **Given** sebuah Question master sudah terbit dan diadopsi sebuah Client, **When** Eduscreen Admin
   memperbaiki salah ketik pada Question master itu, **Then** salinan milik Client tidak berubah.

---

### User Story 3 - Merakit Exercise master sebagai paket kurasi (Priority: P2)

Eduscreen Admin mengumpulkan Question master yang tersebar di beberapa Subject dan Topic menjadi satu
Exercise master bernama, misalnya `Persiapan UTS Matematika Kelas 4`. Paket itu kemudian bisa dipilih
saat mendaftarkan Client baru dan bisa diadopsi utuh dari katalog.

**Why this priority**: Mempercepat onboarding dan memberi Client Admin satuan yang bermakna, tetapi
adopsi per Question sudah cukup untuk membuat produk berjalan tanpanya.

**Independent Test**: Rakit satu Exercise master berisi 20 Question dari dua Subject berbeda,
terbitkan, lalu daftarkan Client baru dengan memilih paket itu; Client Admin menemukan 20 Question
dan satu Exercise di Question Bank miliknya pada login pertama.

**Acceptance Scenarios**:

1. **Given** Eduscreen Admin sudah menambahkan lima Question dari Topic `Pecahan`, **When** ia
   berpindah ke Subject `Bahasa Indonesia Kelas 4` dan menambah tiga Question lagi, **Then** Exercise
   master memuat delapan Question lintas Subject tanpa peringatan apa pun.
2. **Given** sebuah Exercise master kosong, **When** Eduscreen Admin menerbitkannya, **Then**
   penerbitan ditolak karena harus memuat minimal satu Question.
3. **Given** sebuah Exercise master sudah diadopsi banyak Client, **When** Eduscreen Admin mengubah
   susunan isinya, **Then** perubahan diterima — Exercise master tidak pernah terkunci — dan tidak
   satu pun salinan Client ikut berubah.

---

### User Story 4 - Client Admin menelusuri katalog per Question (Priority: P2)

Client Admin membuka katalog Eduscreen, menyaring per Subject dan Topic, mencari kata kunci, lalu
mencentang sepuluh Question yang cocok dengan kurikulum sekolahnya dan mengadopsinya sekaligus.
Sisanya ia lewatkan.

**Why this priority**: Sekolah jarang mau seluruh isi paket. Tanpa penelusuran granular, adopsi
menjadi ambil-semua-atau-tidak-sama-sekali dan bank soal Client cepat penuh materi yang tak dipakai.

**Independent Test**: Katalog berisi 50 Question master terbit di tiga Topic; Client Admin menyaring
ke satu Topic, mencentang sepuluh, mengadopsi, lalu menemukan tepat sepuluh Question baru di Question
Bank sekolahnya.

**Acceptance Scenarios**:

1. **Given** katalog memuat 50 Question master terbit, **When** Client Admin menyaring per Topic dan
   mencari sebuah kata, **Then** ia melihat Question yang cocok satu per satu, bukan hanya daftar
   paket.
2. **Given** Client Admin mencentang sepuluh Question, **When** ia mengadopsi, **Then** kesepuluhnya
   menjadi milik sekolahnya sepenuhnya dan boleh diedit, dan ringkasan menyebut berapa Question dan
   Topic yang tersalin.
3. **Given** sebuah Question master sudah pernah diadopsi sekolah itu, **When** Client Admin membuka
   katalog lagi, **Then** Question itu ditandai sudah diadopsi sehingga ia tidak menggandakannya
   tanpa sadar.
4. **Given** Question master berada di bawah Subject Eduscreen, **When** adopsi selesai, **Then**
   tidak ada Subject baru dibuat untuk sekolah itu; Topic dan Question yang tersalin menunjuk Subject
   Eduscreen yang sama.

---

### User Story 5 - Guru meracik Exercise dari hasil adopsi (Priority: P3)

Guru membuka perakit Exercise, menelusuri Question Bank sekolahnya, dan menemukan soal hasil adopsi
berdampingan dengan soal buatan rekannya. Ia memilih beberapa dari masing-masing dan menyusunnya
menjadi satu Exercise.

**Why this priority**: Sebagian besar perilakunya sudah terpenuhi FR-024 dan BR-E01. Cerita ini ada
untuk membuktikan sambungan hulu-ke-hilir bekerja utuh, bukan untuk menambah kemampuan baru.

**Independent Test**: Setelah Client Admin mengadopsi sepuluh Question, Guru menyusun satu Exercise
berisi lima soal adopsi dan tiga soal buatan sekolah tanpa langkah tambahan apa pun.

**Acceptance Scenarios**:

1. **Given** sepuluh Question baru diadopsi Client Admin, **When** Guru membuka Question Bank
   sekolahnya, **Then** kesepuluhnya terlihat dan bisa dipakai seperti soal buatan sendiri.
2. **Given** Guru sedang merakit Exercise, **When** ia mencari konten master yang belum diadopsi
   sekolahnya, **Then** konten itu tidak muncul — jalur adopsi adalah kewenangan Client Admin.

---

### Edge Cases

- Question master ditarik dari peredaran saat sedang berada di dalam Exercise master yang terbit —
  Exercise itu tidak boleh diam-diam menyusut isinya bagi Client yang belum mengadopsinya.
- Topic Eduscreen dihapus padahal masih memayungi Question master yang terbit.
- Client Admin mengadopsi Question yang sama dua kali, sengaja maupun tidak.
- Client Admin membuka katalog ketika belum ada satu pun konten master yang terbit.
- Question master bertipe essay diadopsi lalu hendak diterbitkan Guru sebagai Practice — aturan
  BR-M04 dan BR-Q03 di sisi Client tetap yang menolak, bukan aturan baru di sisi master.
- Eduscreen Admin dan seorang Client Admin menyunting hal yang bersinggungan pada saat bersamaan:
  yang satu menarik Question dari peredaran, yang lain sedang mengadopsinya.
- Dua Eduscreen Admin menyunting Question master yang sama secara bersamaan.
- Adopsi sebuah Exercise master yang sebagian isinya sudah pernah diadopsi sekolah itu lewat jalur
  per Question.

## Requirements *(mandatory)*

### Functional Requirements

**Penulisan konten master**

- **FR-060**: Eduscreen Admin MUST bisa membuat, mengubah, dan menghapus Question yang dimiliki
  Eduscreen dan bukan milik Client mana pun.
- **FR-061**: Eduscreen Admin MUST bisa membuat Topic Eduscreen di bawah Subject Eduscreen mana pun.
- **FR-062**: Question master MUST tunduk pada aturan bentuk yang sama dengan Question milik Client:
  melekat pada tepat satu Topic, dan yang bertipe pilihan ganda memiliki minimal dua Option dengan
  tepat satu Option benar (FR-015, FR-016).
- **FR-063**: Question master MUST bisa memuat teks berformat, gambar, dan notasi matematika, dengan
  pembersihan konten yang sama seperti yang berlaku bagi konten Client (FR-017).
- **FR-064**: Eduscreen Admin MUST bisa mencari Question master berdasarkan Subject, Topic, dan kata
  di dalam isi soal tanpa terganggu format di dalamnya.
- **FR-065**: Question dan Exercise master yang dihapus MUST hilang dari ruang kerja master dan dari
  katalog seluruh Client, sementara salinan yang sudah diadopsi MUST tetap utuh dan tetap bisa
  dipakai.

**Keadaan terbit**

- **FR-066**: Setiap Question dan Exercise master MUST berada pada salah satu dari dua keadaan:
  belum terbit, yang hanya terlihat oleh Eduscreen Admin, atau terbit, yang terlihat di katalog
  seluruh Client.
- **FR-067**: Konten master yang belum terbit MUST NOT muncul di katalog Client mana pun dan MUST
  NOT bisa diadopsi maupun disalin lewat pendaftaran Client baru.
- **FR-068**: Eduscreen Admin MUST bisa menarik konten master yang sudah terbit kembali dari
  peredaran; penarikan MUST NOT mengubah, menghapus, atau menandai salinan yang sudah diadopsi
  Client mana pun.
- **FR-069**: Exercise master MUST NOT bisa diterbitkan selama masih memuat Question yang belum
  terbit; penolakan MUST menyebut Question penyebabnya.
- **FR-070**: Mengubah konten master setelah diterbitkan MUST NOT merambat ke Client yang sudah
  mengadopsinya (FR-021).

**Exercise master**

- **FR-071** *(digantikan, ADR-0018)*: ~~Eduscreen Admin MUST bisa menyusun Exercise master dari
  Question master mana pun, berpindah bebas antar-Subject dan antar-Topic dalam satu sesi
  perakitan.~~ Exercise master dicabut: wadah jual Eduscreen sekarang Paket, dan Exercise kembali
  sepenuhnya milik alur Guru. Menyusun isi sebuah Paket master dari Paket master lain dilakukan
  lewat pinjam antar-Paket — lihat AC-B03 di `001-student-exercise-portal/business-rules.md`.
- **FR-072** *(digantikan, ADR-0018)*: ~~Exercise master MUST memuat minimal satu Question untuk
  bisa diterbitkan.~~ Aturannya tetap berlaku, tetapi pada satuan yang menggantikan Exercise
  master: Paket master tanpa satu pun Question ditolak terbit — lihat AC-B16.
- **FR-073** *(digantikan, ADR-0018)*: ~~Exercise master MUST tetap bisa diubah sepanjang
  hidupnya; penguncian yang berlaku bagi Exercise milik Client setelah Assignment pertamanya
  (FR-026) MUST NOT berlaku baginya, karena Exercise master tidak pernah diterbitkan sebagai
  Assignment.~~ Exercise master dicabut. Paket master juga tidak pernah menjadi Assignment,
  sehingga `locked_at` tidak berlaku baginya sama sekali; yang membatasi penyuntingan isinya
  adalah keadaan terbit Paket, bukan penguncian — lihat AC-B17.

**Katalog dan adopsi granular**

- **FR-074** *(digantikan, ADR-0018)*: ~~Katalog MUST menampilkan Question master terbit satu per
  satu, dapat disaring per Subject dan Topic serta dicari berdasarkan kata di dalam isi soal,
  berdampingan dengan daftar Exercise master terbit.~~ Katalog dan adopsi bergeser seluruhnya
  menjadi per Paket — Question tidak lagi ditampilkan maupun diadopsi satu per satu. Lihat AC-B05
  dan seterusnya di `001-student-exercise-portal/business-rules.md`.
- **FR-075** *(digantikan, ADR-0018)*: ~~Client Admin MUST bisa memilih beberapa Question master
  sekaligus dan mengadopsinya dalam satu tindakan.~~ Satuan adopsi bergeser menjadi Paket: Client
  Admin memilih beberapa Paket sekaligus, dan Question tidak lagi diadopsi satu per satu — lihat
  AC-B05. (`001-student-exercise-portal/business-rules.md` sudah mencatatnya digantikan; ini
  penandaan di tempatnya sendiri.)
- **FR-076**: Katalog MUST menandai konten master yang sudah pernah diadopsi Client yang sedang
  melihatnya, agar Client Admin tidak menggandakannya tanpa sadar.
- **FR-077**: Mengadopsi konten yang sudah pernah diadopsi MUST tetap diizinkan, tetapi MUST
  didahului peringatan yang menyebut bahwa salinan sebelumnya sudah ada.
- **FR-078**: Setiap adopsi MUST menghasilkan salinan penuh milik Client yang boleh diedit,
  dipindahkan Topic-nya, dan dihapus tanpa menyentuh konten master (FR-021).
- **FR-079** *(digantikan, ADR-0018)*: ~~Setiap adopsi MUST diakhiri ringkasan yang menyebut
  berapa Question, Topic, dan Exercise yang tersalin.~~ Ringkasan tetap wajib, tetapi menyebut
  Paket, Topic, dan Question: Exercise tidak pernah ikut tersalin sejak satuan adopsi menjadi
  Paket — lihat AC-B05.

**Batas kewenangan**

- **FR-080**: Ruang kerja konten master MUST NOT menyediakan jalan bagi Eduscreen Admin untuk
  membaca Question, Exercise, atau data operasional milik Client mana pun; satu-satunya jalur baca
  ke data Client tetap akses dukungan berizin dan berbatas waktu (FR-006).
- **FR-081**: Guru MUST NOT bisa mengadopsi konten master; ia meracik Exercise dari Question yang
  sudah ada di Client-nya (FR-024).
- **FR-082**: Aliran konten master MUST satu arah, dari Eduscreen ke Client. Konten milik Client
  MUST NOT bisa dipromosikan menjadi konten master, dan konten satu Client MUST NOT bisa mencapai
  Client lain lewat jalur apa pun.

### Key Entities

- **Question master**: satu butir soal yang dimiliki Eduscreen, bukan Client mana pun. Bentuknya
  identik dengan Question milik Client — melekat pada tepat satu Topic, bertipe pilihan ganda atau
  essay — dan perbedaannya hanya pada kepemilikan dan pada keadaan terbit yang melekat padanya.
- **Topic Eduscreen**: pengelompokan Question master di bawah sebuah Subject Eduscreen. Disalin ke
  Client saat adopsi, berbeda dengan Subject Eduscreen yang dibaca langsung dan tidak pernah
  disalin (FR-013).
- **Exercise master**: kumpulan Question master terurut yang berfungsi sebagai paket kurasi. Inilah
  satuan yang dipilih saat mendaftarkan Client baru dan yang bisa diadopsi utuh dari katalog. Tidak
  pernah menjadi Assignment, karena itu tidak pernah terkunci.
- **Keadaan terbit**: penanda pada Question master dan Exercise master yang menentukan apakah ia
  terlihat oleh Client. Bukan entitas tersendiri, melainkan sifat konten master; konten milik Client
  tidak memilikinya.
- **Katalog**: pandangan Client Admin atas seluruh konten master yang terbit. Bukan entitas — ia
  adalah satu-satunya tempat data milik Eduscreen boleh terbaca dari dalam konteks sebuah Client.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-011**: Eduscreen Admin menyiapkan satu Exercise master berisi 20 Question dari nol sampai
  terbit dalam satu sesi kerja, tanpa bantuan teknis dan tanpa menyentuh basis data.
- **SC-012**: Client Admin menemukan dan mengadopsi sepuluh Question tertentu dari katalog berisi
  5.000 Question dalam waktu di bawah dua menit.
- **SC-013**: Nol konten master yang belum terbit pernah muncul di katalog Client mana pun,
  dibuktikan dengan penelusuran menyeluruh atas seluruh Client.
- **SC-014**: Setelah adopsi, 100% perubahan pada konten master — termasuk penarikan dan penghapusan
  — tidak mengubah satu pun salinan milik Client.
- **SC-015**: 95% pencarian katalog atas 5.000 Question master mengembalikan hasil tanpa jeda yang
  terasa menunggu bagi penggunanya.
- **SC-016**: Client baru yang didaftarkan dengan satu Exercise master berisi 20 Question menemukan
  ke-20 Question dan satu Exercise itu di Question Bank miliknya pada login pertama Client Admin.
- **SC-017**: Nol upaya Eduscreen Admin membaca konten milik sebuah Client berhasil di luar jendela
  akses dukungan yang dinyalakan Client Admin.
- **SC-018**: Guru memakai Question hasil adopsi di perakit Exercise tanpa satu pun langkah tambahan
  setelah Client Admin mengadopsinya.

## Assumptions

- Penomoran kebutuhan dilanjutkan dari spesifikasi 001 — `FR-060` dan `SC-011` — bukan diulang dari
  `FR-001`. Nomor FR sudah dirujuk di kontrak dan di dalam kode, sehingga penomoran yang berulang
  akan membuat rujukan itu ambigu.
- Exercise master dipakai sebagai wadah kurasi. Alur pendaftaran Client di spesifikasi 001 sudah
  memilih "paket" saat onboarding, dan paket itu adalah Exercise master. Tidak ada jenis konten baru
  yang diperkenalkan spesifikasi ini.
- Adopsi tetap kewenangan Client Admin. Guru tidak diberi jalur adopsi langsung, sehingga sekolah
  tetap punya satu titik kendali atas apa yang masuk ke Question Bank-nya.
- Keadaan terbit hanya melekat pada konten master. Perilaku konten milik Client tidak berubah sama
  sekali oleh spesifikasi ini — tidak ada draf dan tidak ada penerbitan di sisi sekolah.
- Impor massal berkas untuk konten master di luar cakupan v1. Eduscreen Admin menulis lewat editor;
  impor berkas tetap milik Client Admin sebagaimana FR-022 dan FR-023.
- Tidak ada versi maupun riwayat revisi Question master di v1. Yang ada hanya keadaan terbit,
  penarikan, dan penghapusan.
- Subject Eduscreen sudah bisa dibuat sejak spesifikasi 001 (FR-013). Yang ditambahkan di sini adalah
  Topic dan Question di bawahnya.
- Adopsi tetap berupa salinan penuh sekali jalan. Tidak ada mekanisme sinkronisasi dari master ke
  Client, dan menambahkannya akan membatalkan keputusan yang sudah tercatat di `docs/adr/0001`.
