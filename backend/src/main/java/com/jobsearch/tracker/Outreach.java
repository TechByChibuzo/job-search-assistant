package com.jobsearch.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.ConflictException;
import com.jobsearch.contact.Contact;
import com.jobsearch.job.Job;

/**
 * A conversation with one person, usually about one role. It starts when the owner records a message they
 * sent, schedules a follow-up, and moves on as replies and outcomes are recorded. Every step is kept as an
 * event, for the timeline and for reply-rate stats.
 */
@Entity
public class Outreach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @Enumerated(EnumType.STRING)
    private OutreachChannel channel;

    @Enumerated(EnumType.STRING)
    private OutreachStatus status;

    private String message;

    private Instant firstSentAt;

    private Instant lastContactedAt;

    private int followUps;

    /** When to follow up if there's still no reply; null once the conversation has moved on. */
    private LocalDate followUpOn;

    @OneToMany(mappedBy = "outreach", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("at ASC, id ASC")
    private List<OutreachEvent> events = new ArrayList<>();

    protected Outreach() {}

    /** The owner sent a first message on {@code sentOn}; a reminder is due {@code followUpDays} later. */
    static Outreach sent(Contact contact, Job job, OutreachChannel channel, String message, Instant sentAt,
            LocalDate sentOn, int followUpDays) {
        Outreach o = new Outreach();
        o.contact = contact;
        o.job = job;
        o.channel = channel;
        o.message = message;
        o.status = OutreachStatus.AWAITING_REPLY;
        o.firstSentAt = sentAt;
        o.lastContactedAt = sentAt;
        o.followUpOn = sentOn.plusDays(followUpDays);
        o.events.add(new OutreachEvent(o, OutreachEventType.SENT, sentAt, null));
        return o;
    }

    /** Records what happened, moving the conversation on. */
    void record(OutreachEventType type, String note, Instant at, LocalDate today, int followUpDays) {
        switch (type) {
            case SENT -> throw new BadRequestException("A conversation is started once; record a follow-up instead.");
            case FOLLOW_UP -> {
                if (status != OutreachStatus.AWAITING_REPLY) {
                    throw new ConflictException("This conversation has moved on; there is nothing to follow up.");
                }
                followUps++;
                lastContactedAt = at;
                followUpOn = today.plusDays(followUpDays);
            }
            case REPLIED -> moveTo(OutreachStatus.REPLIED);
            case REFERRED -> moveTo(OutreachStatus.REFERRED);
            case NO_REPLY -> moveTo(OutreachStatus.NO_REPLY);
            case DECLINED -> moveTo(OutreachStatus.DECLINED);
            case NOTE -> {
                if (note == null || note.isBlank()) {
                    throw new BadRequestException("A note needs some text.");
                }
            }
        }
        events.add(new OutreachEvent(this, type, at, note == null || note.isBlank() ? null : note.strip()));
    }

    private void moveTo(OutreachStatus next) {
        status = next;
        followUpOn = null;
    }

    public boolean isFollowUpDue(LocalDate today) {
        return status == OutreachStatus.AWAITING_REPLY && followUpOn != null && !followUpOn.isAfter(today);
    }

    public Long getId() {
        return id;
    }

    public Contact getContact() {
        return contact;
    }

    public Job getJob() {
        return job;
    }

    public OutreachChannel getChannel() {
        return channel;
    }

    public OutreachStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getFirstSentAt() {
        return firstSentAt;
    }

    public Instant getLastContactedAt() {
        return lastContactedAt;
    }

    public int getFollowUps() {
        return followUps;
    }

    public LocalDate getFollowUpOn() {
        return followUpOn;
    }

    public List<OutreachEvent> getEvents() {
        return events;
    }
}
