package com.jobsearch.company;

import java.time.Instant;

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

    private boolean active = true;

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

    public void rename(String name) {
        this.name = name;
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
