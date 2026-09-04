# Feature Specification: Portal Latihan Siswa Eduscreen (v1)

**Feature Branch**: `001-student-exercise-portal`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "Portal siswa untuk sekolah dan lembaga bimbingan belajar. Memudahkan proses exercise siswa dalam organisasi client, dan memudahkan guru mendistribusikan soal latihan."

> **Sumber**: perilaku terperinci beserta `BR-*` dan `AC-*` tinggal di `business-rules.md`;
> kosakata domain di `CONTEXT.md`; aturan teknis di `CONSTITUTION.md`. Rujukan `BR-*` di bawah
> menunjuk ke aturan asal, bukan ke detail implementasi.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Menyiapkan Ruangan dan penggunanya (Priority: P1)

Client Admin sebuah sekolah membuat Ruangan untuk tahun ajaran berjalan, mendaftarkan akun Guru
dan Siswa, lalu menempatkan mereka ke Ruangan yang sesuai. Guru dan Siswa menerima undangan,
menetapkan password sendiri, dan masuk ke portal masing-masing.

**Why this priority**: Tanpa Ruangan berisi orang, tidak ada satu pun bagian lain yang bisa
dipakai. Ini fondasi yang menentukan siapa boleh melihat apa.

**Independent Test**: Client Admin membuat satu Ruangan, satu Guru, dan lima Siswa; ketiga peran
berhasil masuk dan masing-masing melihat portal yang sesuai perannya — tanpa satu pun Exercise
atau Assignment dibuat.

**Acceptance Scenarios**:

1. **Given** Client Admin sudah masuk, **When** ia membuat Ruangan `Kelas 4B 2026/2027` dan
   menempatkan lima Siswa ke dalamnya, **Then** kelima Siswa melihat Ruangan itu di portalnya.
2. **Given** seorang Siswa terdaftar di `Kelas 4B` dan `Bimbel Intensif SBMPTN Group B`,
   **When** ia membuka portalnya, **Then** kedua Ruangan tampil dengan penandanya masing-masing.
3. **Given** Client Admin membuat akun Guru baru, **When** akun tersimpan, **Then** undangan
   terkirim ke email Guru dan Guru bisa menetapkan password sendiri lewat tautan itu.
4. **Given** Guru ditugaskan hanya di `Kelas 4A`, **When** ia membuka daftar Ruangan,
   **Then** hanya `4A` yang terlihat dan Ruangan lain tidak bisa diakses dengan cara apa pun.

---

### User Story 2 - Menerbitkan Quiz dan mengerjakannya sampai keluar nilai (Priority: P1)

Guru menelusuri bank soal Client, memilih soal lintas Subject dan Topic, menyusunnya menjadi satu
Exercise, lalu menerbitkannya sebagai Quiz ke Ruangan dengan durasi pengerjaan dan batas akhir.
Siswa membuka portalnya, menekan Mulai, mengerjakan dengan urutan soal yang diacak khusus untuk
dirinya, dan menerima nilai pilihan gandanya begitu selesai. Guru membuka rekap satu Ruangan.

**Why this priority**: Inilah lingkaran nilai utama produk — dari materi guru sampai nilai siswa.
Sepotong ini saja sudah merupakan produk yang bisa dipakai sekolah.

**Independent Test**: Dengan satu Ruangan berisi lima Siswa, Guru menyusun Exercise sepuluh soal
pilihan ganda, menerbitkannya, kelima Siswa mengerjakannya, dan Guru melihat rekap nilai lengkap
tanpa memeriksa satu lembar pun secara manual.

**Acceptance Scenarios**:

1. **Given** Guru sedang merakit Exercise dan sudah menambahkan lima soal dari Topic `Aljabar`,
   **When** ia berpindah ke Subject `Fisika Kelas 9` dan menambahkan tiga soal lagi, **Then**
   Exercise memuat delapan soal lintas Subject tanpa hambatan.
