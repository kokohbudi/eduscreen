package com.eduscreen.app.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Pembangkit UUID versi 7 (RFC 9562): 48 bit stempel waktu milidetik Unix, lalu bit acak.
 *
 * <p>Dipakai sebagai primary key seluruh entitas yang pengenalnya muncul di URL (ADR-0009).
 * Dua sifat yang dibutuhkan sekaligus:
 *
 * <ul>
 *   <li><b>Tidak tertebak</b> — lapis pertama pertahanan anti-IDOR. {@code /session/102} bisa
 *       dijelajahi dengan menambah satu; UUID tidak.</li>
 *   <li><b>Terurut waktu</b> — {@code session_answer} adalah tabel dengan tulis paling deras di
 *       sistem ini, dan kunci acak v4 memecah lokalitas halaman index B-tree tepat pada beban
 *       itu. v7 menulis di ujung index seperti bilangan berurut.</li>
 * </ul>
 *
 * <p>Pengenal dibuat aplikasi, bukan database, sehingga entitas punya identitas sebelum
 * disimpan.
 *
 * <p>Dalam milidetik yang sama, 12 bit {@code rand_a} dipakai sebagai pencacah agar urutan
 * pembuatan tetap monoton — tanpa itu, ribuan penyisipan dalam milidetik yang sama kembali
 * teracak dan manfaat lokalitasnya hilang.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_COUNTER = 0x0FFF;

    private static long lastTimestampMillis = -1L;
    private static int counter = 0;

    private UuidV7() {
    }

    public static UUID randomUuid() {
        final long timestamp;
        final int sequence;

        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now == lastTimestampMillis) {
                if (counter >= MAX_COUNTER) {
                    // Pencacah penuh dalam satu milidetik: tunggu milidetik berikutnya daripada
                    // mengulang pengenal.
                    now = waitForNextMillis(lastTimestampMillis);
                    lastTimestampMillis = now;
                    counter = RANDOM.nextInt(MAX_COUNTER >> 1);
                } else {
                    counter++;
                }
            } else {
                lastTimestampMillis = now;
                counter = RANDOM.nextInt(MAX_COUNTER >> 1);
            }
            timestamp = now;
            sequence = counter;
        }

        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16   // 48 bit unix_ts_ms
                | (0x7L << 12)                            // versi 7
                | (sequence & 0x0FFFL);                   // 12 bit rand_a sebagai pencacah

        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) // 62 bit rand_b
                | 0x8000000000000000L;                       // varian RFC 9562 (10xx)

        return new UUID(msb, lsb);
    }

    /** Stempel waktu milidetik Unix yang tertanam di UUID v7. */
    public static long timestampMillis(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    private static long waitForNextMillis(long previous) {
        long now = System.currentTimeMillis();
        while (now <= previous) {
            Thread.onSpinWait();
            now = System.currentTimeMillis();
        }
        return now;
    }
}
