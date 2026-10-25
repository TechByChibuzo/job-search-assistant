package com.jobsearch.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the daily sync through the runner, so the UI shows its progress like a manual one. Off in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true", matchIfMissing = true)
class SyncScheduler {

    private final SyncRunner runner;

    SyncScheduler(SyncRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${app.sync.cron}")
    void daily() {
        runner.runNow();
    }
}
