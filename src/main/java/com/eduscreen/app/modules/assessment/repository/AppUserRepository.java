package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Setiap method yang membaca data milik Client menyebut {@code clientId} secara eksplisit di
 * tanda tangannya (TC-36, ADR-0012).
 *
 * <p>Ini disengaja bertele-tele. Filter otomatis Hibernate akan membuat method di bawah lebih
 * pendek, tetapi memindahkan aturan terpenting sistem ini ke anotasi yang lama-lama berhenti
 * dipikirkan orang. Melewatkan filter soft delete menampilkan konten basi; melewatkan filter
 * {@code client_id} membocorkan bank soal satu sekolah ke sekolah lain, dan kebocoran itu bisa
 * berjalan berbulan-bulan tanpa terdeteksi.
 */
public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    /** Untuk autentikasi: email unik global, sehingga pencarian ini lintas Client. */
    Optional<AppUserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUserEntity> findByIdAndClientId(UUID id, UUID clientId);

    Page<AppUserEntity> findByClientIdAndRole(UUID clientId, UserRole role, Pageable pageable);

    Page<AppUserEntity> findByClientId(UUID clientId, Pageable pageable);

    List<AppUserEntity> findByClientIdAndIdIn(UUID clientId, List<UUID> ids);

    /**
     * Menjaga BR-O10: sebuah Client tidak boleh kehilangan Client Admin terakhirnya yang masih
     * bisa masuk. {@code INVITED} ikut dihitung — undangan yang belum ditebus tetap jalan masuk
     * yang sah, dan menonaktifkannya mengunci sekolah sama rapatnya.
     */
    long countByClientIdAndRoleAndStatusNot(UUID clientId, UserRole role, UserStatus status);
}
