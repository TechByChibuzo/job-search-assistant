package com.jobsearch.job.source;

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
}
