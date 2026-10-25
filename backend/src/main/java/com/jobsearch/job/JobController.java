package com.jobsearch.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.common.NotFoundException;
import com.jobsearch.common.PageResponse;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.JobDtos.AddJobRequest;
import com.jobsearch.job.JobDtos.AddJobResponse;
import com.jobsearch.job.JobDtos.CompanyNew;
import com.jobsearch.job.JobDtos.JobDetail;
import com.jobsearch.job.JobDtos.JobSummary;
import com.jobsearch.job.JobDtos.Summary;
import com.jobsearch.job.JobDtos.UpdateJobRequest;
import com.jobsearch.job.source.Workplace;

@RestController
@RequestMapping("/api/jobs")
class JobController {

    private final JobRepository jobs;
    private final ManualJobService manualJobs;
    private final AppProperties properties;
    private final Clock clock;

    JobController(JobRepository jobs, ManualJobService manualJobs, AppProperties properties, Clock clock) {
        this.jobs = jobs;
        this.manualJobs = manualJobs;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The inbox: open roles, most recently posted first, posted within the max age unless {@code includeOlder}.
     * {@code newOnly} keeps those first seen in the "new" window.
     */
    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<JobSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Set<Workplace> workplace,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "false") boolean newOnly,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "false") boolean includeOlder,
            @RequestParam(defaultValue = "false") boolean needsSponsorship,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        Instant newSince = newSince();
        Instant postedSince = includeOlder ? null : clock.instant().minus(properties.jobs().maxAge());
        var filter = new JobFilter(q, companyId, workplace, location, newOnly ? newSince : null, postedSince, includeClosed,
                needsSponsorship, "fit".equalsIgnoreCase(sort));
        var results = jobs.findAll(filter.toSpecification(), PageRequest.of(page, size));
        return PageResponse.of(results, job -> JobSummary.of(job, newSince));
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    Summary summary(@RequestParam(defaultValue = "false") boolean needsSponsorship) {
        Instant since = newSince();
        var byCompany = jobs.countNewByCompany(since, needsSponsorship).stream()
                .map(c -> new CompanyNew(c.getCompanyId(), c.getCompanyName(), c.getNewJobs()))
                .toList();
        var rules = properties.jobs();
        return new Summary(since, byCompany.stream().mapToLong(CompanyNew::newJobs).sum(), byCompany,
                rules.maxAge().toDays(), rules.usOnly(), properties.tracker().followUpAfter().toDays());
    }

    @PostMapping
    @Transactional
    ResponseEntity<AddJobResponse> add(@Valid @RequestBody AddJobRequest request) {
        var added = manualJobs.add(request.url(), request.title(), request.company(), request.location(), request.workplace());
        var body = new AddJobResponse(JobSummary.of(added.job(), newSince()), added.alreadySaved(), added.trackableBoard());
        return ResponseEntity.status(added.alreadySaved() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    /** Closes or reopens a role added by hand. */
    @PatchMapping("/{id}")
    @Transactional
    JobSummary update(@PathVariable long id, @RequestBody UpdateJobRequest request) {
        Job job = request.closed() == null
                ? jobs.findWithCompanyById(id).orElseThrow(() -> new NotFoundException("No job with id " + id))
                : manualJobs.setClosed(id, request.closed());
        return JobSummary.of(job, newSince());
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
