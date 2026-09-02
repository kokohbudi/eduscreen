package com.eduscreen.app.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menegakkan TC-39: nama tes merujuk pengenal aturan, sehingga cakupan kriteria penerimaan bisa
 * diperiksa mesin, bukan ditaksir manusia.
 *
 * <p>Dua hal yang diperiksa, dan keduanya sengaja berbeda sifatnya:
 *
 * <ul>
 *   <li><b>Setiap {@code @DisplayName} menyebut sebuah pengenal</b> — {@code AC-*} untuk kriteria
 *       penerimaan, {@code TC-*} untuk aturan konstitusi, {@code BR-*} untuk aturan bisnis. Tes
 *       yang tidak bisa menyebut aturan mana yang ia jaga biasanya tidak menjaga aturan apa pun.</li>
 *   <li><b>Setiap {@code AC-*} yang disebut benar-benar ada</b> di {@code business-rules.md}.
 *       Ini yang menangkap pergeseran diam-diam: pengenal salah ketik membuat tes terlihat
 *       tercakup padahal ia menunjuk kriteria yang tidak pernah ada.</li>
 * </ul>
 *
 * <p>Yang sengaja <b>tidak</b> diperiksa: kelengkapan cakupan seluruh kriteria yang ada. Memaksanya
 * hijau akan mendorong orang menempelkan pengenal ke tes yang tidak membuktikan apa-apa. Daftar
 * kriteria yang belum tersentuh dicetak sebagai laporan, bukan sebagai kegagalan.
 */
class AcceptanceCriteriaCoverageTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path BUSINESS_RULES =
            Path.of("specs/001-student-exercise-portal/business-rules.md");

    private static final Pattern DISPLAY_NAME = Pattern.compile("@DisplayName\\(\"([^\"]+)\"");
    private static final Pattern RULE_ID = Pattern.compile("\\b(AC-[A-Z]\\d{2}|TC-\\d{2}|BR-[A-Z]\\d{2})\\b");
    private static final Pattern DECLARED_AC = Pattern.compile("\\*\\*(AC-[A-Z]\\d{2})\\*\\*");

    @Test
    @DisplayName("TC-39: setiap @DisplayName menyebut pengenal AC-*, TC-*, atau BR-*")
    void everyTestNamesTheRuleItGuards() {
        List<String> tanpaPengenal = displayNames().stream()
                .filter(name -> !RULE_ID.matcher(name).find())
                .toList();

        assertTrue(tanpaPengenal.isEmpty(),
                "Tes berikut tidak menyebut pengenal aturan yang dijaganya (TC-39):\n  "
                        + String.join("\n  ", tanpaPengenal));
    }

    @Test
    @DisplayName("TC-39: setiap AC-* yang dirujuk tes benar-benar ada di business-rules.md")
    void referencedCriteriaExist() {
        Set<String> declared = declaredCriteria();
        Set<String> referenced = new TreeSet<>();
        for (String name : displayNames()) {
            Matcher matcher = RULE_ID.matcher(name);
            while (matcher.find()) {
                if (matcher.group(1).startsWith("AC-")) {
                    referenced.add(matcher.group(1));
                }
            }
        }

        Set<String> hantu = new TreeSet<>(referenced);
        hantu.removeAll(declared);
        assertTrue(hantu.isEmpty(),
                "Tes merujuk kriteria yang tidak ada di business-rules.md: " + hantu);

        Set<String> belumTersentuh = new TreeSet<>(declared);
        belumTersentuh.removeAll(referenced);
        // Laporan, bukan kegagalan: memaksanya kosong akan mendorong orang menempelkan pengenal
        // ke tes yang tidak membuktikan apa pun.
        System.out.println("[TC-39] kriteria tercakup: " + referenced.size() + "/" + declared.size());
        if (!belumTersentuh.isEmpty()) {
            System.out.println("[TC-39] belum tersentuh tes: " + belumTersentuh);
        }
    }

    private Set<String> declaredCriteria() {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = DECLARED_AC.matcher(read(BUSINESS_RULES));
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        assertTrue(ids.size() > 40,
                "business-rules.md tidak terbaca sebagaimana mestinya; hanya " + ids.size()
                        + " kriteria ditemukan");
        return ids;
    }

    private List<String> displayNames() {
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    // Kelas ini sendiri dikecualikan: ia menguji aturan penamaan, bukan kriteria.
                    .filter(path -> !path.getFileName().toString().equals(
                            "AcceptanceCriteriaCoverageTest.java"))
                    .flatMap(path -> {
                        Matcher matcher = DISPLAY_NAME.matcher(read(path));
                        return matcher.results().map(result -> result.group(1));
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Sumber tes tidak terbaca", e);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Tidak bisa membaca " + path, e);
        }
    }
}
