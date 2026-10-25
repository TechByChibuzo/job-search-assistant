package com.jobsearch.tracker;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.ConflictException;
import com.jobsearch.common.NotFoundException;
import com.jobsearch.config.AppProperties;
import com.jobsearch.contact.Contact;
import com.jobsearch.contact.ContactService;
import com.jobsearch.job.Job;
import com.jobsearch.job.JobLookup;
import com.jobsearch.job.ManualJobService;
import com.jobsearch.tracker.TrackerDtos.OutreachView;
import com.jobsearch.tracker.TrackerDtos.PursuitView;
import com.jobsearch.tracker.TrackerDtos.RoleRef;
import com.jobsearch.tracker.TrackerDtos.RoleTracking;
import com.jobsearch.tracker.TrackerDtos.UpdatePursuitRequest;

/**
 * Roles the owner is pursuing and the conversations about them. Recording outreach moves a role along on
 * its own: the first message makes it "reaching out", a referral makes it "referred".
 */
@Service
public class TrackerService {

    private final PursuitRepository pursuits;
    private final OutreachRepository outreach;
    private final JobLookup jobs;
    private final ManualJobService manualJobs;
    private final ContactService contacts;
    private final Clock clock;
    private final int followUpDays;

    TrackerService(PursuitRepository pursuits, OutreachRepository outreach, JobLookup jobs, ManualJobService manualJobs,
            ContactService contacts, Clock clock, AppProperties properties) {
        this.pursuits = pursuits;
        this.outreach = outreach;
        this.jobs = jobs;
        this.manualJobs = manualJobs;
        this.contacts = contacts;
        this.clock = clock;
        this.followUpDays = (int) properties.tracker().followUpAfter().toDays();
    }

    @Transactional
    public PursuitView track(long jobId) {
        Job job = jobs.get(jobId);
        if (pursuits.findByJobId(jobId).isPresent()) {
            throw new ConflictException("Already tracking this role.");
        }
        Pursuit pursuit = pursuits.save(new Pursuit(job, PursuitStage.SAVED, clock.instant()));
        return view(pursuit, List.of());
    }

    @Transactional
    public PursuitView update(long pursuitId, UpdatePursuitRequest r) {
        Pursuit p = pursuits.findWithJobById(pursuitId).orElseThrow(() -> new NotFoundException("No tracked role with id " + pursuitId));
        Instant now = clock.instant();
        if (r.stage() != null) {
            p.setStage(r.stage(), today(), now);
        }
        LocalDate appliedOn = r.clearAppliedOn() ? null : r.appliedOn() != null ? r.appliedOn() : p.getAppliedOn();
        String resume = r.resumeVersion() != null ? blankToNull(r.resumeVersion()) : p.getResumeVersion();
        String notes = r.notes() != null ? blankToNull(r.notes()) : p.getNotes();
        p.update(appliedOn, resume, notes, now);
        return view(p, outreach.findByJobIdOrderByFirstSentAtAsc(p.getJob().getId()));
    }

    /** Stops tracking the role; the conversations about it stay with the people. */
    @Transactional
    public void untrack(long pursuitId) {
        pursuits.delete(pursuits.findById(pursuitId).orElseThrow(() -> new NotFoundException("No tracked role with id " + pursuitId)));
    }