2. **Given** Exercise siap dan Guru memilih Ruangan `4B`, durasi 60 menit, batas akhir Minggu
   23:59, **When** ia menerbitkan, **Then** Assignment tampil di portal seluruh Siswa `4B`.
3. **Given** Assignment terbit dengan pengacakan menyala, **When** Siswa A dan Siswa B menekan
   Mulai, **Then** keduanya menerima urutan soal berbeda, dan urutan tiap Siswa tidak berubah
   sepanjang pengerjaannya.
4. **Given** Siswa telah menjawab 12 dari 40 soal lalu menutup browser, **When** ia membuka
   kembali Assignment lima menit kemudian, **Then** ia kembali ke pengerjaan yang sama dengan 12
   jawaban utuh, urutan soal identik, dan sisa waktu sudah berkurang lima menit.
5. **Given** Siswa menekan Selesai, **When** seluruh soal berupa pilihan ganda, **Then** nilainya
   dihitung seketika tanpa campur tangan Guru.
6. **Given** batas akhir sudah lewat dan sebagian Siswa tidak pernah menekan Mulai, **When** Guru
   membuka rekap Ruangan, **Then** seluruh anggota Ruangan tampil — yang tidak mengerjakan
   berstatus belum mulai dengan nilai nol.

---

### User Story 3 - Berlatih dengan pembahasan seketika (Priority: P2)

Guru menerbitkan Exercise yang sama sebagai Practice, bukan Quiz. Siswa mengerjakannya satu soal
sekaligus: begitu satu jawaban dikirim, ia langsung tahu benar atau salah dan membaca
pembahasannya, lalu maju ke soal berikutnya. Ia boleh mengulang sebanyak yang ia mau.

**Why this priority**: Nilai jual utama untuk lembaga bimbingan belajar, tetapi Quiz sudah bisa
berdiri sendiri tanpanya.

**Independent Test**: Guru menerbitkan satu Exercise pilihan ganda sebagai Practice; Siswa
mengerjakannya, melihat pembahasan tiap soal, mengulang dua kali, dan aktivitasnya terlihat Guru
secara terpisah dari rekap nilai.

**Acceptance Scenarios**:

1. **Given** Assignment bermode Practice, **When** Siswa mengirim jawaban soal ketiga, **Then**
   benar/salah dan pembahasannya muncul seketika, dan jawaban itu tidak bisa diubah lagi.
2. **Given** Exercise memuat satu soal essay, **When** Guru menerbitkannya sebagai Practice,
   **Then** penerbitan ditolak dengan menyebutkan soal essay penyebabnya.
3. **Given** Siswa sudah menyelesaikan Practice tiga kali, **When** ia memulai lagi, **Then**
   pengerjaan baru diterima tanpa batas jumlah.
4. **Given** seorang Siswa mengerjakan empat Practice dan satu Quiz, **When** Guru membuka rekap
   nilai Ruangan, **Then** hanya hasil Quiz yang tampil di rekap nilai.

---

### User Story 4 - Menilai jawaban essay (Priority: P2)

Guru menerbitkan Quiz yang memuat soal essay. Setelah Siswa selesai, nilai pilihan gandanya
tampil sementara sambil menunggu penilaian. Guru membuka antrean penilaian, membaca tiap jawaban
essay, memberi nilai, dan hasil Siswa menjadi final.

**Why this priority**: Menjadikan produk layak untuk mata pelajaran yang tidak bisa diukur dengan
pilihan ganda, tetapi Quiz pilihan ganda sudah bernilai tanpanya.

**Independent Test**: Guru menerbitkan Exercise berisi sembilan soal pilihan ganda dan satu essay;
Siswa mengerjakannya; Guru menilai essaynya; nilai akhir berubah sesuai penilaian.

**Acceptance Scenarios**:

1. **Given** Siswa menyelesaikan Quiz beressay, **When** essay belum dinilai, **Then** hasilnya
   berstatus menunggu penilaian dengan nilai sementara yang ditandai jelas.
