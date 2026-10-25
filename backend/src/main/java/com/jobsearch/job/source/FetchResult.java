package com.jobsearch.job.source;

import java.time.Instant;
import java.util.List;

/**
 * The roles a source returned, and how far back the list is complete.
 *
 * @param coveredSince null when the source returned every matching role; otherwise it stopped paging at
 *                     this cutoff, and roles posted earlier may still be open even though they are missing
 */
public record FetchResult(List<FetchedJob> jobs, Instant coveredSince) {

    public static FetchResult complete(List<FetchedJob> jobs) {
        return new FetchResult(jobs, null);
    }

    /** The source couldn't read the whole board and has no dates to say how far it got: close nothing. */
    public static FetchResult incomplete(List<FetchedJob> jobs) {
        return new FetchResult(jobs, Instant.MAX);
    }

    /** Whether a stored role missing from this result has really left the board. */
    public boolean covers(Instant postedAt) {
        return coveredSince == null || (postedAt != null && !postedAt.isBefore(coveredSince));
    }
}
