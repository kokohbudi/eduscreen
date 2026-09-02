package com.eduscreen.app.modules.assessment.domain;

/** Tiga sebab berakhirnya sesi (§8.3 business-rules). */
public enum TerminalReason {
    MANUAL_SUBMIT,
    TIMER_TIMEOUT,
    EXPIRATION_REACHED
}
