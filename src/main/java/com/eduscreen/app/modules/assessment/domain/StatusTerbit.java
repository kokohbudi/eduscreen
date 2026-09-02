package com.eduscreen.app.modules.assessment.domain;

/**
 * Penyaring keadaan terbit di ruang kerja konten master. Bukan kolom database — {@code published_at}
 * yang menyimpan keadaannya; ini hanya bahasa penyaring di permukaan HTTP.
 */
public enum StatusTerbit {
    DRAF,
    TERBIT
}
