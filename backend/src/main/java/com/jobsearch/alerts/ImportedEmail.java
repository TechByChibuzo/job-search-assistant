package com.jobsearch.alerts;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An alert email already read, so it is processed once. */
@Entity
class ImportedEmail {

    @Id
    private String messageId;

    private String sender;

    private String subject;

    private Instant receivedAt;

    private Instant importedAt;

    private int rolesFound;

    protected ImportedEmail() {}

    ImportedEmail(AlertEmail email, Instant now, int rolesFound) {
        this.messageId = email.messageId();
        this.sender = email.from();
        this.subject = email.subject();
        this.receivedAt = email.receivedAt();
        this.importedAt = now;
        this.rolesFound = rolesFound;
    }
}
