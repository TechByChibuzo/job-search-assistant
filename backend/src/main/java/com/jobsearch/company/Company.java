package com.jobsearch.company;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private AtsType ats;

    private String board;

    private String careersUrl;

    /** Comma-separated search phrases; null means every role. */
    private String search;

    /** The company's own website ("airbnb.com"), for its logo. */
    private String domain;

    private boolean active = true;

    /** Set by the owner: the company doesn't sponsor visas, whatever its postings say. */
    private boolean noSponsorship;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    private Instant lastSyncedAt;

    @Enumerated(EnumType.STRING)
    private SyncStatus lastSyncStatus;

    private String lastSyncError;

    protected Company() {}

    public Company(String name, DetectedBoard board, String careersUrl) {
        this.name = name;
        this.ats = board.ats();
        this.board = board.board();
        this.careersUrl = careersUrl;
    }

    /** Points the company at a new board, for when it has switched ATS. */
    public void moveTo(DetectedBoard board, String careersUrl) {
        this.ats = board.ats();
        this.board = board.board();
        this.careersUrl = careersUrl;
        this.lastSyncStatus = null;
        this.lastSyncError = null;
    }

    public void recordSync(SyncStatus status, Instant at, String error) {
        this.lastSyncStatus = status;
        this.lastSyncedAt = at;
        this.lastSyncError = error;
    }

    public void setSearch(String search) {
        this.search = search == null || search.isBlank() ? null : search.strip();
    }

    /** "software engineer, backend" → ["software engineer", "backend"]. */
    public List<String> getSearches() {
        if (search == null) {
            return List.of();
        }
        return Arrays.stream(search.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSearch() {
        return search;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean isNoSponsorship() {
        return noSponsorship;
    }

    public void setNoSponsorship(boolean noSponsorship) {
        this.noSponsorship = noSponsorship;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AtsType getAts() {
        return ats;
    }

    public String getBoard() {
        return board;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public SyncStatus getLastSyncStatus() {
        return lastSyncStatus;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }
}
