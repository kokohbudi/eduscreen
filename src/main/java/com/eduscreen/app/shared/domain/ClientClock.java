package com.eduscreen.app.shared.domain;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Sumber waktu tunggal aplikasi.
 *
 * <p>Dua aturan yang ditegakkan kelas ini:
 *
 * <ul>
 *   <li><b>Waktu adalah milik server</b> (BR-T03). Sisa waktu pengerjaan, batas akhir, dan
 *       finalisasi sesi selalu dihitung dari sini — tidak pernah dari nilai yang dikirim
 *       klien.</li>
 *   <li><b>Simpan UTC, tampilkan zona Client</b> (BR-T01, BR-T02). Indonesia punya tiga zona;
 *       "Minggu 23:59" harus berarti hal yang sama bagi Guru di Makassar dan Siswa di
 *       Jayapura, yaitu waktu Client-nya.</li>
 * </ul>
 *
 * <p>Konversi ke zona Client hanya terjadi di lapisan render. Tidak ada perbandingan waktu
 * yang boleh dilakukan dalam zona lokal.
 */
@Component
public class ClientClock {

    private final Clock clock;

    public ClientClock() {
        this(Clock.systemUTC());
    }

    /** Untuk tes yang perlu memajukan atau membekukan waktu. */
    public ClientClock(Clock clock) {
        this.clock = clock;
    }

    /** Waktu server sekarang, selalu UTC. */
    public OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** Menyajikan waktu UTC dalam zona Client, khusus untuk render. */
    public ZonedDateTime inClientZone(OffsetDateTime utc, ZoneId clientZone) {
        return utc.atZoneSameInstant(clientZone);
    }
}
