package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Client adalah satu-satunya entitas yang tidak menyaring {@code client_id} — ia adalah
 * {@code client_id} itu sendiri. Akses ke repository ini terbatas pada Eduscreen Admin.
 */
public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {
}
