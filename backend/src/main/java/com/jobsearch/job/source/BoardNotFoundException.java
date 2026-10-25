package com.jobsearch.job.source;

import com.jobsearch.company.AtsType;

public class BoardNotFoundException extends RuntimeException {

    public BoardNotFoundException(AtsType ats, String board) {
        super("No " + ats + " board named '" + board + "'. The company may have moved to another ATS.");
    }
}
