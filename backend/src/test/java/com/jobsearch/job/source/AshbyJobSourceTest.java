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

class AshbyJobSourceTest {

    private static final String URL = "https://api.ashbyhq.com/posting-api/job-board/openai";

    private MockRestServiceServer server;
    private AshbyJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new AshbyJobSource(builder);
    }

    @Test
    void mapsJobsFromTheRealFeed() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.read("ashby-job-board.json"), MediaType.APPLICATION_JSON));

        List<FetchedJob> jobs = source.fetch("openai", Fixtures.ALL).jobs();

        assertThat(jobs).hasSize(2);
        FetchedJob data = jobs.getFirst();
        assertThat(data.externalId()).isEqualTo("fc5bbc77-a30c-4e7a-9acc-8a2e748545b4");
        assertThat(data.title()).isEqualTo("Data Engineer");
        assertThat(data.team()).isEqualTo("Applied AI Engineering");
        assertThat(data.location()).isEqualTo("San Francisco");
        assertThat(data.postedAt()).isEqualTo(Instant.parse("2023-09-26T22:34:48.884Z"));
        assertThat(data.url()).isEqualTo("https://jobs.ashbyhq.com/openai/fc5bbc77-a30c-4e7a-9acc-8a2e748545b4");
        assertThat(data.description()).startsWith("ABOUT THE TEAM");
    }

    @Test
    void hybridWinsOverTheIsRemoteFlag() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.read("ashby-job-board.json"), MediaType.APPLICATION_JSON));

        List<FetchedJob> jobs = source.fetch("openai", Fixtures.ALL).jobs();

        // The first job has workplaceType "Hybrid" and isRemote true; the second has neither
        assertThat(jobs).extracting(FetchedJob::workplace).containsExactly(Workplace.HYBRID, Workplace.UNKNOWN);
    }

    @Test
    void skipsUnlistedJobs() {
        String feed = """
                {"jobs": [
                  {"id": "a", "title": "Listed", "isListed": true, "jobUrl": "https://jobs.ashbyhq.com/acme/a"},
                  {"id": "b", "title": "Hidden", "isListed": false, "jobUrl": "https://jobs.ashbyhq.com/acme/b"}
                ]}
                """;
        server.expect(requestTo("https://api.ashbyhq.com/posting-api/job-board/acme"))
                .andRespond(withSuccess(feed, MediaType.APPLICATION_JSON));

        assertThat(source.fetch("acme", Fixtures.ALL).jobs()).extracting(FetchedJob::title).containsExactly("Listed");
    }

    @Test
    void reportsAMissingBoard() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> source.fetch("openai", Fixtures.ALL).jobs()).isInstanceOf(BoardNotFoundException.class);
    }
}
