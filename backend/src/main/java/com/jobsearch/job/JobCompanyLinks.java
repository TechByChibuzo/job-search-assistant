package com.jobsearch.job;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.company.CompanyLinks;

@Component
class JobCompanyLinks implements CompanyLinks {

    private final JobRepository jobs;

    JobCompanyLinks(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> sample(long companyId) {
        return jobs.findUrlsByCompany(companyId, Limit.of(5));
    }
}
