package com.jobsearch.job.source;

import java.time.Instant;
import java.util.List;

/**
 * What a sync wants from a board. Sources that can search or filter on their side use it to ask for less;
 * the sync applies the same rules again afterwards, so a source that ignores it is still correct.
 *
 * @param now         the sync's clock reading, for feeds with relative dates ("Posted 3 Days Ago")
 * @param postedAfter roles posted before this are too old to import
 * @param searches    search phrases for the company; empty means every role
 * @param usOnly      only roles in the United States
 */
public record FetchQuery(Instant now, Instant postedAfter, List<String> searches, boolean usOnly) {

    public FetchQuery {
        searches = searches == null ? List.of() : List.copyOf(searches);
    }
}
