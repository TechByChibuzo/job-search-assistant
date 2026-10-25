package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jobsearch.contact.ContactKind;

class DraftServiceTest {

    @Test
    void describesAnAlumWithoutAJobAndCarriesTheOwnersNotes() {
        var r = new DraftService.Request(DraftService.Kind.CONNECTION_NOTE, "LINKEDIN", null, null, "Ada", "Staff Engineer",
                ContactKind.ALUMNI, null, null, "We met at AFROTECH");

        String request = DraftService.request(DraftService.Kind.CONNECTION_NOTE, r, null, null, "Ada");

        assertThat(request).contains("LinkedIn connection note", "Name: Ada", "Title: Staff Engineer",
                "alumni: went to the same school", "We met at AFROTECH");
        assertThat(request).doesNotContain("<job_posting>", "<earlier_message");
    }

    @Test
    void aFollowUpIncludesTheEarlierMessage() {
        var r = new DraftService.Request(DraftService.Kind.FOLLOW_UP, "EMAIL", null, null, "Ada", null, ContactKind.RECRUITER,
                "Hi Ada, I'm interested in the SWE II role.", 6, null);

        String request = DraftService.request(DraftService.Kind.FOLLOW_UP, r, null, null, "Ada");

        assertThat(request).contains("follow-up message sent by email", "<earlier_message sent_days_ago=\"6\">",
                "interested in the SWE II role", "recruiter");
    }
}
