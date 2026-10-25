package com.jobsearch.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.jobsearch.common.ConflictException;

/** Runs the daily sync. Turned off in tests with app.sync.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true", matchIfMissing = true)
class SyncScheduler {

    private final JobSyncService sync;

    SyncScheduler(JobSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${app.sync.cron}")
    void daily() {
        try {
            sync.syncAll();
        } catch (ConflictException alreadyRunning) {
            // A manual sync is in progress and will do the same work
        }
    }
}
