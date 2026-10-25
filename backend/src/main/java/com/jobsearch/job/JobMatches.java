package com.jobsearch.job;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.NotFoundException;

/** Reading and recording match scores, for the AI feature, without exposing the repository. */
@Component
public class JobMatches {

    private final JobRepository jobs;
    private final Clock clock;

    JobMatches(JobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public Job record(long jobId, int score, String summary, List<String> strengths, List<String> gaps, String model) {
        Job job = jobs.findWithCompanyById(jobId).orElseThrow(() -> new NotFoundException("No job with id " + jobId));
        job.recordMatch(score, summary, strengths, gaps, model, clock.instant());
        return job;
    }

    /** New roles not yet scored, most recent first, at most {@code limit}. */
    @Transactional(readOnly = true)
    public List<Job> unscoredNew(Duration window, int limit) {
        return limit <= 0 ? List.of() : jobs.findUnscoredSince(clock.instant().minus(window), Limit.of(limit));
    }
}
