package com.jobsearch.ai;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.contact.Contact;
import com.jobsearch.contact.ContactKind;
import com.jobsearch.contact.ContactService;
import com.jobsearch.job.Job;
import com.jobsearch.job.JobLookup;

/** Drafts outreach for the owner to edit and send themselves. Nothing is sent or stored here. */
@Service
public class DraftService {

    public enum Kind {
        CONNECTION_NOTE,
        MESSAGE,
        FOLLOW_UP
    }

    /** LinkedIn's limit for a connection request note. */
    public static final int CONNECTION_NOTE_LIMIT = 300;

    /**
     * Who the message is to: a saved contact, or a person typed into the form who isn't saved yet.
     *
     * @param previousMessage for a follow-up: what the owner sent before
     * @param daysSince       for a follow-up: how long ago that was
     * @param notes           anything the owner wants mentioned ("we met at AFROTECH")
     */
    public record Request(
            Kind kind,
            String channel,
            Long jobId,
            Long contactId,
            String personName,
            String personTitle,
            ContactKind personKind,
            String previousMessage,
            Integer daysSince,
            String notes) {}

    public record Draft(String message, String subject, int characters, Integer limit) {}

    private final Claude claude;
    private final ResumeStore resumes;
    private final JobLookup jobs;
    private final ContactService contacts;

    DraftService(Claude claude, ResumeStore resumes, JobLookup jobs, ContactService contacts) {
        this.claude = claude;
        this.resumes = resumes;
        this.jobs = jobs;
        this.contacts = contacts;
    }

    public Draft draft(Request r) {
        Kind kind = r.kind() == null ? Kind.MESSAGE : r.kind();
        Resume resume = resumes.current().orElseThrow(() -> new AiException(resumes.describeFolder()));
        Job job = r.jobId() == null ? null : jobs.get(r.jobId());
        Contact contact = r.contactId() == null ? null : contacts.get(r.contactId());
        String name = contact != null ? contact.getName() : r.personName();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Say who the message is to.");
        }
        String request = request(kind, r, job, contact, name.strip());
        JsonNode answer = claude.ask(Prompts.DRAFT, resume, request, Prompts.DRAFT_SCHEMA, Claude.Effort.MEDIUM);
        String message = answer.path("message").asText("").strip();
        String subject = "EMAIL".equalsIgnoreCase(r.channel()) ? answer.path("subject").asText("").strip() : "";
        Integer limit = kind == Kind.CONNECTION_NOTE ? CONNECTION_NOTE_LIMIT : null;
        return new Draft(message, subject, message.length(), limit);
    }

    static String request(Kind kind, Request r, Job job, Contact contact, String name) {
        StringBuilder s = new StringBuilder();
        s.append("Write a ").append(switch (kind) {
            case CONNECTION_NOTE -> "LinkedIn connection note";
            case MESSAGE -> "message";
            case FOLLOW_UP -> "follow-up message";
        }).append(" sent by ").append("EMAIL".equalsIgnoreCase(r.channel()) ? "email" : "LinkedIn").append(".\n\n");

        s.append("<person>\nName: ").append(name).append('\n');
        String title = contact != null ? contact.getTitle() : r.personTitle();
        ContactKind personKind = contact != null ? contact.getKind() : r.personKind();
        if (title != null && !title.isBlank()) {
            s.append("Title: ").append(title.strip()).append('\n');
        }
        if (personKind != null) {
            s.append("Relationship: ").append(switch (personKind) {
                case RECRUITER -> "recruiter at the company";
                case HIRING_MANAGER -> "hiring manager";
                case ENGINEER -> "engineer, possibly on the team";
                case ALUMNI -> "alumni: went to the same school as the candidate";
                case OTHER -> "works at the company";
            }).append('\n');
        }
        if (contact != null) {
            s.append("Company: ").append(contact.getCompany().getName()).append('\n');
            if (contact.getNotes() != null) {
                s.append("Owner's notes about them: ").append(contact.getNotes()).append('\n');
            }
        }
        s.append("</person>\n\n");

        if (job != null) {
            s.append(MatchService.posting(job).replace("\n\nAssess the candidate's fit for this role.", "")).append("\n\n");
        }
        if (kind == Kind.FOLLOW_UP && r.previousMessage() != null && !r.previousMessage().isBlank()) {
            s.append("<earlier_message sent_days_ago=\"").append(r.daysSince() == null ? "a few" : r.daysSince()).append("\">\n")
                    .append(r.previousMessage().strip()).append("\n</earlier_message>\n\n");
        }
        if (r.notes() != null && !r.notes().isBlank()) {
            s.append("The candidate also wants to mention: ").append(r.notes().strip()).append('\n');
        }
        return s.toString();
    }
}
