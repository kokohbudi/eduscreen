package com.eduscreen.app.config;

import com.eduscreen.app.modules.identity.port.out.IdentityProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Menggagalkan start bila jumlah {@link IdentityProviderPort} yang aktif tidak tepat satu
 * (TC-05).
 *
 * <p>Yang dicegah adalah <b>fallback diam-diam</b>. Nol implementasi berarti aplikasi hidup
 * tanpa jalur autentikasi sama sekali; lebih dari satu berarti tidak ada yang tahu pasti mana
 * yang memutuskan siapa boleh masuk. Keduanya harus berbunyi keras saat start, bukan muncul
 * sebagai perilaku aneh berbulan-bulan kemudian.
 */
@Component
public class IdentityProviderStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(IdentityProviderStartupCheck.class);

    private final Map<String, IdentityProviderPort> providers;

    public IdentityProviderStartupCheck(Map<String, IdentityProviderPort> providers) {
        this.providers = providers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyExactlyOneProvider() {
        if (providers.size() != 1) {
            throw new IllegalStateException(
                    "TC-05: dibutuhkan tepat satu IdentityProviderPort aktif, ditemukan "
                            + providers.size() + " " + providers.keySet()
                            + ". Tidak boleh ada fallback diam-diam ke adapter dummy.");
        }
        log.info("IdentityProviderPort aktif: {}", providers.keySet().iterator().next());
    }
}
