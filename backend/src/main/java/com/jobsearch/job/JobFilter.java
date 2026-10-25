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
