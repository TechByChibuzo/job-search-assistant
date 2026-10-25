package com.jobsearch.contact;

import java.time.Instant;
import java.util.Map;

/**
 * The latest conversation with each contact, for the people list. The tracker implements this, so the
 * contact feature doesn't depend on it.
 */
public interface ContactActivity {

    Map<Long, Latest> byContactId();

    /** @param status the most recent conversation's status, such as AWAITING_REPLY */
    record Latest(String status, Instant lastContactedAt, int conversations) {}
}
