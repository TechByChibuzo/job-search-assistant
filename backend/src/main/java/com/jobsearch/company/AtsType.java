package com.jobsearch.company;

/**
 * Where a company's roles are read from. The first three have documented public APIs; the rest are read the
 * way their own careers pages load them, which works but can change without notice (ADR 0004). The last
 * three each serve one company, named here.
 */
public enum AtsType {
    GREENHOUSE(null),
    LEVER(null),
    ASHBY(null),
    WORKDAY(null),
    AVATURE(null),
    AMAZON("Amazon"),
    GOOGLE("Google"),
    GOLDMAN("Goldman Sachs");

    private final String company;

    AtsType(String company) {
        this.company = company;
    }

    /** The company a single-company source belongs to, or null for systems many companies use. */
    public String company() {
        return company;
    }

    /** Search-based sources return only what matches the company's search phrases, so phrases matter more there. */
    public boolean isSearchBased() {
        return this == WORKDAY || company != null;
    }
}
