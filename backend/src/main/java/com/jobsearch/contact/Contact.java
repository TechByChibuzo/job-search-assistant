package com.jobsearch.contact;

import java.time.Instant;

import jakarta.persistence.Column;
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

/** A person at a company who might refer the owner, added by hand (or, later, by the browser extension). */
@Entity
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    private String name;

    private String title;

    @Enumerated(EnumType.STRING)
    private ContactKind kind;

    private String linkedinUrl;

    private String email;

    private String notes;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected Contact() {}

    public Contact(Company company, String name, ContactKind kind) {
        this.company = company;
        this.name = name;
        this.kind = kind;
    }

    public void update(String name, String title, ContactKind kind, String linkedinUrl, String email, String notes) {
        if (name != null) {
            this.name = name;
        }
        if (kind != null) {
            this.kind = kind;
        }
        this.title = title;
        this.linkedinUrl = linkedinUrl;
        this.email = email;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public ContactKind getKind() {
        return kind;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
