package com.jobsearch.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.common.NotFoundException;
import com.jobsearch.common.PageResponse;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.JobDtos.JobDetail;
import com.jobsearch.job.JobDtos.JobSummary;
import com.jobsearch.job.source.Workplace;

@RestController
@RequestMapping("/api/jobs")
class JobController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("firstSeenAt"), Sort.Order.desc("id"));

    private final JobRepository jobs;
    private final AppProperties properties;
    private final Clock clock;

    JobController(JobRepository jobs, AppProperties properties, Clock clock) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    /** The inbox: open roles, newest first. {@code newOnly} keeps those first seen in the "new" window. */
    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<JobSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Set<Workplace> workplace,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "false") boolean newOnly,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        Instant newSince = newSince();
        var filter = new JobFilter(q, companyId, workplace, location, newOnly ? newSince : null, includeClosed);
        var results = jobs.findAll(filter.toSpecification(), PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.of(results, job -> JobSummary.of(job, newSince));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    JobDetail get(@PathVariable long id) {
        Job job = jobs.findWithCompanyById(id).orElseThrow(() -> new NotFoundException("No job with id " + id));
        return JobDetail.of(job, newSince());
    }

    private Instant newSince() {
        return clock.instant().minus(properties.jobs().newWindow());
    }
}
