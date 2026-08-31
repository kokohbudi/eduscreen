package com.eduscreen.app.modules.notification.adapter.out;

import com.eduscreen.app.modules.notification.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Menulis tautan ke log alih-alih mengirim email. Aktif di {@code local} dan {@code demo}.
 *
 * <p>Untuk {@code demo} ini bukan sekadar kenyamanan melainkan kewajiban (TC-49): mengirim
 * tautan reset password sungguhan dari sistem yang autentikasinya palsu adalah cara tercepat
 * mengubah peragaan menjadi insiden.
 *
 * <p>Alamat email <b>tidak</b> ikut ditulis (TC-44). Yang dicetak hanya nama tampilan dan
 * tautannya — cukup untuk menyelesaikan alur di pengembangan, tanpa menumpuk data pribadi di
 * berkas log.
 */
@Component
@Profile({"local", "demo"})
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void sendInvitation(String recipientEmail, String fullName, String activationUrl) {
        log.info("[EMAIL TIDAK DIKIRIM] Undangan untuk '{}' -> {}", fullName, activationUrl);
    }

    @Override
    public void sendPasswordReset(String recipientEmail, String fullName, String resetUrl) {
        log.info("[EMAIL TIDAK DIKIRIM] Reset password untuk '{}' -> {}", fullName, resetUrl);
    }
}
