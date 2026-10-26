package com.jobsearch.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.jobsearch.job.source.Workplace;

/**
 * The inbox filters. All are optional and combine with AND.
 *
 * @param q             words that must all appear in the title or team
 * @param newSince      only new jobs: first seen after this instant, and not part of a first import
 * @param postedSince   hide jobs posted before this (or first seen before it, when the feed has no date)
 * @param includeClosed also list jobs that have left the feed
 * @param excludeIds    roles to leave out, such as those the owner has already applied to
 * @param needsSponsorship hide roles closed to someone who needs a visa: citizens only, clearance required or no
 *                      sponsorship, by the posting or because the owner marked the company
 * @param maxYears      hide roles asking for more years of experience than this, or with a senior title; null shows all
 * @param bestFitFirst  order by match score, highest first, unscored roles last; otherwise newest first
 */
record JobFilter(
        String q,
        Long companyId,
        Set<Workplace> workplaces,
        String location,
        Instant newSince,
        Instant postedSince,
        boolean includeClosed,
        Set<Long> excludeIds,
        boolean needsSponsorship,
        Integer maxYears,
        boolean bestFitFirst) {

    Specification<Job> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!includeClosed) {
                where.add(cb.isNull(root.get("closedAt")));
            }
            if (companyId != null) {
                where.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (workplaces != null && !workplaces.isEmpty()) {
                where.add(root.get("workplace").in(workplaces));
            }
            if (location != null && !location.isBlank()) {
                where.add(cb.like(cb.lower(root.get("location")), contains(location)));
            }
            if (newSince != null) {
                where.add(cb.greaterThan(root.get("firstSeenAt"), newSince));
                where.add(cb.isFalse(root.get("baseline")));
            }
            if (postedSince != null) {
                where.add(cb.greaterThan(cb.coalesce(root.<Instant>get("postedAt"), root.<Instant>get("firstSeenAt")), postedSince));
            }
            if (excludeIds != null && !excludeIds.isEmpty()) {
                where.add(cb.not(root.get("id").in(excludeIds)));
            }
            if (needsSponsorship) {
                where.add(cb.or(cb.isNull(root.get("visaRestriction")),
                        cb.equal(root.get("visaRestriction"), VisaRestriction.MAY_BE_RESTRICTED)));
                where.add(cb.isFalse(root.get("company").get("noSponsorship")));
            }
            if (maxYears != null) {
                where.add(cb.isFalse(root.get("seniorTitle")));
                where.add(cb.or(cb.isNull(root.get("minYears")), cb.le(root.get("minYears"), maxYears)));
            }
            if (q != null && !q.isBlank()) {
                for (String word : q.strip().split("\\s+")) {
                    String pattern = contains(word);
                    where.add(cb.or(
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(cb.coalesce(root.get("team"), "")), pattern)));
                }
            }
            // Newest first by the date a role was listed (see Job.getListedAt). Undated roles from a first
            // import have no known date and sort last. Set here rather than through Pageable because it orders
            // by an expression; skipped for the count query
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<Instant> undated = cb.<Instant>selectCase()
                        .when(cb.isTrue(root.get("baseline")), cb.literal(Instant.EPOCH))
                        .otherwise(root.<Instant>get("firstSeenAt"));
                var listed = cb.desc(cb.coalesce(root.<Instant>get("postedAt"), undated));
                if (bestFitFirst) {
                    query.orderBy(cb.desc(cb.coalesce(root.<Integer>get("matchScore"), -1)), listed, cb.desc(root.get("id")));
                } else {
                    query.orderBy(listed, cb.desc(root.get("id")));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
