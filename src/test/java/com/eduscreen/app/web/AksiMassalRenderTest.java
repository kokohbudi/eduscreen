package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.domain.MemberRole;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.modules.assessment.domain.UserStatus;
import com.eduscreen.app.modules.assessment.repository.AppUserEntity;
import com.eduscreen.app.modules.assessment.repository.AppUserRepository;
import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseItemEntity;
import com.eduscreen.app.modules.assessment.repository.PaketEntity;
import com.eduscreen.app.modules.assessment.repository.PaketRepository;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.RuanganEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.service.ExerciseService;
import com.eduscreen.app.modules.assessment.service.MasterPublishingService;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.RuanganService;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-B26 di luar halaman isi Paket master (yang dijaga {@code MasterContentRenderTest}): setiap
 * daftar yang menawarkan aksi massal merender kotak centangnya ({@code data-massal}) dan
 * endpoint massalnya menghasilkan keadaan yang sama dengan aksi per baris.
 */
@AutoConfigureMockMvc
class AksiMassalRenderTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired AppUserRepository userRepo;
    @Autowired RuanganService ruanganService;
    @Autowired ExerciseService exerciseService;
    @Autowired PaketService paketService;
    @Autowired PaketRepository paketRepo;
    @Autowired MasterPublishingService masterPublishing;

    @Test
    @DisplayName("AC-B26: pengguna — nonaktifkan beberapa sekaligus; undang ulang melewati yang sudah nonaktif")
    void penggunaMassal() throws Exception {
        ClientEntity client = data.client("SD Massal Pengguna");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Massal")));
        AppUserEntity a = data.user(client, UserRole.GURU, "Guru Massal A");
        AppUserEntity b = data.user(client, UserRole.GURU, "Guru Massal B");

        mvc.perform(get("/admin/pengguna").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"userIds\" value=\"" + a.getId() + "\" data-massal")));

        mvc.perform(post("/admin/pengguna/massal")
                        .param("aksi", "nonaktif").param("role", "GURU")
                        .param("userIds", a.getId().toString(), b.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/pengguna?role=GURU"));
        assertThat(userRepo.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(userRepo.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.DEACTIVATED);

        // Undang ulang atas akun nonaktif tidak menggagalkan kiriman.
        mvc.perform(post("/admin/pengguna/massal")
                        .param("aksi", "undang-ulang")
                        .param("userIds", a.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("AC-B26: Ruangan — arsipkan beberapa sekaligus, dan keluarkan beberapa anggota sekaligus")
    void ruanganDanAnggotaMassal() throws Exception {
        ClientEntity client = data.client("SD Massal Ruangan");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Ruangan Massal")));
        RuanganEntity r1 = data.ruangan(client, "Kelas Massal 1");
        RuanganEntity r2 = data.ruangan(client, "Kelas Massal 2");
        AppUserEntity s1 = data.user(client, UserRole.SISWA, "Siswa Massal 1");
        AppUserEntity s2 = data.user(client, UserRole.SISWA, "Siswa Massal 2");
        data.join(r1, s1, MemberRole.SISWA);
        data.join(r1, s2, MemberRole.SISWA);

        mvc.perform(get("/admin/ruangan/{id}", r1.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"userIds\" value=\"" + s1.getId() + "\" data-massal")));
        mvc.perform(post("/admin/ruangan/{id}/anggota/massal", r1.getId())
                        .param("aksi", "keluarkan")
                        .param("userIds", s1.getId().toString(), s2.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/ruangan/" + r1.getId()));
        assertThat(ruanganService.membersOf(r1.getId(), client.getId(), MemberRole.SISWA)).isEmpty();

        mvc.perform(get("/admin/ruangan").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"ruanganIds\" value=\"" + r1.getId() + "\" data-massal")));
        mvc.perform(post("/admin/ruangan/massal")
                        .param("aksi", "arsip")
                        .param("ruanganIds", r1.getId().toString(), r2.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(ruanganService.require(r1.getId(), client.getId()).getStatus().name()).isEqualTo("ARCHIVED");
        assertThat(ruanganService.require(r2.getId(), client.getId()).getStatus().name()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("AC-B26: item Exercise — lepas beberapa item sekaligus, balasannya fragmen daftar tanpa item itu")
    void itemExerciseMassal() throws Exception {
        ClientEntity client = data.client("SD Massal Exercise");
        AppUserEntity guruEntity = data.user(client, UserRole.GURU, "Guru Massal Exercise");
        var guru = user(data.principal(guruEntity));
        PaketEntity paket = data.paket(client, "IPA Massal Exercise", "Paket Massal Exercise");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        QuestionEntity q1 = data.mcq(client, topic, "Item massal satu unik", 4);
        QuestionEntity q2 = data.mcq(client, topic, "Item massal dua unik", 4);
        QuestionEntity q3 = data.mcq(client, topic, "Item massal tiga unik", 4);
        ExerciseEntity exercise = data.exercise(client, guruEntity, "Ulangan Massal", List.of(q1, q2, q3));

        mvc.perform(get("/exercise/{id}", exercise.getId()).with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"itemIds\" value=\"" + q1.getId() + "\" data-massal")))
                .andExpect(content().string(containsString("/item/hapus-terpilih")));

        mvc.perform(post("/exercise/{id}/item/hapus-terpilih", exercise.getId())
                        .param("itemIds", q1.getId().toString(), q2.getId().toString())
                        .with(guru).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Item massal satu unik"))))
                .andExpect(content().string(not(containsString("Item massal dua unik"))))
                .andExpect(content().string(containsString("Item massal tiga unik")));
        assertThat(exerciseService.itemsOf(exercise.getId())).extracting(ExerciseItemEntity::getQuestionId)
                .containsExactly(q3.getId());
    }

    @Test
    @DisplayName("AC-B26: Paket master — tarik beberapa Paket terbit sekaligus, hanya yang terbit bisa dicentang")
    void paketMasterMassal() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        PaketEntity p1 = data.masterPaket("Kimia Massal Paket", "Paket massal terbit satu");
        PaketEntity p2 = data.masterPaket("Kimia Massal Paket", "Paket massal terbit dua");
        PaketEntity draf = data.masterPaket("Kimia Massal Paket", "Paket massal masih draf");
        for (PaketEntity p : List.of(p1, p2)) {
            data.publishedMasterMcq(paketService.topicsOf(p.getId()).get(0), "Soal terbit " + p.getTitle());
            masterPublishing.publishPaket(p.getId(), false);
        }

        mvc.perform(get("/eduscreen/bank-soal").param("subjectId", p1.getSubjectId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"paketIds\" value=\"" + p1.getId() + "\" data-massal")))
                .andExpect(content().string(not(containsString("name=\"paketIds\" value=\"" + draf.getId() + "\""))))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mvc.perform(post("/eduscreen/bank-soal/paket/massal")
                        .param("aksi", "tarik").param("subjectId", p1.getSubjectId().toString())
                        .param("paketIds", p1.getId().toString(), p2.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/eduscreen/bank-soal?subjectId=" + p1.getSubjectId()));
        assertThat(paketRepo.findById(p1.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(paketRepo.findById(p2.getId()).orElseThrow().isPublished()).isFalse();
    }

    @Test
    @DisplayName("AC-B26: Bank Soal Client — hapus beberapa soal sekaligus di satu Topic")
    void soalClientMassal() throws Exception {
        ClientEntity client = data.client("SD Massal Bank");
        var admin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin Bank Massal")));
        PaketEntity paket = data.paket(client, "Fisika Massal Bank", "Paket Massal Bank");
        TopicEntity topic = paketService.topicsOf(paket.getId()).get(0);
        QuestionEntity a = data.mcq(client, topic, "Soal client massal A unik", 4);
        QuestionEntity b = data.mcq(client, topic, "Soal client massal B unik", 4);
        QuestionEntity tetap = data.mcq(client, topic, "Soal client massal tetap unik", 4);

        mvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"questionIds\" value=\"" + a.getId() + "\" data-massal")))
                // Terbitkan/Tarik milik ruang kerja master saja.
                .andExpect(content().string(not(containsString("value=\"tarik\""))));

        mvc.perform(post("/bank-soal/paket/{id}/soal/massal", paket.getId())
                        .param("aksi", "hapus")
                        .param("questionIds", a.getId().toString(), b.getId().toString())
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/bank-soal/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Soal client massal A unik"))))
                .andExpect(content().string(not(containsString("Soal client massal B unik"))))
                .andExpect(content().string(containsString(tetap.getBodyText())));
    }
}
