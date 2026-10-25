package com.jobsearch.job;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    List<Job> findByCompanyId(Long companyId);

    /** Loads the company with each job, so listing a page doesn't run one query per row. */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<Job> findAll(Specification<Job> spec, Pageable pageable);

    @EntityGraph(attributePaths = "company")
    Optional<Job> findWithCompanyById(Long id);

    @Query("select j.company.id as companyId, count(j) as openJobs from Job j where j.closedAt is null group by j.company.id")
    List<OpenJobCount> countOpenByCompany();

    interface OpenJobCount {
        Long getCompanyId();

        Long getOpenJobs();
    }
}
