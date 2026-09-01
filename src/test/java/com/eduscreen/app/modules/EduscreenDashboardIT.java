package com.eduscreen.app.modules;

import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.EduscreenDashboardService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard Eduscreen Admin: angka kartu dan antrean pekerjaan yang macet (BR-O05).
 *
 * <p>Database tes dipakai bersama seluruh kelas (lihat {@link PostgresTestBase}), jadi tidak ada
 * assertion yang boleh mengandaikan angka mutlak. Yang diukur selalu SELISIH sebelum dan sesudah.
 */
class EduscreenDashboardIT extends PostgresTestBase {

    @Autowired
    TestData data;
    @Autowired
    EduscreenDashboardService dashboard;

    @Test
    @DisplayName("BR-O05: kartu menghitung Client, Question master, dan paket terbit")
    void kartuMenghitungMilikEduscreen() {
        var sebelum = dashboard.kartu();

        data.client("SD Dashboard1");
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity terbit = data.publishedMasterMcq(topic, "Soal terbit dashboard");
        data.masterMcq(topic, "Soal draf dashboard");
        data.masterExercise("Paket dashboard terbit", List.of(terbit));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
        // Dua Question master lahir: satu terbit, satu draf. Kartu menghitung keduanya.
        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster() + 2);
        // masterExercise() melahirkan paket DRAF, jadi kartu "paket terbit" tidak bergerak.
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
    }

    @Test
    @DisplayName("BR-P04 (FR-080): kartu tidak menghitung satu pun Question atau paket milik Client")
    void kartuTidakMenghitungMilikClient() {
        var sebelum = dashboard.kartu();

        ClientEntity client = data.client("SD Dashboard2");
        TopicEntity topicClient = data.topic(client, "Matematika Kelas 4", "Aljabar");
        QuestionEntity soalClient = data.mcq(client, topicClient, "Soal sekolah dashboard", 4);
        data.exercise(client, data.user(client, UserRole.GURU, "Guru Dashboard"),
                "Paket sekolah dashboard", List.of(soalClient));

        var sesudah = dashboard.kartu();

        assertThat(sesudah.questionMaster()).isEqualTo(sebelum.questionMaster());
        assertThat(sesudah.paketTerbit()).isEqualTo(sebelum.paketTerbit());
        // Client-nya sendiri MEMANG bertambah: entitas client dikelola Eduscreen, isinya tidak.
        assertThat(sesudah.client()).isEqualTo(sebelum.client() + 1);
    }
}
