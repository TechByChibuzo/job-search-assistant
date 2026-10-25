package com.jobsearch.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.Links;
import com.jobsearch.common.NotFoundException;
import com.jobsearch.company.AtsDetector;
import com.jobsearch.company.AtsType;
import com.jobsearch.company.Company;
import com.jobsearch.company.CompanyService;
import com.jobsearch.company.DetectedBoard;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.Region;
import com.jobsearch.job.source.Workplace;

/**
 * Roles added by pasting a link: from LinkedIn, from a company whose site can't be read, or anything else.
 * They join the inbox like any other role, but no sync ever closes them.
 */
@Service
public class ManualJobService {

    private final JobRepository jobs;
    private final CompanyService companies;
    private final AtsDetector detector;
    private final Clock clock;

    ManualJobService(JobRepository jobs, CompanyService companies, AtsDetector detector, Clock clock) {
        this.jobs = jobs;
        this.companies = companies;
        this.detector = detector;
        this.clock = clock;
    }

    public record AddedJob(Job job, boolean alreadySaved, DetectedBoard trackableBoard) {}

    /**
     * Saves the role, or returns the one already saved with that link. When the link is on a board the app
     * can sync, that board is returned too, so the interface can offer to track the whole company.
     */
    @Transactional
    public AddedJob add(String url, String title, String companyName, String location, Workplace workplace) {
        String link = checkLink(url);
        Optional<Job> existing = jobs.findFirstByUrl(link);
        if (existing.isPresent()) {
            return new AddedJob(existing.get(), true, null);
        }
        Instant now = clock.instant();
        Company company = companies.findOrCreateManual(companyName);
        FetchedJob fetched = new FetchedJob("manual:" + link, title, null, location,
                workplace == null ? Workplace.UNKNOWN.orFromLocation(location) : workplace,
                Region.fromLocation(location), null, link, null);
        Job job = jobs.save(Job.manual(company, fetched, now));
        DetectedBoard board = detector.detect(link)
                .filter(b -> b.ats().isSyncable() && company.getAts() == AtsType.MANUAL)
                .orElse(null);
        return new AddedJob(job, false, board);
    }

    @Transactional
    public Job setClosed(long jobId, boolean closed) {
        Job job = jobs.findWithCompanyById(jobId).orElseThrow(() -> new NotFoundException("No job with id " + jobId));
        if (!job.isManual()) {
            throw new BadRequestException("Only roles added by hand can be closed here; others follow their company's board.");
        }
        job.setClosedByOwner(closed, clock.instant());
        return job;
    }

    private static String checkLink(String url) {
        return Links.requireHttp(url, "Paste the full link to the posting, starting with https://");
    }
}
