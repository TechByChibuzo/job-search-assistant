package com.jobsearch.alerts;

import java.util.List;

/** Reads the roles out of one kind of alert email. */
interface AlertParser {

    boolean handles(AlertEmail email);

    /** The roles in the email, or empty when it has none this parser can read. */
    List<AlertJob> parse(AlertEmail email);
}
