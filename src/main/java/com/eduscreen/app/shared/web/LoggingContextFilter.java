package com.eduscreen.app.shared.web;

import com.eduscreen.app.shared.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Menaruh konteks penelusuran ke MDC untuk setiap permintaan (TC-44).
 *
 * <p>Yang dibawa: {@code clientId}, {@code userId}, dan {@code requestId}. Ketiganya cukup
 * untuk menelusuri satu kejadian sampai ke tenant dan penggunanya.
 *
 * <p>Yang <b>tidak pernah</b> dibawa: alamat email, isi jawaban Siswa, isi soal, dan password.
 * Pengenal buram sudah cukup untuk menelusuri masalah; data pribadi yang masuk log akan tinggal
 * di sana jauh lebih lama daripada kegunaannya.
 *
 * <p>Namanya sengaja bukan {@code RequestContextFilter}: Spring Boot sudah mendaftarkan bean
 * dengan nama itu di {@code WebMvcAutoConfiguration}, dan menabraknya menggagalkan start.
 */
@Component
@Order(1)
public class LoggingContextFilter extends OncePerRequestFilter {

    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(REQUEST_ID, java.util.UUID.randomUUID().toString().substring(0, 8));
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                MDC.put(USER_ID, String.valueOf(principal.userId()));
                if (principal.clientId() != null) {
                    MDC.put(CLIENT_ID, String.valueOf(principal.clientId()));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
            MDC.remove(CLIENT_ID);
        }
    }
}
