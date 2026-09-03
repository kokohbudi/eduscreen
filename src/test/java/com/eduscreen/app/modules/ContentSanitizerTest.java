package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.service.ContentSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Murni unit, tanpa Spring: ContentSanitizer tidak punya ketergantungan apa pun. */
class ContentSanitizerTest {

    private final ContentSanitizer sanitizer = new ContentSanitizer();

    @Test
    @DisplayName("TC-25: teks polos memuat karakter sebenarnya, bukan entitas — termasuk yang dikodekan sanitizer untuk keamanan atribut (+ = @ `)")
    void teksPolosMendekodeSeluruhEntitas() {
        assertThat(sanitizer.toPlainText("<p>Berapa 2 + 2?</p>")).isEqualTo("Berapa 2 + 2?");
        assertThat(sanitizer.toPlainText("<p>x = y @ `z`</p>")).isEqualTo("x = y @ `z`");
        assertThat(sanitizer.toPlainText("<p>5 &lt; 7 &amp;&amp; \"a\" 'b'</p>")).isEqualTo("5 < 7 && \"a\" 'b'");
        assertThat(sanitizer.toPlainText("<p>al<b>jab</b>ar&nbsp;&nbsp;dasar</p>")).isEqualTo("aljabar dasar");
        assertThat(sanitizer.toPlainText("<script>x()</script><p>aman</p>")).isEqualTo("aman");
    }
}
