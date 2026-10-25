package com.jobsearch.job.source;

import java.time.Instant;

/** One open role as read from an ATS feed, already normalised to the app's vocabulary. */
public record FetchedJob(
        String externalId,
        String title,
        String team,
        String location,
        Workplace workplace,
        Region region,
        Instant postedAt,
        String url,
        String description) {

    /** Feeds pad fields with stray whitespace ("United States "), and blank means absent. */
    public FetchedJob {
        title = clean(title);
        team = clean(team);
        location = clean(location);
        workplace = workplace == null ? Workplace.UNKNOWN : workplace;
        region = region == null || region == Region.UNKNOWN ? Region.fromLocation(location) : region;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
