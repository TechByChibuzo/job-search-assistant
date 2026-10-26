package com.jobsearch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On start, rereads every open role's title and description for visa restrictions and seniority, so roles saved
 * before a check existed, or before its rules last changed, are classified without waiting for a sync. Only
 * changed roles are written.
 */
@Component
class PostingChecksBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostingChecksBackfill.class);

    private final JobRepository jobs;

    PostingChecksBackfill(JobRepository jobs) {
        this.jobs = jobs;
    }

    private record Reading(VisaRestriction visa, String visaEvidence, Integer minYears, String yearsEvidence, boolean senior) {

        static Reading of(Job j) {
            return new Reading(j.getVisaRestriction(), j.getVisaEvidence(), j.getMinYears(), j.getYearsEvidence(), j.isSeniorTitle());
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long changed = 0;
        for (Job job : jobs.findByClosedAtIsNull()) {
            Reading before = Reading.of(job);
            job.readPosting();
            if (!before.equals(Reading.of(job))) {
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Posting checks: {} open roles reclassified", changed);
        }
    }
}
