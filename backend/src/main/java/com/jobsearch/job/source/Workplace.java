package com.jobsearch.job.source;

import java.util.Locale;

public enum Workplace {
    REMOTE,
    HYBRID,
    ONSITE,
    UNKNOWN;

    /** Reads the labels the ATS feeds use: "Remote", "hybrid", "OnSite", "On-site" and so on. */
    public static Workplace fromLabel(String label) {
        if (label == null) {
            return UNKNOWN;
        }
        return switch (label.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")) {
            case "remote" -> REMOTE;
            case "hybrid" -> HYBRID;
            case "onsite", "inoffice", "office" -> ONSITE;
            default -> UNKNOWN;
        };
    }

    /** Falls back to the location text, which often says "Remote" when the feed has no workplace field. */
    public Workplace orFromLocation(String location) {
        if (this == UNKNOWN && location != null && location.toLowerCase(Locale.ROOT).contains("remote")) {
            return REMOTE;
        }
        return this;
    }
}
