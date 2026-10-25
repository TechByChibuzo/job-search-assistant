package com.jobsearch.job;

import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.NotFoundException;

/** Finds jobs for other features, such as the tracker, without exposing the repository. */
@Component
public class JobLookup {

    private final JobRepository jobs;

    JobLookup(JobRepository jobs) {
        this.jobs = jobs;
    }

    /**
     * The saved role an application page belongs to: the same link once "/apply" and the query are
     * stripped, or a role whose own id appears in the page's link.
     */
    @Transactional(readOnly = true)
    public Optional<Job> findForApplicationPage(String pageUrl) {
        String base = posting(pageUrl);
        return jobs.findFirstByUrl(base)
                .or(() -> jobs.findFirstByUrl(base + "/"))
                .or(() -> jobs.findByIdInLink(pageUrl, Limit.of(1)).stream().findFirst());
    }

    /** "https://jobs.lever.co/acme/123/apply?src=x#form" → "https://jobs.lever.co/acme/123". */
    static String posting(String pageUrl) {
        return pageUrl.strip()
                .replaceFirst("[?#].*$", "")
                .replaceFirst("/+$", "")
                .replaceFirst("(?i)/(apply|application)$", "");
    }

    @Transactional(readOnly = true)
    public Job get(long id) {
        return jobs.findWithCompanyById(id).orElseThrow(() -> new NotFoundException("No job with id " + id));
    }
}
