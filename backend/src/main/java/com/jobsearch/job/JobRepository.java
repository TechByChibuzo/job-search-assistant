package com.jobsearch.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    List<Job> findByCompanyId(Long companyId);

    List<Job> findByClosedAtIsNull();

    /** Open roles with no description that haven't been sought one recently, newest first. */
    @Query("""
            select j.id from Job j
            where j.closedAt is null and (j.description is null or j.description = '')
              and (j.descriptionCheckedAt is null or j.descriptionCheckedAt < :before)
            order by j.firstSeenAt desc, j.id desc""")
    List<Long> findUndescribed(Instant before, Limit limit);

    @Query("select j from Job j where j.closedAt is null and lower(j.company.name) = lower(:company) and lower(j.title) = lower(:title) order by j.id")
    List<Job> findOpenByCompanyAndTitle(String company, String title);

    Optional<Job> findFirstByUrl(String url);

    /** Roles whose own id appears in a page's link: an application page usually carries the posting's id. */
    @EntityGraph(attributePaths = "company")
    @Query("""
            select j from Job j
            where length(j.externalId) >= 6 and j.externalId not like 'manual:%' and j.externalId not like 'alert:%'
              and locate(lower(j.externalId), lower(:url)) > 0
            order by j.lastSeenAt desc""")
    List<Job> findByIdInLink(String url, Limit limit);

    @Query("select j.url from Job j where j.company.id = :companyId order by j.id desc")
    List<String> findUrlsByCompany(long companyId, Limit limit);

    /** Open roles first seen since {@code since}, not part of a first import, not yet scored: most recent first. */
    @EntityGraph(attributePaths = "company")
    @Query("""
            select j from Job j
            where j.closedAt is null and j.baseline = false and j.matchScore is null and j.firstSeenAt > :since
            order by coalesce(j.postedAt, j.firstSeenAt) desc, j.id desc""")
    List<Job> findUnscoredSince(Instant since, Limit limit);

    /** Loads the company with each job, so listing a page doesn't run one query per row. */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<Job> findAll(Specification<Job> spec, Pageable pageable);

    @EntityGraph(attributePaths = "company")
    Optional<Job> findWithCompanyById(Long id);

    @Query("select j.company.id as companyId, count(j) as openJobs from Job j where j.closedAt is null group by j.company.id")
    List<OpenJobCount> countOpenByCompany();

    /**
     * New roles per company: first seen after {@code since}, not part of a first import, still open. With
     * {@code needsSponsorship}, roles closed to someone who needs a visa don't count, and with {@code maxYears} roles
     * asking for more experience or with a senior title (see {@link JobFilter}).
     */
    @Query("""
            select j.company.id as companyId, j.company.name as companyName, count(j) as newJobs
            from Job j
            where j.closedAt is null and j.baseline = false and j.firstSeenAt > :since
              and (:needsSponsorship = false
                   or ((j.visaRestriction is null or j.visaRestriction = com.jobsearch.job.VisaRestriction.MAY_BE_RESTRICTED)
                       and j.company.noSponsorship = false))
              and (:maxYears is null or (j.seniorTitle = false and (j.minYears is null or j.minYears <= :maxYears)))
            group by j.company.id, j.company.name
            order by count(j) desc, j.company.name""")
    List<NewJobCount> countNewByCompany(Instant since, boolean needsSponsorship, Integer maxYears);

    interface NewJobCount {
        Long getCompanyId();

        String getCompanyName();

        Long getNewJobs();
    }

    interface OpenJobCount {
        Long getCompanyId();

        Long getOpenJobs();
    }
}