2. **Given** Guru memberi nilai essay pada skala 0–100, **When** seluruh essay di satu pengerjaan
   sudah dinilai, **Then** hasilnya menjadi final dengan nilai yang mencerminkan penilaian itu.
3. **Given** hasil sudah final, **When** Guru mengubah nilai essay dari 75 menjadi 90, **Then**
   hasil dihitung ulang seketika dan jejak perubahannya tercatat permanen — siapa, kapan, dari
   berapa ke berapa.
4. **Given** Guru tidak ditugaskan di Ruangan Assignment itu, **When** ia mencoba menilai,
   **Then** permintaan ditolak.

---

### User Story 5 - Membuka Client baru dengan bank soal terisi (Priority: P3)

Eduscreen Admin mendaftarkan sekolah baru: nama, zona waktu, dan akun Client Admin pertama. Ia
memilih paket konten master Eduscreen yang disalin ke bank soal sekolah itu, sehingga sekolah
punya materi siap pakai di hari pertama.

**Why this priority**: Mempercepat waktu-menuju-nilai untuk Client baru, tetapi sekolah tetap
bisa mulai dengan menulis soalnya sendiri.

**Independent Test**: Eduscreen Admin membuat satu Client dengan satu paket master berisi 20
soal; Client Admin masuk dan menemukan 20 soal itu sudah ada di bank soalnya.

**Acceptance Scenarios**:

1. **Given** Eduscreen Admin memilih satu paket master berisi 20 soal, **When** pendaftaran
   selesai, **Then** Client memiliki 20 soal sebagai miliknya sendiri dan boleh mengeditnya.
2. **Given** Client sudah mengadopsi sebuah soal master, **When** Eduscreen Admin mengubah soal
   master itu, **Then** salinan milik Client tidak ikut berubah.
3. **Given** sebuah sekolah membutuhkan mata pelajaran muatan lokal, **When** Client Admin
   membuatnya, **Then** mata pelajaran itu tersedia untuk sekolah tersebut dengan penanda bahwa
   ia buatan sekolah, bukan bawaan Eduscreen.

---

### User Story 6 - Memindahkan bank soal warisan (Priority: P3)

Client Admin sekolah yang sudah punya ribuan soal dalam berkas mengunggahnya, memeriksa
pratinjau, membaca daftar baris yang gagal beserta alasannya, lalu menyimpan yang valid.

**Why this priority**: Menghilangkan hambatan terbesar bagi sekolah yang sudah punya materi,
tetapi bank soal tetap bisa diisi lewat editor.

**Independent Test**: Client Admin mengunggah berkas 500 baris berisi tujuh baris cacat; sistem
menampilkan 493 baris valid dan tujuh kegagalan bernomor baris; penyimpanan memasukkan 493 soal.

**Acceptance Scenarios**:

1. **Given** berkas 500 baris dengan tujuh baris tanpa kunci jawaban, **When** diunggah,
   **Then** pratinjau menampilkan 493 baris valid dan tujuh kegagalan beserta nomor baris dan
   alasannya, dan penyimpanan hanya memasukkan yang valid.
2. **Given** berkas berisi 2.000 baris, **When** diunggah, **Then** berkas ditolak sebelum
   diproses dengan pesan yang menyebut batas 500 baris dan meminta pengguna memecahnya.

---

### User Story 7 - Mengulang untuk memperbaiki nilai (Priority: P3)

Guru menerbitkan Quiz yang boleh dikerjakan beberapa kali. Siswa mengulang, dan yang tercatat
sebagai nilai resminya adalah yang terbaik.

**Why this priority**: Mendorong latihan berulang di lembaga bimbingan belajar; Quiz sekali kerja
sudah bernilai tanpanya.

**Independent Test**: Guru menerbitkan Quiz dengan batas tiga kali pengerjaan; satu Siswa
memperoleh 60, 85, lalu 70; rekap Guru menampilkan 85 dan ketiga pengerjaan tetap bisa dibuka.

