package com.eduscreen.app.modules.assessment.controller;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.service.PaketService;
import com.eduscreen.app.modules.assessment.service.QuestionService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Formulir halaman Paket baru (BR-Q07): judul, Subject, dan soal-soal pertamanya dalam satu
 * kiriman. Kelas mutable, bukan record, karena blok soalnya lahir dari x-for di peramban dengan
 * nama berindeks ({@code soal[3].bodyHtml}) — Spring hanya bisa menumbuhkan daftar seperti itu
 * lewat setter. Indeksnya boleh berlubang: blok yang dihapus di layar meninggalkan celah, dan
 * Spring mengisinya dengan {@link SoalForm} kosong yang {@link #soalBaru()} lewati.
 */
public class PaketBaruForm {

    private String title;
    private UUID subjectId;
    private String subjectName;
    private List<SoalForm> soal = new ArrayList<>();

    public PaketService.PaketDraft draft() {
        return new PaketService.PaketDraft(title, subjectId, subjectName);
    }

    /** Soal yang benar-benar diisi; blok kosong sisa penghapusan di layar dilewati. */
    public List<QuestionService.SoalBaru> soalBaru() {
        List<QuestionService.SoalBaru> hasil = new ArrayList<>();
        for (SoalForm s : soal) {
            if (s == null || s.bodyHtml == null || s.bodyHtml.isBlank()) {
                continue;
            }
            hasil.add(new QuestionService.SoalBaru(s.topicTitle, QuestionService.draftOf(
                    null, s.type, s.bodyHtml, s.explanationHtml, s.optionBody, s.correctIndex)));
        }
        return hasil;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public List<SoalForm> getSoal() {
        return soal;
    }

    public void setSoal(List<SoalForm> soal) {
        this.soal = soal;
    }

    /** Satu blok soal; nama kolomnya sama dengan editor soal supaya {@code QuestionService.draftOf} dipakai apa adanya. */
    public static class SoalForm {

        private String topicTitle;
        private QuestionType type = QuestionType.MULTIPLE_CHOICE;
        private String bodyHtml;
        private String explanationHtml;
        private List<String> optionBody = new ArrayList<>();
        private int correctIndex = -1;

        public String getTopicTitle() {
            return topicTitle;
        }

        public void setTopicTitle(String topicTitle) {
            this.topicTitle = topicTitle;
        }

        public QuestionType getType() {
            return type;
        }

        public void setType(QuestionType type) {
            this.type = type;
        }

        public String getBodyHtml() {
            return bodyHtml;
        }

        public void setBodyHtml(String bodyHtml) {
            this.bodyHtml = bodyHtml;
        }

        public String getExplanationHtml() {
            return explanationHtml;
        }

        public void setExplanationHtml(String explanationHtml) {
            this.explanationHtml = explanationHtml;
        }

        public List<String> getOptionBody() {
            return optionBody;
        }

        public void setOptionBody(List<String> optionBody) {
            this.optionBody = optionBody;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }

        public void setCorrectIndex(int correctIndex) {
            this.correctIndex = correctIndex;
        }
    }
}
