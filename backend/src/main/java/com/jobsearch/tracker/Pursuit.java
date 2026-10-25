package com.jobsearch.tracker;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.jobsearch.job.Job;

/** A role the owner is pursuing, and how far it has got. One per role. */
@Entity
public class Pursuit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @Enumerated(EnumType.STRING)
    private PursuitStage stage;

    private LocalDate appliedOn;

    private String resumeVersion;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    protected Pursuit() {}

    Pursuit(Job job, PursuitStage stage, Instant now) {
        this.job = job;
        this.stage = stage;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Moves forward to {@code next} if it hasn't got that far yet; never moves back. */
    void advanceTo(PursuitStage next, Instant now) {
        if (stage.isBefore(next) && stage != PursuitStage.CLOSED) {
            stage = next;
            updatedAt = now;
        }
    }

    /** The owner sets the stage directly, forward or back. Applying records the date if it isn't set. */
    void setStage(PursuitStage next, LocalDate today, Instant now) {
        stage = next;
        if (next == PursuitStage.APPLIED && appliedOn == null) {
            appliedOn = today;
        }
        updatedAt = now;
    }

    void update(LocalDate appliedOn, String resumeVersion, String notes, Instant now) {
        this.appliedOn = appliedOn;
        this.resumeVersion = resumeVersion;
        this.notes = notes;
        this.updatedAt = now;
    }

    void touch(Instant now) {
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public PursuitStage getStage() {
        return stage;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public String getResumeVersion() {
        return resumeVersion;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
