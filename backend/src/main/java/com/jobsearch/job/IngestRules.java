package com.jobsearch.job;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.jobsearch.job.source.Region;

/**
 * Which roles the app keeps, applied the same way to every source: in the US (when that's on), matching one
 * of the company's search phrases (when it has any), and, for roles not seen before, posted recently enough.
 *
 * @param postedAfter roles posted before this aren't imported
 * @param searches    a role matches a phrase when its title and team contain every word of it, so
 *                    "software engineer" matches "Software Development Engineer II"
 */
record IngestRules(boolean usOnly, Instant postedAfter, List<String> searches) {

    boolean wanted(String title, String team, Region region) {
        if (usOnly && region == Region.NON_US) {
            return false;
        }
        return searches.isEmpty() || matchesAny(title, team);
    }

    boolean tooOld(Instant postedAt) {
        return postedAt != null && postedAt.isBefore(postedAfter);
    }

    private boolean matchesAny(String title, String team) {
        String text = ((title == null ? "" : title) + " " + (team == null ? "" : team)).toLowerCase(Locale.ROOT);
        for (String phrase : searches) {
            boolean all = true;
            for (String word : phrase.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!text.contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }
}
