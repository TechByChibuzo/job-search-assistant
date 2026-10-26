package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AvatureJobSourceTest {

    @Test
    void readsRolesAndLocationsFromTheResultsPage() {
        List<FetchedJob> jobs = AvatureJobSource.parse(Fixtures.read("avature-search.html"));

        assertThat(jobs).extracting(FetchedJob::externalId).containsExactly("45808", "45785", "45796");
        FetchedJob first = jobs.getFirst();
        assertThat(first.title()).isEqualTo("Accounting Analyst - International Controllers Group");
        assertThat(first.location()).isEqualTo("New York, New York, United States of America");
        assertThat(first.region()).isEqualTo(Region.US);
        assertThat(first.url()).isEqualTo("https://bloomberg.avature.net/careers/JobDetail/Accounting-Analyst-International-Controllers-Group/45808");
        assertThat(first.postedAt()).isNull();
        assertThat(jobs.get(1).region()).isEqualTo(Region.NON_US);
    }

    @Test
    void aShortPageIsTheLastOneAndTheListIsComplete() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://bloomberg.avature.net/careers/SearchJobs/?listFilterMode=1&jobRecordsPerPage=12&jobOffset=0"))
                .andRespond(withSuccess(Fixtures.read("avature-search.html"), MediaType.TEXT_HTML));

        FetchResult result = new AvatureJobSource(builder, Pacer.immediate(5)).fetch("bloomberg/careers", Fixtures.ALL);

        server.verify();
        assertThat(result.jobs()).hasSize(3);
        assertThat(result.coveredSince()).isNull();
    }

    @Test
    void describesOneRoleFromItsPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "https://bloomberg.avature.net/careers/JobDetail/Senior-Software-Engineer-Media-API-Platform/45685";
        server.expect(requestTo(url)).andRespond(withSuccess(Fixtures.read("avature-job.html"), MediaType.TEXT_HTML));

        var description = new AvatureJobSource(builder, Pacer.immediate(5)).describe("bloomberg/careers", "45685", url);

        assertThat(description).get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .startsWith("Bloomberg Media empowers global business leaders")
                .contains("Salary Range")
                .doesNotContain("<div");
        server.verify();
    }

    @Test
    void readsOnlyPagesOnTheBoardsOwnSite() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var source = new AvatureJobSource(builder, Pacer.immediate(5));

        assertThat(source.describe("bloomberg/careers", "1", "https://evil.example/careers/JobDetail/x/1")).isEmpty();
        assertThat(source.describe("bloomberg/careers", "1", "https://bloomberg.avature.net/other/JobDetail/x/1")).isEmpty();
        assertThat(source.describe("bloomberg/careers", "1", "https://bloomberg.avature.net.evil.example/careers/JobDetail/x/1")).isEmpty();
        server.verify();
    }
}
