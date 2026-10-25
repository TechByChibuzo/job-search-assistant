package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class LeverJobSourceTest {

    private static final String URL = "https://api.lever.co/v0/postings/spotify?mode=json";

    private MockRestServiceServer server;
    private LeverJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new LeverJobSource(builder);
    }

    @Test
    void mapsPostingsFromTheRealFeed() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.read("lever-postings.json"), MediaType.APPLICATION_JSON));

        List<FetchedJob> jobs = source.fetch("spotify", Fixtures.ALL).jobs();

        assertThat(jobs).extracting(FetchedJob::workplace).containsExactly(Workplace.REMOTE, Workplace.ONSITE);
        FetchedJob first = jobs.getFirst();
        assertThat(first.externalId()).isEqualTo("4310f31c-90e1-4e8d-bdcf-dce39c145b8c");
        assertThat(first.title()).isEqualTo("Associate – Audiobook Licensing & Author Partnerships");
        assertThat(first.team()).isEqualTo("Audio Books & Gated Content");
        assertThat(first.location()).isEqualTo("New York, NY");
        assertThat(first.postedAt()).isEqualTo(Instant.ofEpochMilli(1783608935950L));
        assertThat(first.url()).isEqualTo("https://jobs.lever.co/spotify/4310f31c-90e1-4e8d-bdcf-dce39c145b8c");
        assertThat(first.description()).isNotBlank();
    }

    @Test
    void joinsTheIntroListsAndClosingIntoOneDescription() {
        String posting = """
                [{"id": "1", "text": "Engineer", "hostedUrl": "https://jobs.lever.co/acme/1",
                  "categories": {"department": "Engineering"},
                  "descriptionPlain": "Intro.",
                  "lists": [{"text": "What you'll do", "content": "<li>Build</li><li>Ship</li>"}],
                  "additionalPlain": "Benefits."}]
                """;
        server.expect(requestTo("https://api.lever.co/v0/postings/acme?mode=json"))
                .andRespond(withSuccess(posting, MediaType.APPLICATION_JSON));

        FetchedJob job = source.fetch("acme", Fixtures.ALL).jobs().getFirst();

        assertThat(job.description()).isEqualTo("Intro.\n\nWhat you'll do\n• Build\n• Ship\n\nBenefits.");
        assertThat(job.team()).isEqualTo("Engineering");
        assertThat(job.workplace()).isEqualTo(Workplace.UNKNOWN);
    }

    @Test
    void reportsAMissingBoard() {
        // Lever answers an unknown board with 404 and {"ok":false,"error":"Document not found"}
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"ok\":false,\"error\":\"Document not found\"}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch("spotify", Fixtures.ALL).jobs()).isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void otherFailuresAreNotMistakenForAMissingBoard() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> source.fetch("spotify", Fixtures.ALL).jobs())
                .isInstanceOf(RestClientException.class)
                .isNotInstanceOf(BoardNotFoundException.class);
    }
}
