---
status: accepted
---

# Java 25 sebagai runtime, bukan Java 21

Konstitusi awal mengunci Java 21 LTS. Pada saat implementasi dimulai, mesin pengembangan tidak
memiliki JDK 21 — yang terpasang adalah JDK 26, JDK 25 (Oracle GraalVM), JDK 17, dan JDK 8.
Runtime diubah ke **Java 25 LTS**, dan lini Spring Boot dipatok ke **3.5.x**.

## Alasan

Java 25 sama-sama LTS dan sudah terpasang, sehingga memilihnya menghapus satu langkah pemasangan
dari setiap mesin pengembangan dan setiap runner CI kelak. Alternatifnya — memasang Temurin 21 —
tidak salah, tetapi membeli kematangan yang belum tentu dibutuhkan proyek sebesar ini dengan
biaya penyiapan yang berulang.

Spring Boot dipatok ke lini 3.5.x karena di situlah dukungan JDK 25 berada: dokumentasi Spring
Boot 3.5.16 menyatakan kompatibilitas Java 17 sampai 25, sementara lini 3.3 tidak mencapai
angka itu. Klausa konstitusi sebelumnya, "Spring Boot 3.3+", secara harfiah sudah mengizinkan
3.5 — tetapi dibiarkan kabur ia akan membiarkan seseorang memulai proyek di 3.3 dan menabrak
JDK yang tidak didukung. Karena itu batasnya dinaikkan menjadi eksplisit.

Lompat ke Spring Boot 4.1 dipertimbangkan dan ditolak untuk saat ini: ia mendukung Java 17–26 dan
akan bekerja, tetapi membawa Spring Framework 7 dan Spring Security 7 sekaligus — perubahan besar
yang tidak dibutuhkan hanya untuk menjalankan JDK 25.

## Konsekuensi

- **Ekosistem pustaka di sekitar Java 25 lebih muda daripada di sekitar 21.** Pustaka yang
  melakukan manipulasi bytecode atau memasang agent adalah yang paling mungkin tertinggal.
  Bila ada yang menghalangi, jalan keluarnya adalah memasang JDK 21 dan mengembalikan keputusan
  ini — bukan menambal pustakanya.
- **JDK 26 juga terpasang di mesin pengembangan dan menjadi bawaan `/usr/libexec/java_home`.**
  Java 26 berada di luar dukungan Spring Boot 3.5, sehingga build bisa berjalan di JDK yang salah
  tanpa ada yang menyadarinya sampai muncul galat yang menyesatkan. Karena itu `pom.xml` memasang
  `maven-enforcer-plugin` dengan `requireJavaVersion [25,26)`: batasnya ditegakkan build, bukan
  oleh ingatan orang untuk menyetel `JAVA_HOME`.
- Naik ke Java 26 atau ke Spring Boot 4 kelak adalah keputusan terpisah yang menuntut ADR
  tersendiri.
- Konstitusi naik ke v1.1.0; `plan.md`, `quickstart.md`, dan `tasks.md` diselaraskan pada
  amandemen yang sama.
