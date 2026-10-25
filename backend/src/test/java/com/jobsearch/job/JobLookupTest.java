package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobLookupTest {

    @ParameterizedTest
    @CsvSource({
        "https://jobs.lever.co/acme/123/apply?src=x#form, https://jobs.lever.co/acme/123",
        "https://jobs.ashbyhq.com/openai/abc/application, https://jobs.ashbyhq.com/openai/abc",
        "https://boards.greenhouse.io/airbnb/jobs/7789554?gh_jid=7789554, https://boards.greenhouse.io/airbnb/jobs/7789554",
        "https://www.linkedin.com/jobs/view/42/, https://www.linkedin.com/jobs/view/42",
    })
    void reducesAnApplicationPageToItsPosting(String page, String posting) {
        assertThat(JobLookup.posting(page)).isEqualTo(posting);
    }
}
