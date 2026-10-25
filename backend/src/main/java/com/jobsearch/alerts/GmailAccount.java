package com.jobsearch.alerts;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** The owner's Gmail connection. There is at most one, with id 1. */
@Entity
public class GmailAccount {

    static final long ID = 1L;

    @Id
    private Long id;

    private String email;

    /** Lets the app get short-lived read-only access tokens without the owner signing in again. */
    private String refreshToken;

    private Instant connectedAt;

    private Instant lastImportAt;

    private String lastImportError;

    private int lastEmailsRead;

    private int lastRolesAdded;

    private int lastUnreadable;

    protected GmailAccount() {}

    GmailAccount(String email, String refreshToken, Instant now) {
        this.id = ID;
        this.email = email;
        this.refreshToken = refreshToken;
        this.connectedAt = now;
    }

    void recordImport(Instant at, int emailsRead, int rolesAdded, int unreadable) {
        lastImportAt = at;
        lastImportError = null;
        lastEmailsRead = emailsRead;
        lastRolesAdded = rolesAdded;
        lastUnreadable = unreadable;
    }

    void recordFailure(Instant at, String error) {
        lastImportAt = at;
        lastImportError = error;
    }

    public String getEmail() {
        return email;
    }

    String getRefreshToken() {
        return refreshToken;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastImportAt() {
        return lastImportAt;
    }

    public String getLastImportError() {
        return lastImportError;
    }

    public int getLastEmailsRead() {
        return lastEmailsRead;
    }

    public int getLastRolesAdded() {
        return lastRolesAdded;
    }

    public int getLastUnreadable() {
        return lastUnreadable;
    }
}
