package com.jobsearch.job;

/**
 * Work that runs after the company syncs, such as reading job-alert emails or scoring new roles against the
 * resume. A full sync runs each one that is enabled, and reports it as one more line in the results.
 * Implemented outside this package, so the job feature doesn't depend on them.
 */
public interface SyncStep {

    /** Shown in sync progress, as a company name would be. */
    String name();

    /** Whether it is set up and should run at all. */
    boolean enabled();

    SyncResult run();
}
