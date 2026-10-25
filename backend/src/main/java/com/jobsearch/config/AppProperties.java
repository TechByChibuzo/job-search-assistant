package com.jobsearch.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Cors cors, Http http, Sync sync, Jobs jobs) {

    public record Cors(List<String> allowedOrigins) {}

    public record Http(String userAgent) {}

    /** The daily sync; the cron expression is read directly by the scheduler. */
    public record Sync(boolean enabled, String cron) {}

    /** A job counts as new when it was first seen within this window. */
    public record Jobs(Duration newWindow) {}
}
