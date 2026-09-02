package com.eduscreen.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Dasar untuk seluruh tes yang menyentuh database.
 *
 * <p>TC-38 melarang H2: ia berbeda perilaku pada UUID, {@code timestamptz}, dan constraint
 * khas PostgreSQL, sehingga tes hijau di H2 tidak membuktikan apa pun tentang produksi.
 *
 * <p>Containernya <b>singleton</b>: dinyalakan sekali di blok statis dan sengaja tidak pernah
 * dihentikan. Anotasi {@code @Testcontainers} sengaja tidak dipakai — ia menghentikan container
 * di akhir setiap kelas tes, sementara Spring memakai ulang ApplicationContext yang sama untuk
 * seluruh kelas turunan. Akibatnya kelas kedua dan seterusnya memegang pool koneksi yang masih
 * menunjuk port container yang sudah mati, dan gagal dengan {@code Connection refused} yang
 * terlihat seperti bug aplikasi padahal murni soal daur hidup tes.
 *
 * <p>Yang mematikannya adalah container Ryuk milik Testcontainers saat JVM tes berakhir, jadi
 * tidak ada container yang tertinggal.
 *
 * <p>Konsekuensi yang diterima secara sadar: satu database dipakai bersama seluruh kelas tes,
 * sehingga barisnya menumpuk lintas kelas. {@link TestData} karena itu membuat Client, email,
 * dan konten yang unik di tiap pemanggilan, dan tidak ada tes yang boleh mengandaikan tabelnya
 * kosong.
 *
 * <p>Profil {@code local} ikut aktif supaya tes mengeksekusi rangkaian bean yang sama dengan
 * pengembangan sehari-hari — termasuk adapter identity yang berpagar. Datasource-nya tetap
 * diambil alih {@code @ServiceConnection} dari container, bukan dari application-local.yml.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
public abstract class PostgresTestBase {

    /**
     * {@code protected}, bukan package-private: tes migrasi perlu koordinat containernya untuk
     * membuat database sendiri di dalamnya, terpisah dari database bersama seluruh suite.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("eduscreen")
            .withUsername("eduscreen")
            .withPassword("eduscreen");

    static {
        POSTGRES.start();
    }
}
