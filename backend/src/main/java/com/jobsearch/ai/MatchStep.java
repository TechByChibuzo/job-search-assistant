package com.jobsearch.ai;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jobsearch.company.SyncStatus;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.Job;
import com.jobsearch.job.JobMatches;
import com.jobsearch.job.SyncResult;
import com.jobsearch.job.SyncStep;

/**
 * After the daily sync, scores the newest unscored roles against the resume, up to a daily limit, so each
 * morning's new roles arrive ranked. Each score is one paid API call, hence the limit.
 */
@Component
class MatchStep implements SyncStep {

    static final String NAME = "Match scores";
    /** "New" for scoring: first seen in the last day and a half, so a late or missed run still catches up. */
    private static final Duration WINDOW = Duration.ofHours(36);
    private static final Logger log = LoggerFactory.getLogger(MatchStep.class);

    private final MatchService matches;
    private final JobMatches jobs;
    private final Claude claude;
    private final ResumeStore resumes;
    private final AppProperties.Ai config;

    MatchStep(MatchService matches, JobMatches jobs, Claude claude, ResumeStore resumes, AppProperties properties) {
        this.matches = matches;
        this.jobs = jobs;
        this.claude = claude;
        this.resumes = resumes;
        this.config = properties.ai();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return claude.configured() && config.dailyLimit() > 0 && resumes.current().isPresent();
    }

    @Override
    public SyncResult run() {
        int scored = 0;
        int failed = 0;
        String lastError = null;
        for (Job job : jobs.unscoredNew(WINDOW, config.dailyLimit())) {
            try {
                matches.score(job.getId());
                scored++;
            } catch (AiException e) {
                failed++;
                lastError = e.getMessage();
                log.warn("Couldn't score {} at {}: {}", job.getTitle(), job.getCompany().getName(), e.getMessage());
            }
        }
        SyncStatus status = scored == 0 && failed > 0 ? SyncStatus.FAILED : SyncStatus.OK;
        return new SyncResult(null, NAME, status, scored, 0, 0, failed, status == SyncStatus.FAILED ? lastError : null);
    }
}