    @Transactional(readOnly = true)
    public List<PursuitView> board() {
        List<Pursuit> all = pursuits.findAllByOrderByUpdatedAtDesc();
        Map<Long, List<Outreach>> byJob = outreach.findByJobIdIn(all.stream().map(p -> p.getJob().getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(o -> o.getJob().getId()));
        return all.stream().map(p -> view(p, byJob.getOrDefault(p.getJob().getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public RoleTracking forRole(long jobId) {
        jobs.get(jobId);
        List<Outreach> conversations = outreach.findByJobIdOrderByFirstSentAtAsc(jobId);
        PursuitView pursuit = pursuits.findByJobId(jobId).map(p -> view(p, conversations)).orElse(null);
        LocalDate today = today();
        return new RoleTracking(pursuit, conversations.stream().map(o -> OutreachView.of(o, today, true)).toList());
    }

    @Transactional(readOnly = true)
    public List<OutreachView> forContact(long contactId) {
        contacts.get(contactId);
        LocalDate today = today();
        return outreach.findByContactIdOrderByFirstSentAtDesc(contactId).stream()
                .map(o -> OutreachView.of(o, today, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OutreachView> due() {
        LocalDate today = today();
        return outreach.findDue(today).stream().map(o -> OutreachView.of(o, today, false)).toList();
    }

    /** The owner sent someone a message. Tracks the role if it isn't yet, and moves it to "reaching out". */
    @Transactional
    public OutreachView log(long contactId, Long jobId, OutreachChannel channel, String message, LocalDate sentOn) {
        Contact contact = contacts.get(contactId);
        Job job = jobId == null ? null : jobs.get(jobId);
        if (job != null && !Objects.equals(job.getCompany().getId(), contact.getCompany().getId())) {
            throw new BadRequestException(contact.getName() + " works at " + contact.getCompany().getName()
                    + ", not " + job.getCompany().getName() + ".");
        }
        LocalDate today = today();
        LocalDate day = sentOn == null ? today : sentOn;
        if (day.isAfter(today)) {
            throw new BadRequestException("A message can't be sent in the future.");
        }
        Instant now = clock.instant();
        Instant sentAt = day.equals(today) ? now : day.atTime(12, 0).atZone(zone()).toInstant();
        Outreach o = outreach.save(Outreach.sent(contact, job, channel == null ? OutreachChannel.LINKEDIN : channel,
                blankToNull(message), sentAt, day, followUpDays));
        if (job != null) {
            pursuitFor(job, now).advanceTo(PursuitStage.REACHING_OUT, now);
        }
        return OutreachView.of(o, today, true);
    }

    /** Records a follow-up, reply, referral or outcome. A referral moves the role to "referred". */
    @Transactional
    public OutreachView record(long outreachId, OutreachEventType type, String note) {
        Outreach o = outreach.findWithDetailsById(outreachId).orElseThrow(() -> new NotFoundException("No conversation with id " + outreachId));
        Instant now = clock.instant();
        o.record(type, note, now, today(), followUpDays);
        if (o.getJob() != null) {
            pursuits.findByJobId(o.getJob().getId()).ifPresent(p -> {
                if (type == OutreachEventType.REFERRED) {
                    p.advanceTo(PursuitStage.REFERRED, now);
                } else {
                    p.touch(now);
                }
            });
        }
        return OutreachView.of(o, today(), true);
    }

    /**
     * The owner submitted an application on {@code pageUrl}. Finds the role (or saves it as a hand-added
     * one), tracks it, and moves it to "applied" with today's date and the resume used.
     */
    @Transactional
    public PursuitView recordApplication(String pageUrl, String title, String company, String resumeVersion) {
        Job job = jobs.findForApplicationPage(pageUrl).orElseGet(() -> {
            if (title == null || title.isBlank() || company == null || company.isBlank()) {
                throw new BadRequestException("This page isn't a role the app knows. Say its title and company to save it.");
            }
            return manualJobs.add(pageUrl, title, company, null, null).job();
        });
        Instant now = clock.instant();
        Pursuit p = pursuitFor(job, now);
        p.setStage(PursuitStage.APPLIED, today(), now);
        if (resumeVersion != null && !resumeVersion.isBlank()) {
            p.update(p.getAppliedOn(), resumeVersion.strip(), p.getNotes(), now);
        }
        return view(p, outreach.findByJobIdOrderByFirstSentAtAsc(job.getId()));
    }

    @Transactional
    public void deleteOutreach(long outreachId) {
        outreach.delete(outreach.findById(outreachId).orElseThrow(() -> new NotFoundException("No conversation with id " + outreachId)));
    }

    private Pursuit pursuitFor(Job job, Instant now) {
        return pursuits.findByJobId(job.getId()).orElseGet(() -> pursuits.save(new Pursuit(job, PursuitStage.SAVED, now)));
    }

    private PursuitView view(Pursuit p, List<Outreach> conversations) {
        LocalDate today = today();
        List<Outreach> awaiting = conversations.stream().filter(o -> o.getStatus() == OutreachStatus.AWAITING_REPLY).toList();
        LocalDate next = awaiting.stream().map(Outreach::getFollowUpOn).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return new PursuitView(
                p.getId(),
                p.getStage(),
                RoleRef.of(p.getJob()),
                p.getAppliedOn(),
                p.getResumeVersion(),
                p.getNotes(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                (int) conversations.stream().map(o -> o.getContact().getId()).distinct().count(),
                awaiting.size(),
                next,
                next != null && !next.isAfter(today));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
