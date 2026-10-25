package com.jobsearch.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Cors cors, Http http, Sync sync, Jobs jobs, Tracker tracker) {

    public record Cors(List<String> allowedOrigins) {}

    /** Outgoing requests: how they identify themselves, and how search feeds are paged. */
    public record Http(String userAgent, Duration pageDelay, int maxPages) {}

    /** The daily sync; the cron expression is read directly by the scheduler. */
    public record Sync(boolean enabled, String cron) {}

    /**
     * @param newWindow a role counts as new when it was first seen within this window
     * @param maxAge    roles posted longer ago than this aren't imported, and the inbox hides them
     * @param usOnly    only import roles in the United States
     */
    public record Jobs(Duration newWindow, Duration maxAge, boolean usOnly) {}

    /** @param followUpAfter how long after a message with no reply to suggest following up */
    public record Tracker(Duration followUpAfter) {}
}
