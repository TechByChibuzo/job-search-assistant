package com.jobsearch.job;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.jobsearch.company.DetectedBoard;

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
            boolean open,
            boolean manual,
            Integer matchScore,
            String matchSummary) {

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
                    job.isOpen(),
                    job.isManual(),
                    job.getMatchScore(),
                    job.getMatchSummary());
        }
    }

    /** A role pasted by link. Company is a name: an existing company with that name is reused. */
    record AddJobRequest(
            @NotBlank @Size(max = 2000) String url,
            @NotBlank @Size(max = 300) String title,
            @NotBlank @Size(max = 200) String company,
            @Size(max = 300) String location,
            Workplace workplace) {}

    /**
     * @param alreadySaved   the link was saved before; this is that role
     * @param trackableBoard set when the link is on a board the app can sync, to offer tracking the company
     */
    record AddJobResponse(JobSummary job, boolean alreadySaved, DetectedBoard trackableBoard) {}

    record UpdateJobRequest(Boolean closed) {}

    /**
     * The inbox header: how many roles are new since the window opened, and where; and the rules in force,
     * so the interface can describe them.
     */
    record Summary(Instant newSince, long newJobs, List<CompanyNew> byCompany, long maxAgeDays, boolean usOnly,
            long followUpDays) {}

    record CompanyNew(Long companyId, String companyName, long newJobs) {}

    record JobDetail(
            JobSummary summary,
            String description,
            Instant lastSeenAt,
            Instant closedAt,
            List<String> matchStrengths,
            List<String> matchGaps,
            Instant matchedAt) {

        static JobDetail of(Job job, Instant newSince) {
            return new JobDetail(JobSummary.of(job, newSince), job.getDescription(), job.getLastSeenAt(), job.getClosedAt(),
                    job.getMatchStrengths(), job.getMatchGaps(), job.getMatchedAt());
        }
    }
}
