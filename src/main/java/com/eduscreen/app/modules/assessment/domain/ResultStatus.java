package com.eduscreen.app.modules.assessment.domain;

/** PENDING_REVIEW hanya mungkin bila sesi memuat essay belum dinilai (BR-C05). */
public enum ResultStatus {
    PENDING_REVIEW,
    FINAL
}
