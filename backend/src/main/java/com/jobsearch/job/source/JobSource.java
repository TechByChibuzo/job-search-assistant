package com.jobsearch.job.source;

import java.util.List;

import com.jobsearch.company.AtsType;

/** Reads the open roles on one board of one ATS. */
public interface JobSource {

    AtsType type();

    /**
     * @throws BoardNotFoundException when the board doesn't exist on this ATS
     * @throws org.springframework.web.client.RestClientException on any other failure
     */
    List<FetchedJob> fetch(String board);
}
