package com.jobsearch.job;

import com.jobsearch.company.SyncStatus;

/** What one company's sync did: how many roles appeared, were refreshed, or left the feed. */
public record SyncResult(
        Long companyId,
        String companyName,
        SyncStatus status,
        int newJobs,
        int updatedJobs,
        int closedJobs,
        String error) {

    static SyncResult failed(Long companyId, String companyName, SyncStatus status, String error) {
        return new SyncResult(companyId, companyName, status, 0, 0, 0, error);
    }
}
