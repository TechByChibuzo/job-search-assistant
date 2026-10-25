package com.jobsearch.tracker;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.tracker.TrackerDtos.ApplicationRequest;
import com.jobsearch.tracker.TrackerDtos.LogOutreachRequest;
import com.jobsearch.tracker.TrackerDtos.OutreachView;
import com.jobsearch.tracker.TrackerDtos.PursuitView;
import com.jobsearch.tracker.TrackerDtos.RecordEventRequest;
import com.jobsearch.tracker.TrackerDtos.RoleTracking;
import com.jobsearch.tracker.TrackerDtos.TrackRequest;
import com.jobsearch.tracker.TrackerDtos.UpdatePursuitRequest;

@RestController
class TrackerController {

    private final TrackerService tracker;

    TrackerController(TrackerService tracker) {
        this.tracker = tracker;
    }

    /** Every tracked role, most recently active first, for the board. */
    @GetMapping("/api/pursuits")
    List<PursuitView> board() {
        return tracker.board();
    }

    @PostMapping("/api/pursuits")
    @ResponseStatus(HttpStatus.CREATED)
    PursuitView track(@Valid @RequestBody TrackRequest request) {
        return tracker.track(request.jobId());
    }

    @PatchMapping("/api/pursuits/{id}")
    PursuitView update(@PathVariable long id, @Valid @RequestBody UpdatePursuitRequest request) {
        return tracker.update(id, request);
    }

    @DeleteMapping("/api/pursuits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void untrack(@PathVariable long id) {
        tracker.untrack(id);
    }

    /** Tracking for one role: its stage and every conversation about it. */
    @GetMapping("/api/jobs/{jobId}/tracking")
    RoleTracking forRole(@PathVariable long jobId) {
        return tracker.forRole(jobId);
    }

    @GetMapping("/api/contacts/{contactId}/outreach")
    List<OutreachView> forContact(@PathVariable long contactId) {
        return tracker.forContact(contactId);
    }

    /** Conversations still waiting on a reply whose follow-up date has come. */
    @GetMapping("/api/outreach/due")
    List<OutreachView> due() {
        return tracker.due();
    }

    @PostMapping("/api/outreach")
    @ResponseStatus(HttpStatus.CREATED)
    OutreachView log(@Valid @RequestBody LogOutreachRequest r) {
        return tracker.log(r.contactId(), r.jobId(), r.channel(), r.message(), r.sentOn());
    }

    @PostMapping("/api/applications")
    @ResponseStatus(HttpStatus.CREATED)
    PursuitView recordApplication(@Valid @RequestBody ApplicationRequest r) {
        return tracker.recordApplication(r.url(), r.title(), r.company(), r.resumeVersion());
    }

    @PostMapping("/api/outreach/{id}/events")
    OutreachView record(@PathVariable long id, @Valid @RequestBody RecordEventRequest r) {
        return tracker.record(id, r.type(), r.note());
    }

    @DeleteMapping("/api/outreach/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        tracker.deleteOutreach(id);
    }
}
