package com.jobsearch.ai;

import java.util.Map;

/** Lets tests in other packages tell the resume request apart from the cover letter request. */
public final class TailorServiceTestAccess {

    private TailorServiceTestAccess() {}

    public static Map<String, Object> resumeSchema() {
        return TailorService.RESUME_SCHEMA;
    }
}