**Acceptance Scenarios**:

1. **Given** batas tiga kali dan Siswa sudah menyelesaikan yang pertama, **When** ia memulai yang
   kedua, **Then** soalnya diacak ulang menjadi urutan baru.
2. **Given** Siswa sudah menyelesaikan tiga kali, **When** ia mencoba yang keempat, **Then**
   permintaan ditolak.
3. **Given** nilai 60, 85, lalu 70, **When** Guru membuka rekap, **Then** nilai resminya 85.

---

### Edge Cases

- Siswa menekan Mulai sepuluh menit sebelum batas akhir Assignment padahal durasinya 60 menit:
  waktu efektifnya terpotong menjadi sepuluh menit, dan itu terlihat sejak detik pertama — bukan
  60 menit yang tiba-tiba terputus.
- Siswa menekan Mulai lalu menutup browser dan tidak pernah kembali: hasilnya tetap muncul di
  rekap Guru sebagai pengerjaan yang kedaluwarsa, bukan sebagai baris kosong.
- Siswa tidak pernah menekan Mulai sama sekali: ia tetap muncul di rekap sebagai belum mulai
  dengan nilai nol.
- Guru memperpanjang batas akhir setelah sebagian pengerjaan sudah kedaluwarsa: yang sudah
  kedaluwarsa tetap kedaluwarsa; hanya yang belum mulai dan yang masih berjalan yang diuntungkan.
- Guru memajukan batas akhir: ditolak; batas akhir hanya boleh diperpanjang atau Assignment
  ditutup lebih awal.
- Client Admin menghapus soal yang sedang dipakai Assignment berjalan: Siswa yang sedang
  mengerjakan tidak melihat perubahan apa pun.
- Guru menyadari salah ketik pada Exercise yang sudah diterbitkan: Exercise terkunci; ia
  menduplikasinya menjadi Exercise baru.
- Jaringan Siswa terputus sesaat: jawaban masuk antrean kirim ulang dengan indikator koneksi
  terlihat; putus berkepanjangan berarti Siswa menunggu tersambung, dan waktu terus berjalan.
- Sesi login Siswa hampir habis di tengah pengerjaan panjang: halaman pengerjaan menjaganya tetap
  hidup selama pengerjaan berlangsung.
- Siswa memundurkan jam perangkatnya: sisa waktu tidak bertambah.
- Client berada di zona waktu WITA sementara Siswa membuka dari WIB: batas akhir yang tampil
  adalah waktu Client, sama bagi semua orang.

## Requirements *(mandatory)*

### Functional Requirements

**Organisasi, peran, dan akses**

- **FR-001**: Sistem MUST memisahkan data tiap Client secara penuh; tidak ada peran yang bisa
  membaca data Client lain (BR-P04).
- **FR-002**: Sistem MUST menyediakan empat peran fungsional dengan kewenangan berbeda: Eduscreen
  Admin, Client Admin, Guru, dan Siswa.
- **FR-003**: Guru MUST hanya bisa menerbitkan Assignment ke Ruangan tempat ia ditugaskan
  (BR-P01, BR-M01).
- **FR-004**: Soal dan Exercise milik satu Client MUST terlihat oleh seluruh Guru di Client itu,
  tanpa memandang siapa pembuatnya (BR-P02, BR-E02).
- **FR-005**: Siswa MUST NOT bisa membaca soal di luar konteks pengerjaannya sendiri (BR-P03).
- **FR-006**: Client Admin MUST bisa menyalakan akses dukungan baca-saja bagi Eduscreen Admin;
  akses itu MUST padam sendiri setelah 4 jam dan setiap pembacaan MUST tercatat dalam jejak yang
  bisa ditunjukkan kepada Client (BR-P05).

**Akun dan Ruangan**

