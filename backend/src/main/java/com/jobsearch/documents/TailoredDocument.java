package com.jobsearch.documents;

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

import com.jobsearch.ai.TailorService.Kind;
import com.jobsearch.job.Job;

/** A resume or cover letter tailored to one role, kept so it is generated (and paid for) once. */
@Entity
class TailoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    /** The document as JSON, with the check's findings beside each part. */
    private String content;

    private String model;

    private Instant createdAt;

    protected TailoredDocument() {}

    TailoredDocument(Job job, Kind kind, String content, String model, Instant now) {
        this.job = job;
        this.kind = kind;
        this.content = content;
        this.model = model;
        this.createdAt = now;
    }

    void edit(String content) {
        this.content = content;
    }

    Long getId() {
        return id;
    }

    Job getJob() {
        return job;
    }

    Kind getKind() {
        return kind;
    }

    String getContent() {
        return content;
    }

    String getModel() {
        return model;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
