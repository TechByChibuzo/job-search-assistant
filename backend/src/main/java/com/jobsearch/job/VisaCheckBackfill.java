package com.jobsearch.job;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On start, rereads the open roles' descriptions for visa restrictions, so roles saved before the check existed,
 * or before its rules last changed, are classified without waiting for a sync. Only changed roles are written.
 */
@Component
class VisaCheckBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VisaCheckBackfill.class);

    private final JobRepository jobs;

    VisaCheckBackfill(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long changed = 0;
        for (Job job : jobs.findByClosedAtIsNullAndDescriptionIsNotNull()) {
            VisaRestriction before = job.getVisaRestriction();
            String evidence = job.getVisaEvidence();
            job.checkVisa();
            if (before != job.getVisaRestriction() || !Objects.equals(evidence, job.getVisaEvidence())) {
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Visa check: {} open roles reclassified", changed);
        }
    }
}