- **FR-007**: Setiap pengguna MUST masuk dengan alamat email (BR-U01 dst.).
- **FR-008**: Client Admin MUST bisa membuat akun Guru dan Siswa serta menempatkannya ke satu
  atau lebih Ruangan; satu Ruangan boleh punya banyak Guru (BR-U01).
- **FR-009**: Sistem MUST mengirim undangan akun dan tautan reset password lewat email (BR-U04).
- **FR-010**: Ruangan MUST bisa diarsipkan; Ruangan terarsip tidak menerima Assignment atau
  anggota baru, tetapi riwayat hasilnya tetap terbaca (BR-U02).
- **FR-011**: Menonaktifkan akun Siswa MUST NOT menghapus riwayat pengerjaan dan hasilnya
  (BR-U03).

**Taksonomi dan bank soal**

- **FR-012** *(digantikan, ADR-0018)*: ~~Sistem MUST menyediakan taksonomi dua lapis: Subject
  yang memuat jenjang di namanya, dan Topic di bawahnya.~~ Taksonomi menjadi tiga lapis —
  Subject (label) › Paket › Topic › Question. Subject tetap memuat jenjang di namanya (ADR-0004),
  tetapi Topic tidak lagi menggantung padanya: ia hidup di dalam satu Paket, dan Question
  menempel pada Topic milik Paketnya sendiri. Lihat AC-B01 dan AC-B02 di
  `business-rules.md`.
- **FR-013**: Subject MUST bisa berasal dari Eduscreen (berlaku untuk semua Client) atau dari
  satu Client, dengan asalnya ditandai jelas.
- **FR-014**: Client Admin dan Guru MUST bisa membuat Topic dan Question di lingkup Client-nya.
- **FR-015**: Question MUST melekat pada tepat satu Topic (BR-Q02).
- **FR-016**: Question pilihan ganda MUST memiliki minimal dua Option dengan tepat satu Option
  benar (BR-Q01).
- **FR-017**: Question dan Option MUST bisa memuat teks berformat, gambar, dan notasi matematika.
- **FR-018**: Question dan Exercise yang dihapus MUST hilang dari pencarian bank soal namun tetap
  utuh di Exercise, Assignment, dan pengerjaan yang sudah memakainya; Siswa yang sedang
  mengerjakan MUST NOT melihat perubahan apa pun (BR-Q04).
- **FR-019**: Sistem MUST menyediakan pencarian bank soal yang menemukan kata di dalam isi soal
  tanpa terganggu format di dalamnya.

**Konten master dan onboarding**

- **FR-020**: Eduscreen Admin MUST bisa mendaftarkan Client baru dengan nama, zona waktu, dan
  akun Client Admin pertama (BR-O01).
- **FR-083**: Eduscreen Admin MUST bisa mengubah nama dan zona waktu sebuah Client yang sudah
  berdiri; zona di luar tiga zona Indonesia MUST ditolak, dan perubahan zona MUST NOT menggeser
  satu pun tanggal yang sudah tersimpan (BR-O07, BR-O08).
- **FR-084**: Eduscreen Admin MUST bisa menghentikan (`SUSPENDED`) dan memulihkan (`ACTIVE`)
  sebuah Client. Client `SUSPENDED` MUST menolak login seluruh penggunanya dengan pesan yang
  tidak dapat dibedakan dari pesan password salah (BR-O09).
- **FR-085**: Eduscreen Admin MUST bisa menambah akun Client Admin sebuah Client, mengirim ulang
  undangannya, dan menonaktifkannya; menonaktifkan Client Admin aktif yang terakhir MUST ditolak
  (BR-O10). Layar itu MUST NOT memberi jalan membaca akun Guru, akun Siswa, maupun data
  pemakaian Client (BR-P04).
- **FR-021**: Konten master Eduscreen yang diadopsi Client MUST menjadi salinan penuh milik
  Client itu; perubahan di master MUST NOT merambat ke Client yang sudah mengadopsi.
- **FR-022**: Impor massal MUST menampilkan pratinjau dan laporan kegagalan per baris; kegagalan
  satu baris MUST NOT membatalkan baris lain (BR-Q05).
