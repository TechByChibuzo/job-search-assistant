package com.jobsearch.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.jobsearch.job.source.Workplace;

/**
 * The inbox filters. All are optional and combine with AND.
 *
 * @param q          words that must all appear in the title or team
 * @param newSince   only jobs first seen after this instant
 * @param includeClosed also list jobs that have left the feed
 */
record JobFilter(
        String q,
        Long companyId,
        Set<Workplace> workplaces,
        String location,
        Instant newSince,
        boolean includeClosed) {

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
            }
            if (q != null && !q.isBlank()) {
                for (String word : q.strip().split("\\s+")) {
                    String pattern = contains(word);
                    where.add(cb.or(
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(cb.coalesce(root.get("team"), "")), pattern)));
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
