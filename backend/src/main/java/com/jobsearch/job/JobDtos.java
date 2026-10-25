package com.jobsearch.job;

import java.time.Instant;

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
                    job.getUrl(),
                    job.getFirstSeenAt(),
                    job.getFirstSeenAt().isAfter(newSince),
                    job.isOpen());
        }
    }

    record JobDetail(JobSummary summary, String description, Instant lastSeenAt, Instant closedAt) {

        static JobDetail of(Job job, Instant newSince) {
            return new JobDetail(JobSummary.of(job, newSince), job.getDescription(), job.getLastSeenAt(), job.getClosedAt());
        }
    }
}
