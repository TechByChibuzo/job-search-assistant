package com.jobsearch.job;

import java.util.ArrayList;
import java.util.List;

import com.jobsearch.company.AtsType;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSource;

/** Stands in for an ATS: tests set what the feed returns, or what it throws, before each sync. */
class FakeJobSource implements JobSource {

    private List<FetchedJob> feed = new ArrayList<>();
    private RuntimeException failure;

    void returns(FetchedJob... jobs) {
        this.feed = List.of(jobs);
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
    public List<FetchedJob> fetch(String board) {
        if (failure != null) {
            throw failure;
        }
        return feed;
    }
}
