package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoldmanJobSourceTest {

    private static final String API = "https://api-higher.gs.com/gateway/api/v1/graphql";

    private MockRestServiceServer server;
    private GoldmanJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new GoldmanJobSource(builder, Pacer.immediate(5));
    }

    @Test
    void searchesNewestFirstInTheUsAndMapsRoles() {
        server.expect(requestTo(API)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.variables.searchQueryInput.searchTerm").value("software engineer"))
                .andExpect(jsonPath("$.variables.searchQueryInput.sort.sortStrategy").value("POSTED_DATE"))
                .andExpect(jsonPath("$.variables.searchQueryInput.filters[0].filters[0].filter").value("United States"))
                .andExpect(jsonPath("$.variables.searchQueryInput.page.pageNumber").value(0))
                .andRespond(withSuccess(Fixtures.read("goldman-roles.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API))
                .andExpect(jsonPath("$.variables.searchQueryInput.page.pageNumber").value(1))
                .andRespond(withSuccess("{\"data\": {\"roleSearch\": {\"totalCount\": 3, \"items\": []}}}", MediaType.APPLICATION_JSON));

        var query = new FetchQuery(Fixtures.NOW, Instant.parse("2026-09-25T00:00:00Z"), List.of("software engineer"), true);
        List<FetchedJob> jobs = source.fetch("goldman", query).jobs();

        server.verify();
        assertThat(jobs).hasSize(3);
        FetchedJob se = jobs.get(1);
        assertThat(se.externalId()).isEqualTo("177645_GS_MID_CAREER");
        assertThat(se.title()).isEqualTo("Software Engineer, Global Banking & Markets, Front Office Technology");
        assertThat(se.team()).isEqualTo("Global Banking & Markets");
        assertThat(se.location()).startsWith("New York");
        assertThat(se.region()).isEqualTo(Region.US);
        assertThat(se.postedAt()).isEqualTo(Instant.parse("2026-10-24T20:34:55.775390530Z"));
        assertThat(se.url()).isEqualTo("https://higher.gs.com/roles/177645");
        assertThat(se.description()).isNotBlank().doesNotContain("<");
    }

    @Test
    void saysSoWhenTheApiRejectsTheQuery() {
        server.expect(requestTo(API)).andRespond(withSuccess(
                "{\"errors\": [{\"message\": \"Field 'createdDate' is undefined\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch("goldman", Fixtures.ALL))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("Field 'createdDate' is undefined");
    }
}
