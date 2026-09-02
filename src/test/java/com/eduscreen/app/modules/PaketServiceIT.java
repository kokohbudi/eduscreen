package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.SubjectEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.TaxonomyService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaketServiceIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    PaketService pakets;
    @Autowired
    TaxonomyService taxonomy;

    @Test
    @DisplayName("AC-B01: Paket baru lahir dengan satu Topic bernama Topik 1")
    void newPaketHasDefaultTopic() {
        ClientEntity client = data.client("SD Topik Bawaan");
        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan Pecahan", null, "Matematika Kelas 4 Bawaan"),
                client.getId(), null);

        List<TopicEntity> topics = pakets.topicsOf(paket.getId());

        assertThat(topics).extracting(TopicEntity::getTitle).containsExactly("Topik 1");
        assertThat(topics.get(0).getPosition()).isZero();
    }

    @Test
    @DisplayName("AC-B01: nama Subject yang sudah ada dipakai ulang, bukan diduplikasi")
    void existingSubjectIsReused() {
        ClientEntity client = data.client("SD Subject Pakai Ulang");
        SubjectEntity subject = taxonomy.createClientSubject(client.getId(), "IPA Kelas 5 Pakai Ulang");

        PaketEntity paket = pakets.create(
                new PaketService.PaketDraft("Latihan A", null, "IPA Kelas 5 Pakai Ulang"),
                client.getId(), null);

        assertThat(paket.getSubjectId()).isEqualTo(subject.getId());
    }
}
