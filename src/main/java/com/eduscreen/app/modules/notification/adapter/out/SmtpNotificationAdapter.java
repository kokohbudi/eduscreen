package com.eduscreen.app.modules.notification.adapter.out;

import com.eduscreen.app.modules.notification.port.out.NotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Pengiriman sungguhan. Aktif di seluruh profil selain {@code local} dan {@code demo}. */
@Component
@Profile({"!local & !demo"})
public class SmtpNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationAdapter(JavaMailSender mailSender,
                                   @Value("${eduscreen.mail.from:no-reply@eduscreen.id}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendInvitation(String recipientEmail, String fullName, String activationUrl) {
        send(recipientEmail,
                "Undangan akun Eduscreen",
                "Halo " + fullName + ",\n\n"
                        + "Akun Eduscreen Anda sudah dibuat. Tetapkan password Anda melalui tautan berikut:\n"
                        + activationUrl + "\n\nTautan ini berlaku terbatas.\n");
    }

    @Override
    public void sendPasswordReset(String recipientEmail, String fullName, String resetUrl) {
        send(recipientEmail,
                "Reset password Eduscreen",
                "Halo " + fullName + ",\n\n"
                        + "Gunakan tautan berikut untuk menetapkan password baru:\n"
                        + resetUrl + "\n\n"
                        + "Abaikan pesan ini bila Anda tidak meminta reset password.\n");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
