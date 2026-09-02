package com.eduscreen.app.config;

import com.eduscreen.app.shared.security.EduscreenAuthenticationProvider;
import com.eduscreen.app.shared.security.HtmxAwareAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Rangkaian filter keamanan.
 *
 * <p>Sesi disimpan di server dengan cookie {@code HttpOnly; Secure; SameSite=Lax} (TC-29);
 * atribut cookie diatur di {@code application.yml} agar profil {@code local} bisa mematikan
 * {@code Secure} untuk HTTP tanpa menyentuh kode.
 *
 * <p>Saat Keycloak masuk, yang berubah hanya langkah autentikasi awal menjadi OIDC
 * authorization code; mekanisme sesinya tetap.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    EduscreenAuthenticationProvider authenticationProvider,
                                    HtmxAwareAuthenticationEntryPoint entryPoint) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // HTMX mengirim token lewat header; nilainya harus mentah, bukan ter-encode.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/undangan/**", "/lupa-password", "/reset/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/vendor/**", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Gambar soal TIDAK ada di daftar ini: ia dilayani endpoint berotorisasi yang
                // memeriksa client_id dan peran (TC-26). Soal ujian tidak boleh bocor lewat URL
                // berkas.
                //
                // Pembatasan peran di bawah adalah pagar kasar per prefiks jalur. Ia tidak
                // menggantikan pemeriksaan kepemilikan di service: pagar ini hanya tahu peran,
                // sementara yang menentukan sebuah objek boleh disentuh adalah clientId dan
                // keanggotaan Ruangan, dan itu masuk ke klausa query (TC-08, TC-09).
                .requestMatchers("/eduscreen", "/eduscreen/**").hasRole("EDUSCREEN_ADMIN")
                .requestMatchers("/admin", "/admin/**", "/katalog", "/katalog/**")
                    .hasRole("CLIENT_ADMIN")
                .requestMatchers("/guru", "/guru/**").hasRole("GURU")
                .requestMatchers("/siswa", "/siswa/**").hasRole("SISWA")
                // Bank soal dan perakitan Exercise dipakai Client Admin maupun Guru; Question
                // milik Client terlihat oleh seluruh Guru di Client itu, tanpa konten privat per
                // Guru (BR-P02).
                // Eduscreen Admin ikut karena Question master pun boleh bergambar (FR-063). Yang
                // menentukan siapa boleh MEMBACA sebuah gambar tetap client_id di dalam query,
                // bukan peran di pagar ini (TC-26, TC-08).
                .requestMatchers(HttpMethod.POST, "/gambar")
                    .hasAnyRole("CLIENT_ADMIN", "GURU", "EDUSCREEN_ADMIN")
                .requestMatchers("/bank-soal", "/bank-soal/**", "/soal/**", "/exercise/**",
                        "/subject/**")
                    .hasAnyRole("CLIENT_ADMIN", "GURU")
                // Membaca gambar terbuka untuk setiap peran — Siswa perlu melihat gambar di
                // dalam soal yang sedang dikerjakannya. Yang menyaring adalah client_id di
                // dalam query, bukan peran (TC-26, TC-08).
                .requestMatchers(HttpMethod.GET, "/gambar/**").authenticated()
                .anyRequest().authenticated())
            // Halaman login dibuat sendiri (AuthController). Halaman bawaan Spring berhenti
            // didaftarkan begitu authenticationEntryPoint kustom dipasang untuk TC-30, dan
            // mengakali heuristik itu lebih rapuh daripada menulis satu templat.
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            // TC-30 ditegakkan di sini, bukan di @ControllerAdvice: permintaan tak
            // terautentikasi ditolak sebelum controller mana pun dipanggil.
            .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(SessionFixationConfigurer -> SessionFixationConfigurer.newSession()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler));

        return http.build();
    }

}
