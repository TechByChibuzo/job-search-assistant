package com.jobsearch.company;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A company's logo as fetched, or a record that none was found, so the lookup isn't repeated every view. */
@Entity
class CompanyLogo {

    @Id
    private Long companyId;

    private String domain;

    private boolean found;

    private String contentType;

    private byte[] image;

    private Instant fetchedAt;

    protected CompanyLogo() {}

    CompanyLogo(Long companyId, String domain, boolean found, String contentType, byte[] image, Instant fetchedAt) {
        this.companyId = companyId;
        this.domain = domain;
        this.found = found;
        this.contentType = contentType;
        this.image = image;
        this.fetchedAt = fetchedAt;
    }

    String getDomain() {
        return domain;
    }

    boolean isFound() {
        return found;
    }

    String getContentType() {
        return contentType;
    }

    byte[] getImage() {
        return image;
    }

    Instant getFetchedAt() {
        return fetchedAt;
    }
}
