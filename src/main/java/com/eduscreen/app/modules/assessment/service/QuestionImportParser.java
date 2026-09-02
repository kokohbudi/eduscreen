package com.eduscreen.app.modules.assessment.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mengurai berkas impor soal (.xlsx lewat Apache POI, atau .csv) menjadi baris mentah, tanpa
 * menyentuh database. Kolomnya tetap:
 * {@code topic | tipe | soal | opsi_a | opsi_b | opsi_c | opsi_d | kunci | pembahasan}, baris
 * pertama adalah header dan dilewati. Kolom {@code topic} opsional dan tidak dibaca — lihat
 * {@link #toRawRow}.
 *
 * <p>Parser ini murni sintaksis: ia tidak tahu apa itu Topic yang "terlihat Client" atau apa itu
 * sanitasi HTML — itu tanggung jawab {@link QuestionImportService}. Pemisahan ini membuat aturan
 * bentuk berkas bisa diuji tanpa database maupun Spring context.
 */
@Component
public class QuestionImportParser {

    /** Jumlah kolom minimum yang selalu disediakan, walau baris sumber lebih pendek. */
    private static final int MIN_COLUMNS = 9;

    public record RawRow(int lineNumber, String type, String body,
                          List<String> options, String answerKey, String explanation) {}

    public record RowFailure(int lineNumber, String reason) {}

    public record ParseResult(List<RawRow> valid, List<RowFailure> failures) {}

    /**
     * Format tak terbaca (bukan xlsx yang sah, atau CSV yang gagal didekode) membatalkan
     * SELURUH berkas — hanya itu yang boleh, karena tanpa bisa membaca strukturnya, "nomor
     * baris" pun tidak berarti apa-apa (BR-Q05).
     */
    public ParseResult parse(String filename, byte[] content) {
        List<String[]> rows = isCsv(filename) ? parseCsv(content) : parseXlsx(content);
        List<RawRow> valid = new ArrayList<>();
        List<RowFailure> failures = new ArrayList<>();

        // Baris pertama (index 0) adalah header dan dilewati; nomor baris yang dilaporkan ke
        // pengguna memakai penomoran manusiawi 1-based dari berkas aslinya (AC-Q03).
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] cols = rows.get(i);
            if (isBlankRow(cols)) {
                continue;
            }
            try {
                valid.add(toRawRow(lineNumber, cols));
            } catch (RowValidationException e) {
                failures.add(new RowFailure(lineNumber, e.getMessage()));
            }
        }
        return new ParseResult(valid, failures);
    }

    /**
     * Menghitung baris data (di luar header) tanpa mem-parse isinya, dipakai
     * {@code QuestionImportService} untuk menolak berkas >500 baris SEBELUM diproses (TC-45,
     * AC-Q06).
     */
    public int countRows(String filename, byte[] content) {
        if (isCsv(filename)) {
            String text = new String(content, StandardCharsets.UTF_8);
            String[] lines = text.split("\r\n|\r|\n", -1);
            int count = lines.length;
            // Newline penutup berkas menghasilkan elemen kosong terakhir; itu bukan baris data.
            if (count > 0 && lines[count - 1].isEmpty()) {
                count--;
            }
            return Math.max(0, count - 1); // dikurangi header
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // getLastRowNum() 0-based sudah otomatis tak menghitung baris header di index 0.
            return Math.max(0, sheet.getLastRowNum());
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Berkas tidak terbaca, pastikan formatnya .xlsx atau .csv yang sah", e);
        }
    }

    private boolean isCsv(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private List<String[]> parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            rows.add(parseCsvLine(line));
        }
        return rows;
    }

    /**
     * Parser CSV kecil dengan dukungan tanda kutip ganda (termasuk kutip ganda yang di-escape
     * sebagai {@code ""} di dalam field berkutip) — sengaja ditulis sendiri, tanpa dependensi
     * tambahan.
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private List<String[]> parseXlsx(byte[] content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            List<String[]> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    rows.add(new String[MIN_COLUMNS]);
                    continue;
                }
                int columnCount = Math.max(row.getLastCellNum(), MIN_COLUMNS);
                String[] cols = new String[columnCount];
                for (int c = 0; c < columnCount; c++) {
                    cols[c] = cellText(row.getCell(c));
                }
                rows.add(cols);
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Berkas tidak terbaca, pastikan formatnya .xlsx atau .csv yang sah", e);
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private boolean isBlankRow(String[] cols) {
        for (String c : cols) {
            if (c != null && !c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String col(String[] cols, int index) {
        return index < cols.length && cols[index] != null ? cols[index].trim() : "";
    }

    /**
     * Validasi per baris (AC-Q03): tipe harus PG/ESSAY, soal tidak boleh kosong; PG butuh minimal
     * 2 opsi terisi dan kunci yang menunjuk opsi terisi; ESSAY wajib tanpa opsi maupun kunci.
     *
     * <p>Kolom {@code topic} (kolom pertama) <b>opsional</b> dan tidak dibaca sama sekali. Sejak
     * ADR-0018 tujuan impor dipilih eksplisit di layar — satu Paket dan satu Topic untuk seluruh
     * berkas — sehingga isi kolom itu tidak menentukan apa pun. Mewajibkannya berarti menolak
     * baris karena kolom yang layar impor sendiri nyatakan tidak dipakai, dua pernyataan yang
     * saling menyangkal di satu tampilan. Kolomnya tetap ada di templat dan tetap dilewati
     * secara posisi, supaya berkas lama yang sudah beredar tetap terbaca apa adanya.
     */
    private RawRow toRawRow(int lineNumber, String[] cols) {
        String type = col(cols, 1).toUpperCase(Locale.ROOT);
        String body = col(cols, 2);
        List<String> optionValues = List.of(col(cols, 3), col(cols, 4), col(cols, 5), col(cols, 6));
        String answerKey = col(cols, 7).toUpperCase(Locale.ROOT);
        String explanation = col(cols, 8);

        if (!type.equals("PG") && !type.equals("ESSAY")) {
            throw new RowValidationException("Kolom tipe harus PG atau ESSAY, ditemukan \"" + col(cols, 1) + "\"");
        }
        if (body.isBlank()) {
            throw new RowValidationException("Kolom soal wajib diisi");
        }

        if (type.equals("PG")) {
            long filledOptions = optionValues.stream().filter(o -> !o.isBlank()).count();
            if (filledOptions < 2) {
                throw new RowValidationException("Soal PG butuh minimal 2 opsi terisi");
            }
            int keyIndex = answerKeyIndex(answerKey);
            if (keyIndex < 0 || optionValues.get(keyIndex).isBlank()) {
                throw new RowValidationException("Kolom kunci harus berupa huruf A-D yang menunjuk opsi yang terisi");
            }
        } else {
            boolean hasAnyOption = optionValues.stream().anyMatch(o -> !o.isBlank());
            if (hasAnyOption || !answerKey.isBlank()) {
                throw new RowValidationException("Soal ESSAY tidak boleh mempunyai opsi maupun kunci jawaban");
            }
        }

        return new RawRow(lineNumber, type, body, optionValues,
                type.equals("PG") ? answerKey : null, explanation);
    }

    private int answerKeyIndex(String key) {
        if (key.length() != 1) {
            return -1;
        }
        char c = key.charAt(0);
        return (c >= 'A' && c <= 'D') ? c - 'A' : -1;
    }

    /** Kegagalan satu baris; ditangkap sepenuhnya di {@link #parse} dan tidak pernah bocor keluar kelas ini. */
    private static final class RowValidationException extends RuntimeException {
        RowValidationException(String message) {
            super(message);
        }
    }
}
