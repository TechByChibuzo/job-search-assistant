package com.jobsearch.tracker;

/** How far the owner has got with a role, in order. CLOSED ends it, whatever the outcome. */
public enum PursuitStage {
    SAVED,
    REACHING_OUT,
    REFERRED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    CLOSED;

    boolean isBefore(PursuitStage other) {
        return ordinal() < other.ordinal();
    }
}
