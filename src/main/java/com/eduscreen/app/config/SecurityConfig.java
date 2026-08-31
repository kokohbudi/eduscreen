package com.eduscreen.app.config;

import com.eduscreen.app.shared.security.EduscreenAuthenticationProvider;
import com.eduscreen.app.shared.security.HtmxAwareAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
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
                .anyRequest().authenticated())
            // loginPage() sengaja tidak diatur: halaman login bawaan Spring dipakai sampai
            // AuthController menghadirkan yang sungguhan di T032. Menunjuk ke halaman yang
            // belum ada hanya menghasilkan 404 di jalur paling penting.
            .formLogin(form -> form
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

    @Bean
    SessionFixationProtectionStrategy sessionFixationProtectionStrategy() {
        return new SessionFixationProtectionStrategy();
    }
}
