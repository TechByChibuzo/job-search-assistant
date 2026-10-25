package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AmazonJobSourceTest {

    private MockRestServiceServer server;
    private AmazonJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new AmazonJobSource(builder, Pacer.immediate(5));
    }

    @Test
    void searchesNewestFirstInTheUsAndMapsRoles() {
        server.expect(requestTo(Matchers.startsWith("https://www.amazon.jobs/en/search.json")))
                .andExpect(queryParam("sort", "recent"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("base_query", "software%20engineer"))
                .andExpect(queryParam("normalized_country_code%5B%5D", "USA"))
                .andRespond(withSuccess(Fixtures.read("amazon-search.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("offset=100")))
                .andRespond(withSuccess("{\"hits\": 3, \"jobs\": []}", MediaType.APPLICATION_JSON));

        var query = new FetchQuery(Fixtures.NOW, Instant.parse("2026-09-25T00:00:00Z"), List.of("software engineer"), true);
        FetchResult result = source.fetch("amazon", query);

        server.verify();
        assertThat(result.jobs()).hasSize(3);
        FetchedJob first = result.jobs().getFirst();
        assertThat(first.externalId()).isEqualTo("10588004");
        assertThat(first.title()).isEqualTo("Software Development Engineer II, SageMaker AI Inference");
        assertThat(first.team()).isEqualTo("Software Development");
        assertThat(first.location()).isEqualTo("Bellevue, Washington, USA");
        assertThat(first.region()).isEqualTo(Region.US);
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-10-23T00:00:00Z"));
        assertThat(first.url()).isEqualTo("https://www.amazon.jobs/en/jobs/10588004/software-development-engineer-ii-sagemaker-ai-inference");
        assertThat(first.description()).contains("Basic qualifications");
        assertThat(result.coveredSince()).isNull();
    }

    @Test
    void readsAmazonsDateFormat() {
        assertThat(AmazonJobSource.posted("October 5, 2026")).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(AmazonJobSource.posted("not a date")).isNull();
    }
}
