package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PagingTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    private final List<String> requests = new ArrayList<>();

    @Test
    void stopsAtTheFirstPageWhollyOlderThanTheCutoff() {
        List<List<FetchedJob>> pages = List.of(
                List.of(job("1", 1), job("2", 3)),
                // Roughly sorted: one old role mid-page doesn't stop paging
                List.of(job("3", 40), job("4", 20)),
                List.of(job("5", 31), job("6", 45)),
                List.of(job("7", 2)));

        FetchResult result = Paging.newestFirst(query(), Pacer.immediate(10), (search, page) -> {
            requests.add(search + ":" + page);
            return pages.get(page);
        });

        assertThat(result.jobs()).extracting(FetchedJob::externalId).containsExactly("1", "2", "4");
        assertThat(requests).containsExactly("null:0", "null:1", "null:2");
        assertThat(result.coveredSince()).isEqualTo(CUTOFF);
    }

    @Test
    void readingToTheEndCoversEverything() {
        FetchResult result = Paging.newestFirst(query(), Pacer.immediate(10),
                (search, page) -> page == 0 ? List.of(job("1", 1)) : List.of());

        assertThat(result.coveredSince()).isNull();
        assertThat(result.covers(NOW.minus(Duration.ofDays(400)))).isTrue();
    }

    @Test
    void runsEachSearchPhraseAndMergesDuplicates() {
        var query = new FetchQuery(NOW, CUTOFF, List.of("backend", "platform"), true);

        FetchResult result = Paging.newestFirst(query, Pacer.immediate(10), (search, page) -> {
            requests.add(search + ":" + page);
            if (page > 0) {
                return List.of();
            }
            return search.equals("backend") ? List.of(job("1", 1), job("2", 1)) : List.of(job("2", 1), job("3", 1));
        });

        assertThat(result.jobs()).extracting(FetchedJob::externalId).containsExactly("1", "2", "3");
        assertThat(requests).containsExactly("backend:0", "backend:1", "platform:0", "platform:1");
    }

    @Test
    void thePageLimitCountsAsStoppingEarly() {
        FetchResult result = Paging.newestFirst(query(), Pacer.immediate(2), (search, page) -> List.of(job(page + "", 1)));

        assertThat(result.jobs()).hasSize(2);
        assertThat(result.coveredSince()).isEqualTo(CUTOFF);
        assertThat(result.covers(NOW.minus(Duration.ofDays(60)))).isFalse();
        assertThat(result.covers(null)).isFalse();
    }

    private static FetchQuery query() {
        return new FetchQuery(NOW, CUTOFF, List.of(), true);
    }

    private static FetchedJob job(String id, int daysAgo) {
        return new FetchedJob(id, "Engineer", null, null, null, null, NOW.minus(Duration.ofDays(daysAgo)), "u", null);
    }
}
