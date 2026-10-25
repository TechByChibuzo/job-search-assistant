package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GreenhouseJobSourceTest {

    private static final String URL = "https://boards-api.greenhouse.io/v1/boards/airbnb/jobs?content=true";

    private MockRestServiceServer server;
    private GreenhouseJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new GreenhouseJobSource(builder);
    }

    @Test
    void mapsAJobFromTheRealFeed() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.read("greenhouse-jobs.json"), MediaType.APPLICATION_JSON));

        List<FetchedJob> jobs = source.fetch("airbnb", Fixtures.ALL).jobs();

        assertThat(jobs).hasSize(2);
        FetchedJob legal = jobs.getFirst();
        assertThat(legal.externalId()).isEqualTo("7789554");
        assertThat(legal.title()).isEqualTo("Associate Legal Counsel, Japan");
        assertThat(legal.team()).isEqualTo("Legal");
        assertThat(legal.location()).isEqualTo("Tokyo, Japan");
        assertThat(legal.workplace()).isEqualTo(Workplace.HYBRID);
        assertThat(legal.postedAt()).isEqualTo(Instant.parse("2026-04-13T08:49:05Z"));
        assertThat(legal.url()).isEqualTo("https://careers.airbnb.com/positions/7789554?gh_jid=7789554");
        assertThat(legal.description()).isNotBlank().doesNotContain("<", "&lt;", "&quot;");
    }

    @Test
    void readsTheWorkplaceTypeCustomFieldAndTrimsLocations() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.read("greenhouse-jobs.json"), MediaType.APPLICATION_JSON));

        FetchedJob remote = source.fetch("airbnb", Fixtures.ALL).jobs().get(1);

        assertThat(remote.workplace()).isEqualTo(Workplace.REMOTE);
        assertThat(remote.location()).isEqualTo("United States");
    }

    @Test
    void reportsAMissingBoard() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> source.fetch("airbnb", Fixtures.ALL).jobs()).isInstanceOf(BoardNotFoundException.class);
    }
}
