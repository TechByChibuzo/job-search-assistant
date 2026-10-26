package com.jobsearch.job;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * What the tracker knows about roles, as the inbox needs it. Implemented by the tracker, which depends on jobs,
 * so jobs needn't depend on it.
 */
public interface TrackedJobs {

    /** Roles the owner is done with in the inbox: applied, interviewing, offered or closed. */
    Set<Long> pastTheInbox();

    /** The stage of each tracked role among {@code jobIds}, by job id. */
    Map<Long, String> stages(Collection<Long> jobIds);
}
