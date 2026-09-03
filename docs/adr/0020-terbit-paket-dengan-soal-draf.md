---
status: accepted
---

# Paket master boleh terbit dengan soal draf di dalamnya

Menerbitkan Paket master tidak lagi menuntut seluruh isinya sudah terbit. Gerbangnya menjadi satu:
Paket wajib punya minimal satu Question terbit. Question draf yang tersisa boleh tinggal di dalam
Paket terbit — ia tidak tampil di katalog, tidak ikut tersalin saat diadopsi, dan tetap bisa
diterbitkan kapan saja.

Saat Eduscreen Admin menekan Terbitkan pada Paket yang masih menyimpan draf, layar menawarkan dua
jalan alih-alih menolak: **terbitkan semua** (draf ikut terbit) atau **terbitkan yang siap saja**
(draf tetap draf). Pilihan kedua hilang selama belum ada satu pun Question terbit. Halaman isi
Paket juga mendapat satu tindakan yang menerbitkan seluruh draf sekaligus.

## Alasan

Aturan lama (AC-B12: Paket ditolak terbit selama memuat Question draf) benar secara isi tapi tidak
punya jalan keluar yang layak. Paket berisi 200 soal tersandera oleh satu soal yang belum sempat
ditinjau, dan satu-satunya cara maju adalah menekan tombol Terbitkan dua ratus kali — tidak ada
tindakan massal di mana pun. Penolakannya pun tidak terlihat: balasannya HTTP 400 yang HTMX buang
diam-diam, sehingga tombolnya tampak rusak (BR-O05).

Alasan asli gerbang itu tetap sah dan tetap dijaga, hanya pindah tempat:

- **Katalog tidak boleh memuat Paket yang isinya sebagian tersembunyi.** Dijaga di
  `ContentAdoptionService.adoptPakets`, yang sekarang menyalin Question terbit saja
  (`findPublishedByTopicIdOrderByPositionAsc`). Sebelumnya penyaringan di sana ditolak dengan
  alasan akan menghasilkan salinan diam-diam tidak lengkap — itu benar selama draf di Paket terbit
  adalah keadaan mustahil. Sejak keadaan itu jadi pilihan sadar Eduscreen Admin, salinan tanpa draf
  justru satu-satunya hasil yang jujur: yang disalin adalah apa yang ditawarkan katalog.
- **Paket kosong tidak boleh masuk katalog.** Dipertajam jadi minimal satu Question *terbit*
  (AC-B16), bukan sekadar minimal satu Question. Paket yang seluruh isinya draf ditolak seperti
  Paket kosong, karena hasil adopsinya sama: nol soal.
- **Isi Paket terbit tidak boleh turun.** AC-B17 tidak berubah — menarik atau menghapus Question
  dari Paket yang sedang terbit tetap ditolak.

Alternatif yang ditolak: menerbitkan seluruh draf secara otomatis saat Paket diterbitkan. Itu
menghapus tinjauan per-soal tanpa pernah menanyakannya, dan menerbitkan soal setengah jadi adalah
kesalahan yang tidak bisa ditarik kembali dari sekolah yang sudah terlanjur mengadopsi.

## Konsekuensi

- Jumlah soal di ruang kerja master (kolom Soal) menghitung seluruh isi Paket, termasuk draf,
  sementara yang diadopsi sekolah hanya yang terbit. Ringkasan adopsi menyebut jumlah yang
  benar-benar disalin, jadi angkanya tidak pernah berbohong ke sekolah — tapi kolom Soal di ruang
  kerja bukan angka yang bisa dibaca sebagai "yang akan diterima sekolah".
- `MasterPublishingService.publishPaket(id)` tanpa argumen kedua berarti "jangan sentuh draf".
- FR-069 (Exercise master ditolak terbit selama memuat Question draf) sudah digantikan ADR-0018
  bersama seluruh Exercise master; aturan penggantinya di Paket adalah AC-B12 yang diubah di sini.
