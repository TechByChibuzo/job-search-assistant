package com.jobsearch.job.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Pages through a newest-first search feed until it runs out or a whole page is older than the cutoff.
 * A whole page, not one role, because "sort by date" on these sites is only roughly ordered.
 */
final class Paging {

    private Paging() {}

    /**
     * @param fetchPage (search phrase or null, page index from 0) → the roles on that page, empty when done
     */
    static FetchResult newestFirst(FetchQuery query, Pacer pacer, BiFunction<String, Integer, List<FetchedJob>> fetchPage) {
        List<String> searches = query.searches().isEmpty() ? Collections.singletonList(null) : query.searches();
        Map<String, FetchedJob> jobs = new LinkedHashMap<>();
        boolean stoppedAtCutoff = false;
        for (String search : searches) {
            for (int page = 0; page < pacer.maxPages(); page++) {
                if (page > 0 || !jobs.isEmpty()) {
                    pacer.beforeNextPage();
                }
                List<FetchedJob> batch = fetchPage.apply(search, page);
                if (batch.isEmpty()) {
                    break;
                }
                List<FetchedJob> recent = new ArrayList<>();
                for (FetchedJob job : batch) {
                    if (job.postedAt() == null || !job.postedAt().isBefore(query.postedAfter())) {
                        recent.add(job);
                    }
                }
                recent.forEach(j -> jobs.putIfAbsent(j.externalId(), j));
                if (recent.isEmpty()) {
                    stoppedAtCutoff = true;
                    break;
                }
                if (page == pacer.maxPages() - 1) {
                    stoppedAtCutoff = true;
                }
            }
        }
        Instant coveredSince = stoppedAtCutoff ? query.postedAfter() : null;
        return new FetchResult(List.copyOf(jobs.values()), coveredSince);
    }
}
