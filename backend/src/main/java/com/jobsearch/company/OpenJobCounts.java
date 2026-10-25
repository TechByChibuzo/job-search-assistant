package com.jobsearch.company;

import java.util.Map;

/**
 * Open job counts per company, for the company list. The job feature implements this, so the company
 * feature doesn't depend on it.
 */
public interface OpenJobCounts {

    Map<Long, Long> byCompanyId();
}
