package com.eduscreen.app.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UuidV7 adalah manipulasi bit yang gagal secara diam-diam bila salah, dan ia merupakan lapis
 * pertama pertahanan anti-IDOR (ADR-0009). Karena itu ia diuji meski tidak memetakan ke satu
 * kriteria penerimaan mana pun.
 */
class UuidV7Test {

    @Test
    @DisplayName("Menghasilkan UUID versi 7 dengan varian RFC 9562")
    void producesVersion7WithCorrectVariant() {
        for (int i = 0; i < 1_000; i++) {
            UUID uuid = UuidV7.randomUuid();
            assertThat(uuid.version()).isEqualTo(7);
            assertThat(uuid.variant()).isEqualTo(2); // 10xx
        }
    }

    @Test
    @DisplayName("Stempel waktu tertanam mencerminkan waktu pembuatan")
    void embedsCreationTimestamp() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.randomUuid();
        long after = System.currentTimeMillis();

        assertThat(UuidV7.timestampMillis(uuid)).isBetween(before, after);
    }

    @Test
    @DisplayName("Urutan pembuatan monoton, termasuk dalam milidetik yang sama")
    void isMonotonicWithinSameMillisecond() {
        List<UUID> generated = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            generated.add(UuidV7.randomUuid());
        }

        // Monotonisitas inilah yang membuat penyisipan menulis di ujung index, bukan tersebar.
        for (int i = 1; i < generated.size(); i++) {
            long previous = generated.get(i - 1).getMostSignificantBits();
            long current = generated.get(i).getMostSignificantBits();
            assertThat(current)
                    .as("UUID ke-%d harus tidak lebih kecil daripada pendahulunya", i)
                    .isGreaterThanOrEqualTo(previous);
        }
    }

    @Test
    @DisplayName("Tidak menghasilkan tabrakan di bawah beban paralel")
    void producesNoCollisionsUnderConcurrency() throws Exception {
        int threads = 16;
        int perThread = 5_000;
        Set<UUID> all = java.util.Collections.synchronizedSet(new HashSet<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            all.add(UuidV7.randomUuid());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(all).hasSize(threads * perThread);
    }
}
