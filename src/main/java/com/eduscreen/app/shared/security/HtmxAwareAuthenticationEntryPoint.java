package com.eduscreen.app.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Titik masuk autentikasi yang membedakan permintaan HTMX dari navigasi biasa (TC-30).
 *
 * <p>Perilaku bawaan Spring Security adalah membalas {@code 302} ke halaman login. Untuk
 * navigasi biasa itu benar. Untuk permintaan HTMX itu merusak: HTMX mengikuti pengalihan lalu
 * <b>menempelkan seluruh halaman login ke dalam slot fragmen</b> — misalnya ke tengah lembar
 * soal yang sedang dikerjakan Siswa, yang lalu melihat formulir login tertanam di antara
 * soal-soalnya.
 *
 * <p>Untuk HTMX, jawabannya {@code 401} berisi {@code HX-Redirect} sehingga peramban benar-benar
 * berpindah halaman.
 *
 * <p>Ini harus hidup di rangkaian filter, bukan di {@code @ControllerAdvice}: permintaan tak
 * terautentikasi ditolak sebelum controller mana pun sempat dipanggil, sehingga penangan galat
 * di lapisan controller tidak akan pernah melihatnya.
 */
@Component
public class HtmxAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String HTMX_REQUEST_HEADER = "HX-Request";
    private static final String HTMX_REDIRECT_HEADER = "HX-Redirect";
    private static final String LOGIN_URL = "/login";

    private final AuthenticationEntryPoint browserEntryPoint = new LoginUrlAuthenticationEntryPoint(LOGIN_URL);

    @Override
    public void commence(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException {

        if ("true".equalsIgnoreCase(request.getHeader(HTMX_REQUEST_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HTMX_REDIRECT_HEADER, LOGIN_URL);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<div class=\"eduscreen-error\" role=\"alert\">Sesi berakhir. Mengalihkan ke halaman masuk.</div>");
            return;
        }

        try {
            browserEntryPoint.commence(request, response, authException);
        } catch (jakarta.servlet.ServletException e) {
            throw new IOException(e);
        }
    }
}
