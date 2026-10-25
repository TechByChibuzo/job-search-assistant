package com.jobsearch.job;

/**
 * A source of roles that isn't a company board, such as job-alert emails. A full sync runs each one after
 * the companies, and reports it as one more line in the results. Implemented outside this package, so the
 * job feature doesn't depend on it.
 */
public interface ExtraFeed {

    /** Shown in sync progress, as a company name would be. */
    String name();

    /** Whether it is set up and should run at all. */
    boolean enabled();

    SyncResult run();
}
