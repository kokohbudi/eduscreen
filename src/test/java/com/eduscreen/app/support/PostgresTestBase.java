package com.eduscreen.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dasar untuk seluruh tes yang menyentuh database.
 *
 * <p>TC-38 melarang H2: ia berbeda perilaku pada UUID, {@code timestamptz}, dan constraint
 * khas PostgreSQL, sehingga tes hijau di H2 tidak membuktikan apa pun tentang produksi.
 *
 * <p>Container dideklarasikan {@code static} sehingga satu instance dipakai ulang oleh seluruh
 * kelas tes turunan dalam satu JVM, bukan dinyalakan ulang per kelas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("eduscreen")
            .withUsername("eduscreen")
            .withPassword("eduscreen");
}
