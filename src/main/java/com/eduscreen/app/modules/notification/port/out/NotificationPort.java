package com.eduscreen.app.modules.notification.port.out;

/**
 * Pengiriman email transaksional: undangan akun dan reset password (BR-U04).
 *
 * <p>Hanya email transaksional yang masuk lingkup v1. Pemberitahuan tugas baru dan pengingat
 * deadline berada di luar lingkup — tanpa pemisahan itu, "notifikasi di luar v1" akan
 * bertabrakan dengan alur akun yang menggantungkan pemulihan password pada email.
 */
public interface NotificationPort {

    void sendInvitation(String recipientEmail, String fullName, String activationUrl);

    void sendPasswordReset(String recipientEmail, String fullName, String resetUrl);
}
