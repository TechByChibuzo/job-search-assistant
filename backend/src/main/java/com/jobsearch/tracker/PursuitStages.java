package com.jobsearch.tracker;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.job.TrackedJobs;

/** Tells the inbox which roles are tracked, at what stage, and which it should leave to the tracker. */
@Component
class PursuitStages implements TrackedJobs {

    /** Once applied (or closed), a role is followed in the tracker, not the inbox. */
    static final Set<PursuitStage> PAST_THE_INBOX =
            EnumSet.of(PursuitStage.APPLIED, PursuitStage.INTERVIEWING, PursuitStage.OFFER, PursuitStage.CLOSED);

    private final PursuitRepository pursuits;

    PursuitStages(PursuitRepository pursuits) {
        this.pursuits = pursuits;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> pastTheInbox() {
        return new HashSet<>(pursuits.findJobIdsByStageIn(PAST_THE_INBOX));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> stages(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return pursuits.findStagesByJobIdIn(jobIds).stream()
                .collect(Collectors.toMap(PursuitRepository.JobStage::getJobId, s -> s.getStage().name()));
    }
}
