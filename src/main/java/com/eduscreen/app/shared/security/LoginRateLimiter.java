package com.eduscreen.app.shared.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Membatasi laju percobaan login per akun <b>dan</b> per alamat IP (TC-33).
 *
 * <p>Keduanya dibutuhkan, dan alasannya berbeda. Batas per IP menghentikan satu penyerang
 * menebak banyak password untuk satu akun. Batas per akun menghentikan pola yang justru paling
 * cocok dengan akun sekolah: satu password seragam dicoba ke seluruh daftar siswa, satu
 * percobaan per akun, dari alamat yang berganti-ganti.
 *
 * <p>Penghitung tinggal di memori. Itu sah selama topologi masih satu instance (TC-42);
 * berpindah ke topologi mendatar mengharuskan penghitung ini ikut pindah.
 */
@Component
public class LoginRateLimiter {

    private static final int LOCK_THRESHOLD = 8;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Attempts> byKey = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String ipAddress) {
        Instant now = Instant.now();
        return isBlocked(accountKey(email), now) || isBlocked(ipKey(ipAddress), now);
    }

    public void recordFailure(String email, String ipAddress) {
        Instant now = Instant.now();
        record(accountKey(email), now);
        record(ipKey(ipAddress), now);
    }

    public void recordSuccess(String email, String ipAddress) {
        byKey.remove(accountKey(email));
        byKey.remove(ipKey(ipAddress));
    }

    /**
     * Penundaan yang menaik seiring kegagalan. Membuat penebakan beruntun mahal jauh sebelum
     * ambang penguncian tercapai, tanpa mengganggu orang yang sekadar salah ketik sekali.
     */
    public Duration penaltyDelay(String email, String ipAddress) {
        int failures = Math.max(count(accountKey(email)), count(ipKey(ipAddress)));
        if (failures <= 2) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.min(2_000L, 250L * (1L << Math.min(failures - 3, 3))));
    }

    private boolean isBlocked(String key, Instant now) {
        Attempts attempts = byKey.get(key);
        if (attempts == null) {
            return false;
        }
        if (attempts.expiredAt(now)) {
            byKey.remove(key);
            return false;
        }
        return attempts.count >= LOCK_THRESHOLD
                && Duration.between(attempts.lastAttempt, now).compareTo(LOCK_DURATION) < 0;
    }

    private void record(String key, Instant now) {
        byKey.compute(key, (unused, existing) -> {
            if (existing == null || existing.expiredAt(now)) {
                return new Attempts(1, now);
            }
            return new Attempts(existing.count + 1, now);
        });
    }

    private int count(String key) {
        Attempts attempts = byKey.get(key);
        return attempts == null || attempts.expiredAt(Instant.now()) ? 0 : attempts.count;
    }

    private static String accountKey(String email) {
        return "account:" + (email == null ? "" : email.toLowerCase());
    }

    private static String ipKey(String ipAddress) {
        return "ip:" + (ipAddress == null ? "" : ipAddress);
    }

    private record Attempts(int count, Instant lastAttempt) {
        boolean expiredAt(Instant now) {
            return Duration.between(lastAttempt, now).compareTo(WINDOW) > 0;
        }
    }
}