- **FR-023**: Satu berkas impor MUST dibatasi maksimum 500 baris, dengan penolakan yang
  menjelaskan batas itu (BR-Q06).

**Exercise dan penerbitan**

- **FR-024**: Guru MUST bisa menyusun Exercise dari Question mana pun di Client-nya, lintas
  Subject dan Topic dalam satu sesi perakitan (BR-E01).
- **FR-025**: Exercise MUST memuat minimal satu Question untuk bisa diterbitkan (BR-E03).
- **FR-026**: Exercise MUST menjadi tidak-bisa-diubah begitu Assignment pertamanya dibuat;
  perubahan dilakukan dengan menduplikasinya (BR-E04).
- **FR-027**: Satu Assignment MUST menyasar tepat satu Ruangan (BR-M02).
- **FR-028**: Guru MUST menentukan mode Quiz atau Practice pada saat menerbitkan, bukan pada saat
  menyusun Exercise.
- **FR-029**: Penerbitan sebagai Practice MUST ditolak bila Exercise memuat soal essay atau soal
  tanpa pembahasan, dengan menyebut soal penyebabnya (BR-M04, BR-Q03).
- **FR-030**: Quiz MUST memiliki durasi pengerjaan; Practice MUST boleh tanpa durasi; keduanya
  MUST memiliki batas akhir (BR-M03, BR-M05).
- **FR-031**: Guru MUST bisa mengatur pengacakan urutan soal dan urutan pilihan jawaban secara
  terpisah saat menerbitkan.
- **FR-032**: Setelah diterbitkan, hanya batas akhir yang MUST bisa diubah dan hanya
  diperpanjang; Assignment MUST NOT bisa dihapus, tetapi MUST bisa ditutup lebih awal (BR-A02,
  BR-A03, BR-A04).
- **FR-033**: Menutup Assignment lebih awal MUST menyelesaikan seluruh pengerjaan yang masih
  berjalan di dalamnya (BR-A05).

**Pengerjaan**

- **FR-034**: Pengerjaan MUST tercipta hanya pada saat Siswa menekan Mulai (BR-S01).
- **FR-035**: Urutan soal dan urutan pilihan MUST dibekukan pada saat itu dan MUST NOT berubah
  sepanjang pengerjaan, termasuk setelah Siswa terputus dan kembali (BR-S02).
- **FR-036**: Setiap jawaban MUST tersimpan segera tanpa tindakan menyimpan dari Siswa.
- **FR-037**: Membuka kembali Assignment yang pengerjaannya masih berjalan MUST mengembalikan
  Siswa ke pengerjaan itu, bukan memulai yang baru (BR-S06).
- **FR-038**: Pada mode Practice, jawaban MUST terkunci begitu dikirim, dan benar/salah beserta
  pembahasannya MUST tampil seketika (BR-S07).
- **FR-039**: Pada mode Quiz, Siswa MUST bisa berpindah ke soal mana pun dan mengubah jawaban
  sampai menekan Selesai.
- **FR-040**: Pengerjaan MUST berakhir lewat tiga sebab: Siswa menekan Selesai, durasi habis, atau
  batas akhir Assignment tercapai.
- **FR-041**: Waktu efektif satu pengerjaan MUST merupakan yang lebih awal antara akhir durasi dan
  batas akhir Assignment, ditetapkan saat Mulai ditekan dan ditampilkan sejak awal (BR-T04).
- **FR-042**: Sisa waktu MUST ditentukan sistem, bukan jam perangkat Siswa (BR-T03).
- **FR-043**: Perpanjangan batas akhir MUST NOT menghidupkan kembali pengerjaan yang sudah
  berakhir (BR-T06).
- **FR-044**: Jawaban yang tiba setelah waktu efektif habis MUST ditolak (BR-T08).
- **FR-045**: Pengerjaan MUST bisa dilanjutkan setelah browser tertutup atau perangkat mati,
  tanpa kehilangan jawaban yang sudah terkirim (BR-S08).
