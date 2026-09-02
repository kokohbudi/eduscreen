package com.eduscreen.app.modules.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seluruh pembacaan milik Client menyaring {@code clientId} secara eksplisit (TC-36). */
public interface TopicRepository extends JpaRepository<TopicEntity, UUID> {

    List<TopicEntity> findBySubjectIdOrderByNameAsc(UUID subjectId);

    /** Topic yang boleh dilihat satu Client: GLOBAL milik Eduscreen plus miliknya (FR-014). */
    @Query("select t from TopicEntity t where t.subjectId = :subjectId "
            + "and (t.origin = com.eduscreen.app.modules.assessment.domain.ContentOrigin.GLOBAL or t.clientId = :clientId) "
            + "order by t.name asc")
    List<TopicEntity> findVisibleTo(@Param("subjectId") UUID subjectId, @Param("clientId") UUID clientId);

    Optional<TopicEntity> findByIdAndClientId(UUID id, UUID clientId);

    /**
     * Apakah Client ini sudah pernah mengadopsi Topic master itu (FR-076, FR-077).
     *
     * <p>Dibaca dari {@code sourceTopicId} yang ditulis sejak adopsi pertama, bukan dari
     * pencocokan nama: master yang di-rename Eduscreen dan salinan yang dirapikan Guru
     * sama-sama membuat tebakan berdasarkan nama meleset.
     */
    boolean existsByClientIdAndSourceTopicId(UUID clientId, UUID sourceTopicId);
}
