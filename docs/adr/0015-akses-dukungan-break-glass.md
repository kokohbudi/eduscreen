---
status: accepted
---

# Akses dukungan Eduscreen: baca-saja, berizin Client, berbatas waktu, teraudit

BR-P04 menyatakan isolasi tenant absolut: tidak ada peran yang bisa membaca data Client lain, Eduscreen Admin sekalipun. ADR ini menetapkan satu-satunya pengecualiannya. Client Admin dapat menyalakan **akses dukungan**: jendela baca-saja bagi Eduscreen Admin atas data Client tersebut, padam otomatis setelah beberapa jam, dengan setiap pembacaan tercatat di audit.

## Alasan

Isolasi absolut adalah janji privasi yang kuat dan beban dukungan yang berat. Ketika sebuah sekolah menelepon karena soalnya tidak tampil dengan benar, isolasi absolut berarti tidak ada seorang pun di pihak Eduscreen yang bisa melihat apa pun.

Yang berbahaya bukan memilih salah satu dari kedua sisi, melainkan membiarkannya tidak diputuskan. Dalam keadaan itu, yang benar-benar terjadi adalah seseorang membuka koneksi ke database produksi untuk "melihat sebentar" — akses penuh tulis, tanpa persetujuan Client, tanpa jejak apa pun. Jalur resmi yang sempit dan tercatat lebih aman daripada larangan mutlak yang dilanggar diam-diam.

## Konsekuensi

- BR-P04 tidak lagi mutlak. `spec.md` mencatat pengecualian ini sebagai BR-P05 agar matriks izin dan teksnya tidak saling bertentangan.
- Aksesnya **baca-saja**. Eduscreen Admin tidak pernah bisa mengubah data Client, bahkan untuk memperbaiki masalah yang dilaporkan; perbaikan dipandu ke Client Admin.
- Client Admin yang memegang saklarnya, bukan Eduscreen. Ini yang menjadikannya persetujuan, bukan pemberitahuan.
- Jejak audit harus bisa ditunjukkan kepada Client bila diminta. Audit yang tidak pernah bisa dibaca pemiliknya tidak memenuhi tujuan keputusan ini.
- Konsekuensi turunannya (TC-46): koneksi langsung ke database produksi untuk keperluan dukungan dilarang. Jalur resmi ini ada supaya jalur tidak resmi tidak punya alasan untuk dipakai.
