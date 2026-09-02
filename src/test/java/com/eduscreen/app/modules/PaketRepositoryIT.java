package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectRepository;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.repository.TopicRepository;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaketRepositoryIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    PaketRepository pakets;
    @Autowired
    SubjectRepository subjects;
    @Autowired
    TopicRepository topics;

    @Test
    @DisplayName("TC-36: Paket milik Client lain tidak terbaca, bukan galat")
    void paketIsIsolatedPerClient() {
        ClientEntity a = data.client("SD Isolasi A");
        ClientEntity b = data.client("SD Isolasi B");
        SubjectEntity subject = subjects.save(SubjectEntity.global("Matematika Kelas 4 Isolasi"));
        PaketEntity milikA = pakets.save(
                PaketEntity.forClient(a.getId(), subject.getId(), "Latihan A", null));

        assertThat(pakets.findByIdAndClientId(milikA.getId(), b.getId())).isEmpty();
        assertThat(pakets.findByIdAndClientId(milikA.getId(), a.getId())).isPresent();
    }

    @Test
    @DisplayName("AC-B05: hanya Paket master terbit yang muncul untuk katalog")
    void onlyPublishedMasterIsVisibleToCatalog() {
        SubjectEntity subject = subjects.save(SubjectEntity.global("Fisika Kelas 8 Katalog"));
        PaketEntity draf = pakets.save(PaketEntity.master(subject.getId(), "Draf", null));
        PaketEntity terbit = PaketEntity.master(subject.getId(), "Terbit", null);
        terbit.publish(OffsetDateTime.now());
        pakets.save(terbit);

        assertThat(pakets.findMasterPublished(subject.getId()))
                .extracting(PaketEntity::getTitle)
                .contains("Terbit")
                .doesNotContain(draf.getTitle());
    }

    @Test
    @DisplayName("AC-B01: Topic hidup di dalam satu Paket dan terurut menurut position")
    void topicBelongsToOnePaket() {
        SubjectEntity subject = subjects.save(SubjectEntity.global("Biologi Kelas 7 Urut"));
        PaketEntity paket = pakets.save(PaketEntity.master(subject.getId(), "Latihan Bab 1", null));
        topics.save(TopicEntity.of(paket.getId(), "Kedua", 1));
        topics.save(TopicEntity.of(paket.getId(), "Pertama", 0));

        assertThat(topics.findByPaketIdOrderByPositionAsc(paket.getId()))
                .extracting(TopicEntity::getTitle)
                .containsExactly("Pertama", "Kedua");
    }
}
