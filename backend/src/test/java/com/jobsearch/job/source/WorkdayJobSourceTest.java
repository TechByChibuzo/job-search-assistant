package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WorkdayJobSourceTest {

    private static final String API = "https://nvidia.wd5.myworkdayjobs.com/wday/cxs/nvidia/NVIDIAExternalCareerSite/jobs";

    private MockRestServiceServer server;
    private WorkdayJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new WorkdayJobSource(builder, Pacer.immediate(5));
    }

    @Test
    void findsTheUnitedStatesFacetThenPagesWithIt() {
        server.expect(requestTo(API)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(Fixtures.read("workday-facets.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API))
                .andExpect(content().json("""
                        {"appliedFacets": {"locationHierarchy1": ["2fcb99c455831013ea52fb338f2932d8"]},
                         "offset": 0, "limit": 20, "searchText": "software"}"""))
                .andRespond(withSuccess(Fixtures.read("workday-jobs.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API))
                .andRespond(withSuccess("{\"total\": 0, \"jobPostings\": []}", MediaType.APPLICATION_JSON));

        var query = new FetchQuery(Fixtures.NOW, Fixtures.NOW.minus(Duration.ofDays(30)), List.of("software"), true);
        FetchResult result = source.fetch("nvidia.wd5/NVIDIAExternalCareerSite", query);

        server.verify();
        assertThat(result.jobs()).hasSize(3);
        FetchedJob first = result.jobs().getFirst();
        assertThat(first.title()).isEqualTo("Senior Linux Systems Engineer - EDA Infrastructure");
        assertThat(first.region()).isEqualTo(Region.US);
        assertThat(first.location()).isNull(); // "6 Locations" says nothing useful
        assertThat(first.postedAt()).isEqualTo(Fixtures.NOW.minus(Duration.ofDays(1)));
        assertThat(first.url()).isEqualTo("https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite"
                + "/job/US-CA-Remote/Senior-Linux-Systems-Engineer---EDA-Infrastructure_JR2025019");
        assertThat(result.jobs().get(2).location()).isEqualTo("US, WA, Remote");
        assertThat(result.jobs().get(2).workplace()).isEqualTo(Workplace.REMOTE);
    }

    @Test
    void readsRelativeDates() {
        Instant now = Fixtures.NOW;
        assertThat(WorkdayJobSource.posted("Posted Today", now)).isEqualTo(now);
        assertThat(WorkdayJobSource.posted("Posted Yesterday", now)).isEqualTo(now.minus(Duration.ofDays(1)));
        assertThat(WorkdayJobSource.posted("Posted 6 Days Ago", now)).isEqualTo(now.minus(Duration.ofDays(6)));
        assertThat(WorkdayJobSource.posted("Posted 30+ Days Ago", now)).isEqualTo(now.minus(Duration.ofDays(31)));
        assertThat(WorkdayJobSource.posted("Recently", now)).isNull();
    }

    @Test
    void reportsAMissingSite() {
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        var query = new FetchQuery(Fixtures.NOW, Instant.EPOCH, List.of(), true);
        assertThatThrownBy(() -> source.fetch("nvidia.wd5/NVIDIAExternalCareerSite", query))
                .isInstanceOf(BoardNotFoundException.class);
    }
}