- **FR-046**: Sistem MUST memberi tahu Siswa saat koneksi terganggu dan mencoba mengirim ulang
  jawaban yang tertunda.

**Penilaian dan hasil**

- **FR-047**: Setiap soal MUST bernilai sama; tidak ada pengurangan nilai untuk jawaban salah
  (BR-C01, BR-C02).
- **FR-048**: Soal pilihan ganda MUST dinilai otomatis saat pengerjaan berakhir (BR-C03).
- **FR-049**: Soal yang tidak dijawab MUST dihitung salah saat pengerjaan berakhir (BR-C06).
- **FR-050**: Guru MUST menilai jawaban essay pada skala 0–100, yang diubah sistem menjadi bagian
  dari nilai soal itu (BR-C04).
- **FR-051**: Hasil yang masih memuat essay belum dinilai MUST ditandai sementara dan menjadi
  final setelah seluruh essaynya dinilai (BR-C05, BR-C10).
- **FR-052**: Setiap perubahan nilai essay MUST meninggalkan jejak permanen berisi pelaku, waktu,
  dan nilai sebelum serta sesudahnya (BR-G03).
- **FR-053**: Bila Assignment membolehkan pengerjaan berulang, nilai resmi Siswa MUST yang
  tertinggi, dengan seluruh pengerjaan tetap bisa dibuka Guru (BR-L03).
- **FR-054**: Hasil Practice MUST terpisah dari rekap nilai dan MUST NOT membatasi jumlah
  pengerjaan (BR-C08, BR-M06).
- **FR-055**: Kapan Siswa melihat nilai dan pembahasan MUST mengikuti mode: Practice seketika per
  soal; Quiz sesuai pengaturan Guru pada Assignment itu.

**Laporan**

- **FR-056**: Rekap satu Assignment MUST menampilkan seluruh anggota Ruangan, bukan hanya yang
  mengerjakan; yang tidak pernah mulai tampil dengan nilai nol (BR-L01).
- **FR-057**: Membuka rekap MUST menyelesaikan pengerjaan yang sudah lewat waktunya sehingga
  angkanya lengkap (BR-L02).
- **FR-058**: Portal Siswa MUST menampilkan Assignment aktif dari seluruh Ruangan yang ia ikuti
  beserta riwayat hasilnya sendiri.

**Waktu dan zona**

- **FR-059**: Setiap Client MUST memiliki satu zona waktu, dan seluruh batas akhir serta tampilan
  waktu MUST memakai zona itu bagi semua penggunanya (BR-T01, BR-T02).

### Key Entities

- **Client**: sekolah atau lembaga bimbingan belajar yang berlangganan; akar pemisahan data;
  memiliki satu zona waktu.
- **Pengguna**: satu orang dengan satu peran — Eduscreen Admin, Client Admin, Guru, atau Siswa;
  dikenali lewat alamat email.
- **Subject**: mata pelajaran pada satu jenjang, berasal dari Eduscreen atau dari satu Client.
- **Topic**: sub-bahasan di bawah satu Subject.
- **Question**: satu butir soal, pilihan ganda atau essay, melekat pada tepat satu Topic, memuat
  konten kaya dan pembahasan.
- **Option**: satu pilihan jawaban pada soal pilihan ganda; tepat satu benar.
- **Exercise**: kumpulan soal terurut yang disusun untuk satu tujuan; netral terhadap mode;
  terkunci setelah diterbitkan.
- **Ruangan**: kelompok belajar milik satu Client, beranggotakan Guru dan Siswa; bisa diarsipkan.
- **Assignment**: Exercise yang diterbitkan ke satu Ruangan dengan mode, durasi, batas akhir,
  pengaturan pengacakan, dan batas pengulangan.
