package com.jobsearch.job.source;

import java.util.Optional;

import com.jobsearch.company.AtsType;

/** Reads the open roles on one board of one ATS. */
public interface JobSource {

    AtsType type();

    /**
     * @throws BoardNotFoundException when the board doesn't exist on this ATS
     * @throws org.springframework.web.client.RestClientException on a network or HTTP failure
     * @throws FeedFormatException when the response isn't in the shape this source understands
     */
    FetchResult fetch(String board, FetchQuery query);

    /**
     * The full description of one role, for feeds whose listings leave it out (Workday, Avature). Empty when this
     * source can't give one, or the role's link isn't on this board.
     *
     * @param externalId the role's id in the feed
     * @param url        the role's page, as the feed gave it
     */
    default Optional<String> describe(String board, String externalId, String url) {
        return Optional.empty();
    }
}
