package com.jobsearch.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyDomainsTest {

    @ParameterizedTest
    @CsvSource({
        "https://careers.airbnb.com/positions/7789554?gh_jid=7789554, airbnb.com",
        "https://www.metacareers.com/jobs/123, metacareers.com",
        "https://jobs.bbc.co.uk/job/1, bbc.co.uk",
        "https://higher.gs.com/roles/177645, gs.com",
    })
    void findsTheCompanysSiteFromAJobLink(String link, String domain) {
        assertThat(CompanyDomains.fromLink(link)).contains(domain);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://boards.greenhouse.io/airbnb/jobs/1",
        "https://jobs.lever.co/spotify/abc",
        "https://jobs.ashbyhq.com/openai/1",
        "https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/1",
        "https://www.linkedin.com/jobs/view/4012345678/",
        "https://www.amazon.jobs/en/jobs/1",
        "not a link",
    })
    void ignoresHiringSystemsAndJobBoards(String link) {
        assertThat(CompanyDomains.fromLink(link)).isEmpty();
    }

    @Test
    void knowsAFewCompaniesAndGuessesTheRest() {
        assertThat(CompanyDomains.known("Goldman Sachs")).contains("goldmansachs.com");
        assertThat(CompanyDomains.known("Acme")).isEmpty();
        assertThat(CompanyDomains.guess("Lockheed Martin")).contains("lockheedmartin.com");
        assertThat(CompanyDomains.guess("!!!")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"https://www.Airbnb.com/careers, airbnb.com", "harvey.ai, harvey.ai", " stripe.com/ , stripe.com"})
    void tidiesWhatTheOwnerTypes(String input, String domain) {
        assertThat(CompanyDomains.normalize(input)).contains(domain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a domain", "localhost", "http://", "a..b"})
    void rejectsWhatIsNotADomain(String input) {
        assertThat(CompanyDomains.normalize(input)).isEmpty();
    }
}
