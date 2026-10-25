package com.jobsearch.tracker;

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

@Entity
public class OutreachEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outreach_id")
    private Outreach outreach;

    @Enumerated(EnumType.STRING)
    private OutreachEventType type;

    private Instant at;

    private String note;

    protected OutreachEvent() {}

    OutreachEvent(Outreach outreach, OutreachEventType type, Instant at, String note) {
        this.outreach = outreach;
        this.type = type;
        this.at = at;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public OutreachEventType getType() {
        return type;
    }

    public Instant getAt() {
        return at;
    }

    public String getNote() {
        return note;
    }
}
