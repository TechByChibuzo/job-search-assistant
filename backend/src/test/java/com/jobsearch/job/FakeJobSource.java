package com.jobsearch.job;

import java.util.ArrayList;
import java.util.List;

import com.jobsearch.company.AtsType;
import com.jobsearch.job.source.FetchQuery;
import com.jobsearch.job.source.FetchResult;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSource;

/** Stands in for an ATS: tests set what the feed returns, or what it throws, before each sync. */
class FakeJobSource implements JobSource {

    private FetchResult feed = FetchResult.complete(new ArrayList<>());
    private RuntimeException failure;
    FetchQuery lastQuery;

    void returns(FetchedJob... jobs) {
        returns(FetchResult.complete(List.of(jobs)));
    }

    void returns(FetchResult result) {
        this.feed = result;
        this.failure = null;
    }

    void fails(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public AtsType type() {
        return AtsType.GREENHOUSE;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        this.lastQuery = query;
        if (failure != null) {
            throw failure;
        }
        return feed;
    }
}
