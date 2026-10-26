package com.jobsearch.job;

import java.time.Instant;
import java.util.List;

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

    /** Added by pasting a link: no feed lists it, so a sync never closes it. */
    private boolean manual;

    /** How well the role fits the resume, 0 to 100, as judged by Claude; null until scored. */
    private Integer matchScore;

    private String matchSummary;

    /** Newline-separated short phrases. */
    private String matchStrengths;

    private String matchGaps;

    private String matchModel;

    private Instant matchedAt;

    /** What the description says about visas and legal status; null when it says nothing restrictive. */
    @Enumerated(EnumType.STRING)
    private VisaRestriction visaRestriction;

    /** The sentence the restriction was read from. */
    private String visaEvidence;

    /** When a description was last sought for a role whose feed left it out (see JobDescriptions). */
    private Instant descriptionCheckedAt;

    protected Job() {}

    Job(Company company, FetchedJob fetched, Instant now, boolean baseline) {
        this.company = company;
        this.externalId = fetched.externalId();
        this.firstSeenAt = now;
        this.baseline = baseline;
        apply(fetched, now);
    }

    /** A role added by hand: new as of now, and open until the owner closes it. */
    static Job manual(Company company, FetchedJob fetched, Instant now) {
        Job job = new Job(company, fetched, now, false);
        job.manual = true;
        return job;
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
        // A feed without descriptions mustn't wipe one found for the role separately
        if (fetched.description() != null && !fetched.description().isBlank() || descriptionCheckedAt == null) {
            this.description = fetched.description();
        }
        checkVisa();
        this.lastSeenAt = now;
        this.closedAt = null;
    }

    /** A description found for a role whose feed had none; the visa check reads it too. */
    void recordDescription(String text, Instant at) {
        if (text != null && !text.isBlank() && (description == null || description.isBlank())) {
            this.description = text.strip();
            checkVisa();
        }
        this.descriptionCheckedAt = at;
    }

    public Instant getDescriptionCheckedAt() {
        return descriptionCheckedAt;
    }

    /** Reads the description for visa and legal status restrictions; rerun when the rules improve. */
    void checkVisa() {
        VisaCheck.Result result = VisaCheck.check(description);
        this.visaRestriction = result.restriction();
        this.visaEvidence = result.evidence();
    }

    public VisaRestriction getVisaRestriction() {
        return visaRestriction;
    }

    public String getVisaEvidence() {
        return visaEvidence;
    }

    void close(Instant now) {
        this.closedAt = now;
    }

    /** The owner closes or reopens a hand-added role; feed roles follow their feed. */
    void setClosedByOwner(boolean closed, Instant now) {
        this.closedAt = closed ? now : null;
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

    void recordMatch(int score, String summary, List<String> strengths, List<String> gaps, String model, Instant at) {
        this.matchScore = Math.max(0, Math.min(100, score));
        this.matchSummary = summary;
        this.matchStrengths = String.join("\n", strengths);
        this.matchGaps = String.join("\n", gaps);
        this.matchModel = model;
        this.matchedAt = at;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public String getMatchSummary() {
        return matchSummary;
    }

    public List<String> getMatchStrengths() {
        return lines(matchStrengths);
    }

    public List<String> getMatchGaps() {
        return lines(matchGaps);
    }

    public String getMatchModel() {
        return matchModel;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    private static List<String> lines(String s) {
        return s == null || s.isBlank() ? List.of() : List.of(s.split("\n"));
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

    public boolean isManual() {
        return manual;
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
