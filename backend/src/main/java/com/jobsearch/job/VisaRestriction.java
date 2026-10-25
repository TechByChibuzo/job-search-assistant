package com.jobsearch.job;

/**
 * What a posting says about who may hold the role, for an owner who needs visa sponsorship. Ordered from the
 * strictest: when a posting says several things, the first in this order is the one shown.
 */
public enum VisaRestriction {
    /** U.S. citizens, nationals or green card holders only (export control, government contracts). */
    CITIZENS_ONLY,
    /** A security clearance is required, which in practice means U.S. citizens only. */
    CLEARANCE,
    /** The employer says it won't sponsor a work visa, now or later. */
    NO_SPONSORSHIP,
    /** Legal status rules may apply ("may need to meet certain legal status requirements"); worth checking, not hidden. */
    MAY_BE_RESTRICTED;

    /** Whether the role is closed to someone who needs sponsorship, rather than only possibly restricted. */
    public boolean excludes() {
        return this != MAY_BE_RESTRICTED;
    }
}
