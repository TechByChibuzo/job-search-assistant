package com.jobsearch.job.source;

/** A page that used to carry job data no longer does: the site changed and the source needs updating. */
public class FeedFormatException extends RuntimeException {

    public FeedFormatException(String message) {
        super(message);
    }
}
