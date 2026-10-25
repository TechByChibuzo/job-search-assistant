package com.jobsearch.company;

/**
 * Where a company's roles are read from. The first three have documented public APIs; the rest are read the
 * way their own careers pages load them, which works but can change without notice (ADR 0004).
 */
public enum AtsType {
    GREENHOUSE,
    LEVER,
    ASHBY,
    WORKDAY,
    AVATURE,
    AMAZON,
    GOOGLE;

    /** Search-based sources return only what matches the company's search phrases, so phrases matter more there. */
    public boolean isSearchBased() {
        return this == WORKDAY || this == AMAZON || this == GOOGLE;
    }
}
