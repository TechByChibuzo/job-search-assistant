package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkplaceTest {

    @ParameterizedTest
    @CsvSource({"Remote, REMOTE", "hybrid, HYBRID", "OnSite, ONSITE", "On-site, ONSITE", "In Office, ONSITE", "unspecified, UNKNOWN"})
    void readsFeedLabels(String label, Workplace expected) {
        assertThat(Workplace.fromLabel(label)).isEqualTo(expected);
    }

    @Test
    void fallsBackToTheLocationOnlyWhenUnknown() {
        assertThat(Workplace.UNKNOWN.orFromLocation("Remote - US")).isEqualTo(Workplace.REMOTE);
        assertThat(Workplace.HYBRID.orFromLocation("Remote - US")).isEqualTo(Workplace.HYBRID);
        assertThat(Workplace.UNKNOWN.orFromLocation(null)).isEqualTo(Workplace.UNKNOWN);
    }
}
