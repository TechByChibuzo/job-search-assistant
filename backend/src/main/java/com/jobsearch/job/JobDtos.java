package com.jobsearch.job;

import java.time.Instant;
import java.util.List;

import com.jobsearch.job.source.Workplace;

final class JobDtos {

    private JobDtos() {}

    record JobSummary(
            Long id,
            Long companyId,
            String companyName,
            String title,
            String team,
            String location,
            Workplace workplace,
            Instant postedAt,
            Instant listedAt,
            String url,
            Instant firstSeenAt,
            boolean isNew,
            boolean open) {

        static JobSummary of(Job job, Instant newSince) {
            return new JobSummary(
                    job.getId(),
                    job.getCompany().getId(),
                    job.getCompany().getName(),
                    job.getTitle(),
                    job.getTeam(),
                    job.getLocation(),
                    job.getWorkplace(),
                    job.getPostedAt(),
                    job.getListedAt(),
                    job.getUrl(),
                    job.getFirstSeenAt(),
                    job.isNewSince(newSince),
                    job.isOpen());
        }
    }

    /**
     * The inbox header: how many roles are new since the window opened, and where; and the rules in force,
     * so the interface can describe them.
     */
    record Summary(Instant newSince, long newJobs, List<CompanyNew> byCompany, long maxAgeDays, boolean usOnly) {}

    record CompanyNew(Long companyId, String companyName, long newJobs) {}

    record JobDetail(JobSummary summary, String description, Instant lastSeenAt, Instant closedAt) {

        static JobDetail of(Job job, Instant newSince) {
            return new JobDetail(JobSummary.of(job, newSince), job.getDescription(), job.getLastSeenAt(), job.getClosedAt());
        }
    }
}
