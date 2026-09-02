package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** {@code findByIdAndClientId} menegakkan TC-26 di endpoint gambar; TC-36 untuk sisanya. */
public interface StoredImageRepository extends JpaRepository<StoredImageEntity, UUID> {

    Optional<StoredImageEntity> findByIdAndClientId(UUID id, UUID clientId);

    Optional<StoredImageEntity> findById(UUID id);
}
