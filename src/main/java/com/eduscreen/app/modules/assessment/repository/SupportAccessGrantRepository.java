package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportAccessGrantRepository extends JpaRepository<SupportAccessGrantEntity, UUID> {

    Optional<SupportAccessGrantEntity> findFirstByClientIdOrderByGrantedAtDesc(UUID clientId);

    List<SupportAccessGrantEntity> findByClientIdOrderByGrantedAtDesc(UUID clientId);
}
