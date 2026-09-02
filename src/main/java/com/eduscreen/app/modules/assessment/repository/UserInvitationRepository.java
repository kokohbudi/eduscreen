package com.eduscreen.app.modules.assessment.repository;

import com.eduscreen.app.modules.assessment.domain.InvitationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInvitationRepository extends JpaRepository<UserInvitationEntity, UUID> {

    Optional<UserInvitationEntity> findByTokenHash(String tokenHash);

    List<UserInvitationEntity> findByUserIdAndPurpose(UUID userId, InvitationPurpose purpose);
}
