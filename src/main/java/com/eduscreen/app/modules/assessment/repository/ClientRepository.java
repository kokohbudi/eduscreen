package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Client adalah satu-satunya entitas yang tidak menyaring {@code client_id} — ia adalah
 * {@code client_id} itu sendiri. Akses ke repository ini terbatas pada Eduscreen Admin.
 */
public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {

    /**
     * Dipakai jalur login untuk menolak pengguna Client yang sedang {@code SUSPENDED} (BR-O09).
     * Sengaja mengembalikan boolean, bukan entity: yang dibutuhkan autentikasi hanya izin masuk,
     * dan memuat seluruh baris Client di setiap percobaan login tidak menambah apa pun.
     */
    boolean existsByIdAndStatus(UUID id, ClientStatus status);
}
