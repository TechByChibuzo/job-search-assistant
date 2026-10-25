package com.jobsearch.job;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.jobsearch.company.Company;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.Region;
import com.jobsearch.job.source.Workplace;

/**
 * An open role at a company. Jobs are never deleted: one that leaves the feed is closed, so the history
 * of outreach about it survives, and it reopens if it comes back.
 */
@Entity
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    private String externalId;

    private String title;

    private String team;

    private String location;

    @Enumerated(EnumType.STRING)
    private Workplace workplace;

    @Enumerated(EnumType.STRING)
    private Region region;

    private Instant postedAt;

    private String url;

    private String description;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant closedAt;

    /**
     * Imported on the company's first sync and already older than the "new" window, so it is shown as seen
     * rather than new. Without this, adding a company would mark its whole board as new.
     */
    private boolean baseline;

    protected Job() {}

    Job(Company company, FetchedJob fetched, Instant now, boolean baseline) {
        this.company = company;
        this.externalId = fetched.externalId();
        this.firstSeenAt = now;
        this.baseline = baseline;
        apply(fetched, now);
    }

    /** Copies the latest feed data and marks the job as seen now, reopening it if it was closed. */
    void apply(FetchedJob fetched, Instant now) {
        this.title = fetched.title();
        this.team = fetched.team();
        this.location = fetched.location();
        this.workplace = fetched.workplace();
        this.region = fetched.region();
        this.postedAt = fetched.postedAt();
        this.url = fetched.url();
        this.description = fetched.description();
        this.lastSeenAt = now;
        this.closedAt = null;
    }

    void close(Instant now) {
        this.closedAt = now;
    }

    /** New means first seen after {@code since}, and not part of a company's first import. */
    public boolean isNewSince(Instant since) {
        return !baseline && firstSeenAt.isAfter(since);
    }

    /**
     * When the role was listed: its posting date, or for a feed without dates, when the app first saw it.
     * Null for an undated role from a company's first import, whose age is unknown.
     */
    public Instant getListedAt() {
        if (postedAt != null) {
            return postedAt;
        }
        return baseline ? null : firstSeenAt;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getTeam() {
        return team;
    }

    public String getLocation() {
        return location;
    }

    public Workplace getWorkplace() {
        return workplace;
    }

    public Region getRegion() {
        return region;
    }

    public boolean isBaseline() {
        return baseline;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
