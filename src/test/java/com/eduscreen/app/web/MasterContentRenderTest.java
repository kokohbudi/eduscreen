package com.eduscreen.app.web;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ExerciseEntity;
import com.eduscreen.app.modules.assessment.repository.QuestionEntity;
import com.eduscreen.app.modules.assessment.repository.TopicEntity;
import com.eduscreen.app.modules.assessment.domain.UserRole;
import com.eduscreen.app.support.PostgresTestBase;
import com.eduscreen.app.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Setiap halaman dan fragmen baru benar-benar dirender, bukan sekadar dipetakan.
 *
 * <p>Ada karena dua galat templat pada fitur ini lolos seluruh tes layanan dan baru meledak saat
 * halaman disentuh: ekspresi {@code ${...}} bersarang, dan {@code th:each} yang ditaruh bersama
 * {@code th:replace} di elemen yang sama sehingga fragmen dipanggil sebelum variabel iterasinya
 * terikat. Keduanya kelas kesalahan yang hanya bisa ditangkap dengan merender (TC-13, TC-14).
 */
@AutoConfigureMockMvc
class MasterContentRenderTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;

    @Test
    @DisplayName("TC-13 (FR-064): ruang kerja Question master dirender utuh, halaman penuh maupun fragmen HTMX")
    void ruangKerjaSoalMasterDirender() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        data.masterMcq(topic, "Soal render draf");
        data.publishedMasterMcq(topic, "Soal render terbit");

        mockMvc.perform(get("/eduscreen/soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal render draf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Belum terbit")))
                // URL yang benar-benar terpasang, bukan sekadar status 200. Templat bersama ini
                // menyusun jalurnya dari atribut model, dan `@{...}` memperlakukan isinya sebagai
                // teks URL — bukan SpEL. Versi pertama karena itu sempat merender
                // `hx-post="basePath/..."` secara harfiah dan tetap membalas 200; hanya
                // pemeriksaan jalur seperti inilah yang menangkapnya.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-post=\"/eduscreen/soal/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/terbit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-delete=\"/eduscreen/soal/")))
                // Token CSRF harus benar-benar sampai ke HTML: saat atributnya dipasang pada
                // elemen ber-th:remove="tag" ia ikut terbuang, dan setiap tombol HTMX di halaman
                // ini dijawab 403 walau semuanya tetap dirender 200.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-headers=")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("basePath"))));

        mockMvc.perform(get("/eduscreen/soal").header("HX-Request", "true").with(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/eduscreen/soal/baru").param("topicId", topic.getId().toString()).with(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/eduscreen/subject/{id}/topic", data.subjectIdOf(topic)).with(admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-14 (BR-O04): ruang kerja master memakai jalur Subject-nya sendiri, dan form ubah nama hanya muncul saat ada Subject terpilih")
    void jalurSubjectRuangKerjaMaster() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");

        mockMvc.perform(get("/eduscreen/soal").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("action=\"/eduscreen/subject\"")))
                // Tanpa Subject terpilih, tidak ada yang bisa di-rename.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/nama"))));

        mockMvc.perform(get("/eduscreen/soal")
                        .param("subjectId", data.subjectIdOf(topic).toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/eduscreen/subject/" + data.subjectIdOf(topic) + "/nama")));
    }

    @Test
    @DisplayName("BR-O03 (FR-013): membuat Subject global memuat ulang halaman ke Subject itu, sehingga hasilnya terlihat")
    void subjectGlobalBaruMemuatUlangKeSubjectItu() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));

        // Berhasil harus TERLIHAT. Menyisipkan <option> ke dalam <select> yang tertutup membuat
        // penambahan yang sukses tidak meninggalkan jejak apa pun di layar, dan nama kembar yang
        // dibalas 400 bahkan tidak ditukar HTMX sama sekali — dua-duanya terbaca sebagai "gagal".
        mockMvc.perform(post("/eduscreen/subject").param("name", "Kimia Kelas 11 render")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/eduscreen/soal?subjectId=")));
    }

    @Test
    @DisplayName("BR-O03: nama Subject global yang kembar membalas 400 berpesan, bukan diam")
    void subjectGlobalKembarMembalasPesan() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        mockMvc.perform(post("/eduscreen/subject").param("name", "Fisika Kelas 10 render kembar")
                        .with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/eduscreen/subject").param("name", "fisika KELAS 10 render kembar")
                        .with(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sudah ada")));
    }

    @Test
    @DisplayName("FR-013 (BR-O03): Subject lokal Client juga memuat ulang, bukan menyisip diam-diam")
    void subjectLokalClientMemuatUlang() throws Exception {
        ClientEntity client = data.client("SD Render Subject");
        var clientAdmin = user(data.principal(
                data.user(client, UserRole.CLIENT_ADMIN, "Admin Render Subject")));

        mockMvc.perform(post("/admin/subject").param("name", "Bahasa Sunda Kelas 5 render")
                        .with(clientAdmin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/soal?subjectId=")));
    }

    @Test
    @DisplayName("TC-14 (FR-071): daftar dan perakit paket master dirender utuh")
    void perakitPaketDirender() throws Exception {
        var admin = user(data.principal(data.eduscreenAdmin()));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        QuestionEntity soal = data.publishedMasterMcq(topic, "Isi paket render");
        ExerciseEntity paket = data.masterExercise("Paket render", List.of(soal));

        mockMvc.perform(get("/eduscreen/paket").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paket render")));
        mockMvc.perform(get("/eduscreen/paket/{id}", paket.getId()).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Isi paket render")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/paket/" + paket.getId() + "/terbit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-post=\"/eduscreen/paket/" + paket.getId() + "/item\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("itemPath"))));
    }

    @Test
    @DisplayName("TC-13 (FR-074, FR-076): katalog dirender per Question lengkap dengan penanda adopsi")
    void katalogGranularDirender() throws Exception {
        ClientEntity client = data.client("SD Render");
        var clientAdmin = user(data.principal(data.user(client, UserRole.CLIENT_ADMIN, "Admin")));
        TopicEntity topic = data.globalTopic("Matematika Kelas 4", "Pecahan");
        data.publishedMasterMcq(topic, "Soal katalog render");
        data.masterMcq(topic, "Soal katalog masih digarap");

        mockMvc.perform(get("/katalog").with(clientAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Soal katalog render")))
                // Yang masih digarap tidak pernah bocor ke katalog (FR-067, SC-013).
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Soal katalog masih digarap"))));

        mockMvc.perform(get("/katalog/soal").with(clientAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-13 (FR-024): bank soal Client tetap memakai jalurnya sendiri, tidak tertular jalur ruang kerja master")
    void bankSoalClientTetapMemakaiJalurnyaSendiri() throws Exception {
        ClientEntity client = data.client("SD Render2");
        var guru = user(data.principal(data.user(client, UserRole.GURU, "Guru")));
        TopicEntity topic = data.topic(client, "Matematika Kelas 4", "Aljabar");
        data.mcq(client, topic, "Soal sekolah render", 4);

        mockMvc.perform(get("/soal").with(guru))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-delete=\"/soal/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/admin/subject\"")))
                // Nilai bawaan templat bersama, bukan jalur master yang bocor lewat.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/eduscreen/"))));
    }
}
