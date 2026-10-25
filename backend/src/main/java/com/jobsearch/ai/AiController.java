package com.jobsearch.ai;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.config.AppProperties;
import com.jobsearch.contact.ContactKind;
import com.jobsearch.job.Job;

@RestController
class AiController {

    private final MatchService matches;
    private final DraftService drafts;
    private final ResumeStore resumes;
    private final Claude claude;
    private final AppProperties.Ai config;

    AiController(MatchService matches, DraftService drafts, ResumeStore resumes, Claude claude, AppProperties properties) {
        this.matches = matches;
        this.drafts = drafts;
        this.resumes = resumes;
        this.claude = claude;
        this.config = properties.ai();
    }

    record ResumeInfo(String fileName, Instant modifiedAt) {}

    /** What the interface needs to know: whether Claude is set up, and which resume it will read. */
    record AiStatus(boolean configured, String model, int dailyLimit, ResumeInfo resume, String resumeHint) {}

    record MatchView(Long jobId, Integer score, String summary, List<String> strengths, List<String> gaps, String model,
            Instant matchedAt) {

        static MatchView of(Job j) {
            return new MatchView(j.getId(), j.getMatchScore(), j.getMatchSummary(), j.getMatchStrengths(), j.getMatchGaps(),
                    j.getMatchModel(), j.getMatchedAt());
        }
    }

    record DraftRequest(
            DraftService.Kind kind,
            String channel,
            Long jobId,
            Long contactId,
            @Size(max = 200) String personName,
            @Size(max = 300) String personTitle,
            ContactKind personKind,
            @Size(max = 10000) String previousMessage,
            Integer daysSince,
            @Size(max = 1000) String notes) {}

    @GetMapping("/api/ai")
    AiStatus status() {
        var resume = resumes.current();
        return new AiStatus(claude.configured(), claude.model(), config.dailyLimit(),
                resume.map(r -> new ResumeInfo(r.fileName(), r.modifiedAt())).orElse(null),
                resume.isPresent() ? null : resumes.describeFolder());
    }

    /** Scores one role now. Takes a few seconds: one Claude call. */
    @PostMapping("/api/jobs/{id}/match")
    MatchView match(@PathVariable long id) {
        return MatchView.of(matches.score(id));
    }

    @PostMapping("/api/drafts")
    DraftService.Draft draft(@Valid @RequestBody DraftRequest r) {
        return drafts.draft(new DraftService.Request(r.kind(), r.channel(), r.jobId(), r.contactId(), r.personName(),
                r.personTitle(), r.personKind(), r.previousMessage(), r.daysSince(), r.notes()));
    }

    /** Claude problems are explained, not hidden behind a 500: missing key, missing resume, refusals, outages. */
    @ExceptionHandler(AiException.class)
    ProblemDetail aiFailed(AiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
