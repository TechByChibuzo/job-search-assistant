package com.jobsearch.job;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.NotFoundException;

/** Finds jobs for other features, such as the tracker, without exposing the repository. */
@Component
public class JobLookup {

    private final JobRepository jobs;

    JobLookup(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Job get(long id) {
        return jobs.findWithCompanyById(id).orElseThrow(() -> new NotFoundException("No job with id " + id));
    }
}
