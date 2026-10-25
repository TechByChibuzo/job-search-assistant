package com.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AlertLinksTest {

    @ParameterizedTest
    @CsvSource({
        "'https://jobs.example.com/job/123?utm_source=x&trackingId=y#apply', https://jobs.example.com/job/123",
        "'https://boards.greenhouse.io/acme/jobs/4567?gh_jid=4567&src=email', https://boards.greenhouse.io/acme/jobs/4567?gh_jid=4567",
    })
    void dropsTrackingButKeepsWhatIdentifiesTheRole(String link, String canonical) {
        assertThat(AlertLinks.canonical(link)).isEqualTo(canonical);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://jobs.careers.microsoft.com/global/en/job/1789012/Software-Engineer",
        "https://www.metacareers.com/jobs/1234567890/",
        "https://jobs.apple.com/en-us/details/200512345/software-engineer",
        "https://boards.greenhouse.io/acme/jobs/4567",
    })
    void recognisesJobPostings(String link) {
        assertThat(AlertLinks.looksLikeJobPosting(link)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://jobs.careers.microsoft.com/global/en/search?q=software",
        "https://careers.microsoft.com/profile/alerts",
        "https://example.com/about",
        "mailto:jobs@example.com",
    })
    void ignoresEverythingElse(String link) {
        assertThat(AlertLinks.looksLikeJobPosting(link)).isFalse();
    }
}
