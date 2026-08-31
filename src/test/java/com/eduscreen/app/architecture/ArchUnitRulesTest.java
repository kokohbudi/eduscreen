package com.eduscreen.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Menegakkan Pasal 1 konstitusi: TC-01, TC-02, dan TC-03.
 *
 * <p>Prinsip VII menyatakan aturan yang tidak dijalankan apa pun akan luntur. Kelas ini
 * menjadikan batas arsitektur gagal di CI, bukan bergantung pada tinjauan manusia (TC-40).
 *
 * <p>Catatan jujur untuk Phase 1: modul {@code identity} dan {@code assessment} belum ada,
 * sehingga tidak ada kelas yang bisa diperiksa. Aturan di bawah karena itu <em>dilewati</em>,
 * bukan diluluskan — tes yang lulus tanpa memeriksa apa pun adalah kebohongan kecil yang
 * menumpuk.
 *
 * <p>Pelewatan dikendalikan asumsi, bukan flag konfigurasi, sehingga tiap aturan menyala
 * sendiri begitu paket yang ia periksa benar-benar ada. Tidak ada yang perlu diingat untuk
 * dicabut, dan tidak ada aturan yang lulus tanpa memeriksa apa pun.
 *
 * <p>Penjaganya spesifik per aturan, bukan satu penjaga untuk semuanya: paket
 * {@code modules.assessment} lahir lebih dulu daripada paket {@code service}, sehingga penjaga
 * bersama akan menyalakan aturan yang belum punya bahan untuk diperiksa.
 */
class ArchUnitRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.eduscreen.app");

    private static boolean anyClassInPackageContaining(String fragment) {
        for (JavaClass clazz : CLASSES) {
            if (clazz.getPackageName().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static void requirePackage(String fragment) {
        assumeTrue(anyClassInPackageContaining(fragment),
                "Paket '" + fragment + "' belum ada; aturan menyala sendiri begitu ia lahir");
    }

    @Test
    @DisplayName("TC-03: assessment tidak boleh menyentuh identity.adapter")
    void assessmentMustNotReachIntoIdentityAdapters() {
        requirePackage("modules.assessment");
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.assessment..")
                .should().accessClassesThat().resideInAPackage("..modules.identity.adapter..")
                .because("TC-03: assessment hanya boleh menyentuh identity.port.in, "
                        + "sehingga migrasi ke Keycloak tidak menyentuh inti bisnis");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("TC-02: layer service tidak boleh menyebut nama vendor")
    void serviceLayerMustNotNameVendors() {
        requirePackage(".service");
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .haveSimpleNameStartingWith("Keycloak")
                .orShould().dependOnClassesThat().haveSimpleNameStartingWith("Smtp")
                .orShould().dependOnClassesThat().haveSimpleNameStartingWith("S3")
                .because("TC-02: nama vendor hidup di adapter, tidak pernah di service");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("TC-01: assessment berlapis lurus, tanpa port dan adapter")
    void assessmentMustNotIntroducePortsOrAdapters() {
        requirePackage("modules.assessment");
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.assessment..")
                .should().resideInAnyPackage("..modules.assessment.port..", "..modules.assessment.adapter..")
                .because("TC-01: hexagonal adalah biaya untuk batas eksternal, "
                        + "bukan gaya penulisan default di inti bisnis");
        rule.check(CLASSES);
    }
}
