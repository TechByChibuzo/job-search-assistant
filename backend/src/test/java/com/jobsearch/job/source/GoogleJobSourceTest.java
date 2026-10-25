package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleJobSourceTest {

    private MockRestServiceServer server;
    private GoogleJobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new GoogleJobSource(builder, Pacer.immediate(5), new ObjectMapper());
    }

    @Test
    void readsTheJobsEmbeddedInTheResultsPage() {
        server.expect(requestTo(Matchers.startsWith("https://www.google.com/about/careers/applications/jobs/results")))
                .andExpect(queryParam("sort_by", "date"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("location", "United%20States"))
                .andRespond(withSuccess(Fixtures.read("google-results.html"), MediaType.TEXT_HTML));
        server.expect(requestTo(Matchers.containsString("page=2")))
                .andRespond(withSuccess(emptyPage(), MediaType.TEXT_HTML));

        var query = new FetchQuery(Fixtures.NOW, Instant.EPOCH, List.of(), true);
        List<FetchedJob> jobs = source.fetch("google", query).jobs();

        assertThat(jobs).hasSize(3);
        FetchedJob pm = jobs.getFirst();
        assertThat(pm.externalId()).isEqualTo("141535940278395590");
        assertThat(pm.title()).isEqualTo("Product Manager, Marketing Hub");
        assertThat(pm.location()).startsWith("Kirkland, WA, USA; Mountain View, CA, USA");
        assertThat(pm.region()).isEqualTo(Region.US);
        assertThat(pm.postedAt()).isEqualTo(Instant.ofEpochSecond(1792798723));
        assertThat(pm.url()).isEqualTo("https://www.google.com/about/careers/applications/jobs/results/141535940278395590");
        assertThat(pm.description()).contains("Responsibilities", "Qualifications").doesNotContain("<li>");
    }

    @Test
    void saysSoWhenGoogleChangesItsPage() {
        assertThatThrownBy(() -> source.parse("<html><body>Nothing here</body></html>"))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("no longer has the job data");
    }

    private static String emptyPage() {
        return "<script>AF_initDataCallback({key: 'ds:1', hash: '2', data:[null,null,0,20], sideChannel: {}});</script>";
    }
}
