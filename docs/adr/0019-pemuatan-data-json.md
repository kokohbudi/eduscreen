# ADR-0019 — Muat pertama SSR, pemuatan data berikutnya lewat JSON

**Status**: Diterima
**Tanggal**: 2026-09-02
**Mengubah**: TC-13, TC-14, TC-31

## Konteks

TC-14 mewajibkan setiap endpoint pembaruan parsial membalas **fragmen**, bukan JSON, "agar satu
jalur render melayani muat awal maupun pembaruan parsial". TC-13 melarang SPA. Keduanya lahir
bersama ADR-0010.

Panel pinjam soal di Bank Soal membuat batas aturan itu terasa. Panel itu menampilkan soal lintas
Paket yang bisa disaring per Subject, Paket, Topic, dan kata kunci, dan penggunanya mencentang
beberapa soal — mungkin dari beberapa Subject sekaligus — sebelum menyalinnya. Artinya keadaan
pilihan harus bertahan melewati setiap perubahan penyaring.

Dengan fragmen, tiap perubahan penyaring menukar potongan HTML yang memuat centangan itu. Keadaan
pilihan karena itu harus hidup di luar bagian yang tertukar, dan setiap penambahan penyaring baru
menambah satu hal lagi yang harus dijaga agar tidak ikut tersapu.

Pemilik produk memutuskan aturannya diubah.

## Keputusan

**Muat pertama sebuah URL tetap dirender server.** Membuka `/bank-soal` menghasilkan HTML lengkap.
Tidak berubah.

**Pembedanya satu pertanyaan: apakah balasannya membawa konten yang harus mendarat di DOM sebagai
HTML?**

Kalau ya — pembahasan Practice, batang soal, apa pun yang berisi kolom `*_html` tersimpan atau rumus
LaTeX yang perlu dirender ulang — balasannya **fragment**. Alasannya bukan selera: `th:utext` di
template server adalah satu-satunya tempat keluaran tanpa escape yang teraudit di proyek ini.
Memindahkannya ke `innerHTML` di klien memindahkan titik itu ke kode yang tidak dijaga satu pun tes
render.

Kalau tidak — balasannya cuma keadaan, hitungan, atau angka — balasannya **JSON**, dan dirender
Alpine di klien.

Tiga pagar yang tetap berdiri:

1. **Tetap tanpa kerangka SPA.** Alpine, yang sudah ada di proyek ini, adalah satu-satunya alat
   render klien yang diizinkan. React, Vue, dan sejenisnya tetap butuh ADR tersendiri.
2. **Endpoint JSON menegakkan otorisasi dan penyaringan tenant yang sama** dengan jalur SSR-nya.
   Ia bukan pintu belakang: `clientId` tetap disaring di dalam query utama, dan milik Client lain
   tetap menghasilkan nol hasil atau 404, bukan galat yang membedakannya.
3. **Galat tetap punya jalur yang terlihat.** Endpoint JSON membalas status HTTP yang benar
   beserta pesan yang bisa ditampilkan; klien wajib menampilkannya. Galat yang ditelan diam-diam
   adalah pelanggaran, sama seperti sebelumnya.

## Konsekuensi

**Yang didapat.** Keadaan yang dipegang klien — pilihan, sorotan, gulir — berhenti menjadi sesuatu
yang harus diselamatkan dari swap fragmen. Muatan jaringan untuk penyaringan berulang mengecil,
karena yang dikirim data, bukan markup.

**Yang dibayar, dan ini bagian yang jujur.**

Satu permukaan kini punya **dua jalur render**: HTML server untuk muat pertama, template klien
untuk pembaruan. Itu persis yang TC-14 cegah, dan alasannya bukan teoretis. Rangkaian pekerjaan
Bank Soal menemukan berulang kali bahwa satu aturan yang ditulis dua kali akan menyimpang tanpa
menggagalkan apa pun: gerbang penerbitan yang hanya menutup satu arah, penomoran soal yang benar
di tiga penulis dan salah di satu, kerangka tabel yang disalin lalu tidak lagi sinkron.

