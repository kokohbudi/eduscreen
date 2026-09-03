package com.eduscreen.app.modules.assessment.service;

import org.owasp.html.Encoding;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Membersihkan konten kaya dengan allowlist, <b>saat menulis</b> (TC-22, ADR-0011).
 *
 * <p>Yang tersimpan di database adalah hasil sanitasi; templat merendernya apa adanya dengan
 * {@code th:utext}. Menyanitasi saat render ditolak karena menyebar tanggung jawab keamanan ke
 * setiap templat, dan satu templat yang lupa memanggilnya membuka lubang tanpa galat.
 *
 * <p>Markup asli hilang permanen, jadi allowlist dibuat cukup lebar sejak awal: memperlebarnya
 * belakangan tidak mengembalikan apa yang sudah dibuang.
 *
 * <p>Seluruh jalur masuk konten melewati kelas ini: editor maupun impor CSV.
 */
@Component
public class ContentSanitizer {

    /**
     * Gambar hanya boleh menunjuk endpoint gambar milik aplikasi sendiri.
     *
     * <p>Tidak ada protokol URL yang diizinkan sama sekali, sehingga hanya jalur relatif yang
     * lolos. URL absolut ke host lain akan menjadikan soal pembawa pelacak yang melaporkan
     * siapa membuka soal apa dan kapan — dan pada halaman ujian, dari mana.
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "span", "strong", "b", "em", "i", "u", "s",
                    "ul", "ol", "li", "blockquote", "code", "pre",
                    "h1", "h2", "h3", "h4",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "sub", "sup", "img")
            .allowAttributes("src", "alt", "width", "height").onElements("img")
            .allowAttributes("colspan", "rowspan").onElements("td", "th")
            .toFactory();

    private static final PolicyFactory TEXT_ONLY = new HtmlPolicyBuilder().toFactory();

    /**
     * {@code <script>}, {@code <style>}, {@code <iframe>}, {@code <object>}, atribut
     * {@code on*}, dan URL {@code javascript:} semuanya jatuh di luar allowlist di atas.
     * Larangannya struktural, bukan daftar hitam yang harus dijaga tetap lengkap (TC-23).
     */
    public String sanitize(String rawHtml) {
        return rawHtml == null || rawHtml.isBlank() ? "" : POLICY.sanitize(rawHtml);
    }

    /**
     * Teks polos untuk kolom {@code *_text} yang dipakai pencarian (TC-25).
     *
     * <p>Pencarian tidak boleh menyentuh kolom HTML: mencari "img" di sana memunculkan setiap
     * soal bergambar, dan kata yang terpotong markup — {@code al<b>jab</b>ar} — tak akan ketemu.
     */
    public String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // Sanitizer mengembalikan entitas ter-escape; pencarian butuh kata sebenarnya,
        // yaitu "5 < 7", bukan "5 &lt; 7". Dekodernya milik pustaka yang sama yang mengodekan —
        // daftar replace() buatan sendiri sempat dipakai dan melewatkan &#43; (+), &#61; (=),
        // &#64; (@), &#96; (`): kolom teks polos, yang juga tampil apa adanya di panel pinjam,
        // menyimpan "2 &#43; 2" untuk soal "2 + 2".
        String text = Encoding.decodeHtml(TEXT_ONLY.sanitize(html)).replace('\u00a0', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }
}