- **Pengerjaan (Session)**: satu kali upaya seorang Siswa atas satu Assignment, memuat salinan
  beku urutan soal dan seluruh jawabannya.
- **Hasil (Result)**: nilai terhitung dari satu pengerjaan beserta status penilaiannya dan
  penanda apakah ia berasal dari Quiz atau Practice.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Guru dapat berpindah dari "punya materi" ke "soal sampai di portal siswa" dalam
  waktu kurang dari 10 menit.
- **SC-002**: 100% jawaban pilihan ganda dinilai tanpa campur tangan manusia; Guru tidak
  memeriksa satu lembar pun secara manual untuk memperoleh rekap Ruangan.
- **SC-003**: 100% pengerjaan yang terputus di tengah jalan dapat dilanjutkan tanpa kehilangan
  jawaban yang sudah terkirim dan tanpa berubahnya urutan soal.
- **SC-004**: Rekap satu Ruangan menampilkan 100% anggotanya, termasuk yang tidak pernah
  mengerjakan, tanpa Guru perlu menyusun daftar itu sendiri.
- **SC-005**: Client baru memiliki bank soal terisi pada hari pertama tanpa mengetik satu soal
  pun.
- **SC-006**: Sistem melayani 2.000 siswa mengerjakan serentak dalam satu Client, dan sekitar
  10.000 siswa serentak di seluruh platform, tanpa penurunan mutu layanan yang dirasakan siswa.
- **SC-007**: Rekap satu Ruangan berisi 40 siswa tersaji dalam waktu kurang dari 3 detik.
- **SC-008**: Tidak ada satu pun kasus data satu Client terbaca oleh Client lain, dibuktikan
  pengujian otomatis pada setiap perubahan.
- **SC-009**: 90% Guru berhasil menerbitkan Assignment pertamanya tanpa bantuan.
- **SC-010**: Dua siswa pada Assignment yang sama menerima urutan soal berbeda pada 100%
  Assignment yang pengacakannya menyala.

## Assumptions

- **Lingkup v1**: hanya alur inti — taksonomi, bank soal, Exercise, Ruangan, Assignment,
  pengerjaan, dan hasil. Analitik lanjutan, notifikasi tugas dan pengingat deadline, langganan
  dan penagihan, deteksi kecurangan, serta pengerjaan luring berada di luar lingkup ini.
- **Email transaksional termasuk lingkup**: undangan akun dan reset password. Email pemberitahuan
  tidak.
- **Email wajib untuk semua peran**, termasuk Siswa jenjang dasar. Sekolah menyediakan alamat
  sekolah atau alamat orang tua; ini beban penyiapan Client, dan disebut sejak percakapan
  penjualan.
- **Pengerjaan membutuhkan koneksi.** Gangguan sesaat ditangani pengiriman ulang; putus
  berkepanjangan berarti siswa menunggu, dan waktu terus berjalan di sisi sistem.
- **Satu Assignment satu Ruangan.** Menerbitkan ke tiga kelas menghasilkan tiga Assignment;
  antarmuka boleh menyediakan tindakan borongan.
- **Bobot soal seragam** dan pilihan ganda berjawaban tunggal. Bobot per soal, pilihan jamak,
  nilai sebagian, dan pengurangan nilai berada di luar lingkup v1.
- **Tahun ajaran ditangani lewat pengarsipan Ruangan** dan penamaan yang memuat periode; tidak
  ada entitas tahun ajaran maupun alur kenaikan kelas.
- **Impor v1 hanya menerima soal berbasis teks.** Soal bergambar dan berumus dimasukkan lewat
  editor.
- **Angka beban pada SC-006 adalah hipotesis yang wajib dibuktikan uji beban**, bukan fakta yang
  sudah terukur.
- **Konten master Eduscreen sudah tersedia** untuk diadopsi Client pada saat pendaftaran; mengisi
  konten master itu sendiri adalah pekerjaan Eduscreen Admin di luar alur ini.
- **Antarmuka berbahasa Indonesia saja.**
