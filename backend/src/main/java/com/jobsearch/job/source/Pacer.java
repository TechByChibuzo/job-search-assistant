package com.jobsearch.job.source;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spaces out page requests to the same site. Search-based feeds (Amazon, Google, Workday) take dozens of
 * pages, and a daily personal sync has no reason to hit them faster than a person browsing would.
 */
@Component
public class Pacer {

    private final Duration delay;
    private final int maxPages;

    public Pacer(@Value("${app.http.page-delay}") Duration delay, @Value("${app.http.max-pages}") int maxPages) {
        this.delay = delay;
        this.maxPages = maxPages;
    }

    /** No delay, for tests. */
    public static Pacer immediate(int maxPages) {
        return new Pacer(Duration.ZERO, maxPages);
    }

    public int maxPages() {
        return maxPages;
    }

    public void beforeNextPage() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while paging", e);
        }
    }
}
