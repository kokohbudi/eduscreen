package com.eduscreen.app.modules.assessment.domain;

/** COMPLETED dan EXPIRED terminal; tidak ada transisi keluar (BR-T06). */
public enum SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    EXPIRED
}
