package com.eduscreen.app.modules.assessment.controller;

import java.util.List;
import java.util.UUID;

/**
 * Bentuk balasan JSON panel pinjam soal (ADR-0019), dipakai kedua sisi —
 * {@link BankSoalController#panelPinjamData} dan
 * {@link MasterContentController#panelPinjamData} — supaya kesepadanan dua sisi juga berlaku di
 * bentuk datanya, tidak cuma di jalur Java yang merakitnya.
 *
 * <p>Muat pertama {@code /bank-soal/paket/{id}} tetap SSR (TC-13); balasan ini melayani
 * pembaruan berikutnya yang dipicu tindakan pengguna — mengubah penyaring, mengetik kata kunci,
 * berpindah halaman — dan dirender Alpine di klien (TC-14). {@code subjects}/{@code pakets}/
 * {@code topics} sudah disaring dan disempitkan di server (Subject → Paket → Topic, aturan 7):
 * klien tidak pernah menawarkan Paket yang pasti nol hasil untuk Subject yang sedang dipilih.
 */
public record PinjamPanelData(List<Opsi> subjects, List<Opsi> pakets, List<Opsi> topics, HasilSoal soal) {

    /** Satu entri dropdown/pil: id sebagai nilai, label sebagai teks tampilan. */
    public record Opsi(UUID id, String label) {
    }

    /**
     * Satu baris tabel Soal. Label Paket/Subject/Topic sudah digabung di server (join yang sama
     * dengan yang dulu dilakukan Thymeleaf lewat {@code paketById}/{@code namaSubject}/
     * {@code judulTopic}) — klien murni menampilkan, tidak menyimpulkan apa pun sendiri.
     */
    public record SoalRow(UUID id, String isi, String tipe, UUID paketId, String paketTitle,
                          UUID subjectId, String subjectName, UUID topicId, String topicTitle) {
    }

    /** Satu halaman hasil pencarian, bentuknya sejajar {@code org.springframework.data.domain.Page}. */
    public record HasilSoal(List<SoalRow> content, int page, int totalPages, long totalElements,
                            boolean hasPrevious, boolean hasNext) {
    }
}
