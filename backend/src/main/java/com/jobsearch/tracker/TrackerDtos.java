package com.jobsearch.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.jobsearch.contact.ContactKind;
import com.jobsearch.job.Job;

final class TrackerDtos {

    private TrackerDtos() {}

    /** The role a card or conversation is about, kept small. */
    record RoleRef(Long id, Long companyId, String companyName, String title, String location, String url, boolean open,
            boolean manual) {

        static RoleRef of(Job job) {
            return job == null ? null : new RoleRef(job.getId(), job.getCompany().getId(), job.getCompany().getName(),
                    job.getTitle(), job.getLocation(), job.getUrl(), job.isOpen(), job.isManual());
        }
    }

    record PersonRef(Long id, String name, String title, ContactKind kind, String linkedinUrl, Long companyId, String companyName) {}

    record EventView(Long id, OutreachEventType type, Instant at, String note) {}

    record OutreachView(
            Long id,
            PersonRef person,
            RoleRef role,
            OutreachChannel channel,
            OutreachStatus status,
            String message,
            Instant firstSentAt,
            Instant lastContactedAt,
            int followUps,
            LocalDate followUpOn,
            boolean followUpDue,
            List<EventView> events) {

        static OutreachView of(Outreach o, LocalDate today, boolean withEvents) {
            var c = o.getContact();
            return new OutreachView(
                    o.getId(),
                    new PersonRef(c.getId(), c.getName(), c.getTitle(), c.getKind(), c.getLinkedinUrl(), c.getCompany().getId(),
                            c.getCompany().getName()),
                    RoleRef.of(o.getJob()),
                    o.getChannel(),
                    o.getStatus(),
                    o.getMessage(),
                    o.getFirstSentAt(),
                    o.getLastContactedAt(),
                    o.getFollowUps(),
                    o.getFollowUpOn(),
                    o.isFollowUpDue(today),
                    withEvents
                            ? o.getEvents().stream().map(e -> new EventView(e.getId(), e.getType(), e.getAt(), e.getNote())).toList()
                            : List.of());
        }
    }

    record PursuitView(
            Long id,
            PursuitStage stage,
            RoleRef role,
            LocalDate appliedOn,
            String resumeVersion,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            int people,
            int awaitingReply,
            LocalDate nextFollowUpOn,
            boolean followUpDue) {}

    /** Tracking for one role: the pursuit (null when untracked) and every conversation about it. */
    record RoleTracking(PursuitView pursuit, List<OutreachView> outreach) {}

    record TrackRequest(@NotNull Long jobId) {}

    /** Fields left null are unchanged; {@code clearAppliedOn} empties the date. */
    record UpdatePursuitRequest(
            PursuitStage stage,
            LocalDate appliedOn,
            boolean clearAppliedOn,
            @Size(max = 200) String resumeVersion,
            @Size(max = 5000) String notes) {}

    /**
     * The owner sent someone a message. {@code jobId} is optional (networking with no role in mind);
     * {@code sentOn} defaults to today.
     */
    record LogOutreachRequest(
            @NotNull Long contactId,
            Long jobId,
            OutreachChannel channel,
            @Size(max = 10000) String message,
            LocalDate sentOn) {}

    /** The extension, after the owner submitted an application on {@code url}. */
    record ApplicationRequest(
            @NotNull @Size(max = 2000) String url,
            @Size(max = 300) String title,
            @Size(max = 200) String company,
            @Size(max = 200) String resumeVersion) {}

    record RecordEventRequest(@NotNull OutreachEventType type, @Size(max = 5000) String note) {}
}
