package com.jobsearch.job;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.company.OpenJobCounts;

@Component
class JobOpenCounts implements OpenJobCounts {

    private final JobRepository jobs;

    JobOpenCounts(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> byCompanyId() {
        return jobs.countOpenByCompany().stream()
                .collect(Collectors.toMap(JobRepository.OpenJobCount::getCompanyId, JobRepository.OpenJobCount::getOpenJobs));
    }
}