Lebih tajam lagi: **tes render `MockMvc` berhenti menangkap galat render pada bagian yang dirender
klien.** Dua galat templat pernah lolos seluruh tes layanan di proyek ini dan baru meledak saat
halaman disentuh — itu sebabnya `MasterContentRenderTest` ada. Untuk permukaan yang pindah ke JSON,
penjaga itu tidak lagi berlaku.

Karena itu, tiap permukaan yang memakai jalur ini wajib membawa dua penjaga:

- **Tes kontrak JSON** — bentuk dan isi balasan, termasuk penyaringan tenant dan bentuk galatnya.
- **Tes yang menyentuh halaman itu di peramban sungguhan**, atau, selama itu belum ada, satu tes
  render atas keadaan awal SSR-nya plus catatan eksplisit di berkas tesnya bahwa bagian klien tidak
  terjaga.

Tanpa keduanya, permukaan itu berjalan tanpa jaring — dan proyek ini sudah dua kali membuktikan
kelas kegagalan itu nyata.

**Penerapan pada yang sudah ada.** Menjawab soal Practice tetap fragment **batang soal utuh**:
`ExamSessionController.saveAnswer` (`PUT /siswa/sesi/{id}/jawaban/{qid}`) membalas
`siswa/practice :: soal`, karena jawaban membuka `explanationHtml` yang dirender `th:utext` plus
render ulang KaTeX — jatuh ke sisi fragment pertanyaan di atas, apa adanya.

Auto-save Quiz juga fragment, tapi **bukan batang soal utuh**: menyimpan jawaban Quiz tidak
mengubah soalnya, jadi endpoint yang sama membalas `siswa/fragmen-simpan :: tersimpan` — satu
baris status untuk `#status-simpan` plus satu tombol peta soal yang berubah sebagai swap
out-of-band. Sebelumnya balasannya batang soal, seluruh opsi, textarea yang sedang diketik, dan
seluruh peta soal, dan server membaca ulang delapan query untuk merender yang tidak berubah itu.
Bentuk yang lebih kecil ini masih HTML yang mendarat di DOM, jadi tetap fragment, bukan JSON;
yang berubah hanya seberapa banyak DOM yang disentuh.

Hitung mundur Timer **tidak dipindah** oleh ADR ini. `SessionTimeController.remaining`
(`GET /siswa/sesi/{id}/waktu`) membalas `siswa/fragmen-waktu` — sebuah `<span>` berisi `sisaDetik`
(angka) dan `berjalan` (boolean), tidak ada `_html` apa pun di dalamnya. Di bawah pembeda tajam di
atas, itu jatuh ke sisi JSON, dan karena itu **melanggar TC-14 yang baru sejak ADR ini disahkan** —
bukan sesuatu yang boleh diam-diam dibiarkan. Dicatat di sini sebagai **utang migrasi bernama**,
sengaja TIDAK dikerjakan sekarang: memindahkannya butuh TC-14a-nya sendiri (tes kontrak JSON, dan
salah satu dari tes peramban sungguhan atau tes render SSR plus catatan eksplisit), dan itu di
luar cakupan perubahan Bank Soal yang melahirkan ADR ini.

Rumusan pertama ADR ini menyebut ketiganya wajib fragment, lalu revisi keduanya salah arah lain:
menyebut auto-save Quiz dan Timer sama-sama pindah ke JSON tanpa memeriksa apa yang sebenarnya
dikembalikan `ExamSessionController.saveAnswer` (fragmen soal berisi HTML, bukan status polos).
Keduanya ditulis tanpa memeriksa balasan endpoint yang sebenarnya — koreksi ini yang sudah, dua
kali.

## Alternatif yang ditolak

**Menyimpan pilihan di pembungkus yang tidak ikut ditukar HTMX.** Bekerja, tidak butuh perubahan
aturan, dan sempat diusulkan. Ditolak pemilik produk: ia menyelesaikan panel ini, tapi setiap
permukaan berikutnya dengan keadaan klien yang kaya akan menghadapi persoalan yang sama, dan
aturannya lebih baik diubah sekali dengan sadar daripada diakali berulang kali.

**Menyimpan pilihan di sesi server.** Menjaga satu jalur render, tapi menaruh keadaan sementara
milik satu tab peramban ke dalam sesi yang dibagi seluruh tab. Dua tab yang membuka panel berbeda
akan saling menimpa pilihan.
