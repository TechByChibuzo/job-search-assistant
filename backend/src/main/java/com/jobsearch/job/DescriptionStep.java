package com.jobsearch.job;

import org.springframework.stereotype.Component;

import com.jobsearch.company.SyncStatus;
import com.jobsearch.job.source.Pacer;

/**
 * After the daily sync, fetches descriptions for new roles whose feed had none (Workday, Avature, roles saved
 * from LinkedIn), so the inbox can tell how many years each asks for before the owner opens it. Paced like the
 * sync, and capped per run; anything left is fetched on the next run or when the role is opened.
 */
@Component
class DescriptionStep implements SyncStep {

    static final String NAME = "Role descriptions";
    static final int PER_RUN = 300;

    private final JobDescriptions descriptions;
    private final Pacer pacer;

    DescriptionStep(JobDescriptions descriptions, Pacer pacer) {
        this.descriptions = descriptions;
        this.pacer = pacer;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public SyncResult run() {
        int found = descriptions.describeMissing(PER_RUN, pacer);
        return new SyncResult(null, NAME, SyncStatus.OK, 0, found, 0, 0, null);
    }
}
