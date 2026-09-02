package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.domain.QuestionType;
import com.eduscreen.app.modules.assessment.repository.SessionAnswerEntity;
import com.eduscreen.app.modules.assessment.repository.SessionQuestionEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Perhitungan skor satu sesi (BR-C01 sampai BR-C06).
 *
 * <pre>
 * poin(MCQ)   = is_correct ? 1 : 0
 * poin(essay) = essay_score / 100      -- null dianggap 0 selama PENDING_REVIEW
 * score       = Σ poin ÷ total_questions
 * </pre>
 *
 * <p>Setiap soal bernilai sama; tidak ada bobot per soal dan tidak ada nilai minus. Salah dan
 * tidak dijawab sama-sama bernilai 0 (BR-C02).
 *
 * <p>Kelas ini murni: ia tidak menyentuh database dan tidak menulis apa pun, sehingga hasilnya
 * bisa diuji tanpa menyalakan sesi.
 */
@Service
public class ScoringService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Rekap satu sesi.
     *
     * <p>{@code hasUngradedEssay} menentukan Result lahir {@code PENDING_REVIEW} atau langsung
     * {@code FINAL} (BR-C05).
     */
    public record Tally(int totalQuestions,
                        int correctCount,
                        int incorrectCount,
                        int unansweredCount,
                        BigDecimal score,
                        boolean hasUngradedEssay) {
    }

    /**
     * @param questions        snapshot sesi, sudah beku
     * @param answers          jawaban per {@code sessionQuestionId}; boleh tidak lengkap
     * @param typeByQuestionId tipe tiap soal di snapshot
     */
    public Tally tally(java.util.List<SessionQuestionEntity> questions,
                       Map<UUID, SessionAnswerEntity> answers,
                       Map<UUID, QuestionType> typeByQuestionId) {

        int total = questions.size();
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        boolean ungradedEssay = false;
        BigDecimal points = BigDecimal.ZERO;

        for (SessionQuestionEntity question : questions) {
            SessionAnswerEntity answer = answers.get(question.getId());
            QuestionType type = typeByQuestionId.getOrDefault(
                    question.getQuestionId(), QuestionType.MULTIPLE_CHOICE);

            if (!isAnswered(answer, type)) {
                // Soal tak terjawab saat finalisasi dihitung salah dan masuk unansweredCount
                // (BR-C06).
                unanswered++;
                continue;
            }

            BigDecimal point;
            if (type == QuestionType.ESSAY) {
                if (answer.getEssayScore() == null) {
                    ungradedEssay = true;
                    point = BigDecimal.ZERO;
                } else {
                    point = BigDecimal.valueOf(answer.getEssayScore())
                            .divide(HUNDRED, 4, RoundingMode.HALF_UP);
                }
            } else {
                point = Boolean.TRUE.equals(answer.getIsCorrect()) ? BigDecimal.ONE : BigDecimal.ZERO;
            }

            points = points.add(point);
            // ponytail: essay bernilai sebagian masuk incorrectCount. Kolom "sebagian" baru
            // ditambahkan bila laporan Guru benar-benar membutuhkannya; skornya sendiri sudah
            // tepat karena dihitung dari poin, bukan dari pencacah ini.
            if (point.compareTo(BigDecimal.ONE) == 0) {
                correct++;
            } else {
                incorrect++;
            }
        }

        BigDecimal score = total == 0
                ? BigDecimal.ZERO
                : points.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        return new Tally(total, correct, incorrect, unanswered, score, ungradedEssay);
    }

    private boolean isAnswered(SessionAnswerEntity answer, QuestionType type) {
        if (answer == null) {
            return false;
        }
        return type == QuestionType.ESSAY
                ? answer.getEssayText() != null && !answer.getEssayText().isBlank()
                : answer.getSelectedOptionId() != null;
    }
}
