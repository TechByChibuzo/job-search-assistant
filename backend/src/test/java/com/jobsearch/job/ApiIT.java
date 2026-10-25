package com.jobsearch.job;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jobsearch.TestcontainersConfiguration;
import com.jobsearch.company.CompanyRepository;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSources;
import com.jobsearch.job.source.Workplace;

/** The REST API end to end: HTTP in, Postgres underneath, with the ATS feeds faked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companies;

    @Autowired
    JobRepository jobs;

    @MockitoBean
    JobSources sources;

    private final FakeJobSource feed = new FakeJobSource();

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        companies.deleteAll();
        when(sources.forType(any())).thenReturn(feed);
    }

    @Test
    void addsACompanyFromACareersLink() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://jobs.lever.co/palantir\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Palantir"))
                .andExpect(jsonPath("$.ats").value("LEVER"))
                .andExpect(jsonPath("$.board").value("palantir"))
                .andExpect(jsonPath("$.openJobs").value(0));
    }

    @Test
    void rejectsALinkItCannotRead() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://careers.example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Pick the ATS and board by hand")));
    }

    @Test
    void rejectsADuplicateBoard() throws Exception {
        String body = "{\"careersUrl\": \"https://jobs.ashbyhq.com/openai\"}";
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptsAnExplicitAtsAndBoard() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Airbnb\", \"ats\": \"GREENHOUSE\", \"board\": \" Airbnb \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.board").value("airbnb"));
    }

    @Test
    void previewsDetection() throws Exception {
        mvc.perform(post("/api/companies/detect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"boards.greenhouse.io/airbnb\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ats").value("GREENHOUSE"))
                .andExpect(jsonPath("$.board").value("airbnb"));
    }

    @Test
    void aNewCareersLinkMovesTheCompanyToAnotherAts() throws Exception {
        long id = createCompany("https://jobs.lever.co/plaid");

        mvc.perform(patch("/api/companies/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://jobs.ashbyhq.com/plaid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ats").value("ASHBY"));
    }

    @Test
    void syncsAndListsJobsNewestFirstWithFilters() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(
                job("1", "Backend Engineer", "Platform", "Remote - US", Workplace.REMOTE),
                job("2", "Product Designer", "Design", "New York, NY", Workplace.ONSITE),
                job("3", "Senior Backend Engineer", "Payments", "Austin, TX", Workplace.HYBRID));

        mvc.perform(post("/api/companies/" + id + "/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.newJobs").value(3));

        mvc.perform(get("/api/jobs"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.content[0].isNew").value(true));

        mvc.perform(get("/api/jobs").param("q", "backend engineer"))
                .andExpect(jsonPath("$.content", hasSize(2)));

        mvc.perform(get("/api/jobs").param("q", "payments"))
                .andExpect(jsonPath("$.content[0].title").value("Senior Backend Engineer"));

        mvc.perform(get("/api/jobs").param("workplace", "REMOTE", "HYBRID"))
                .andExpect(jsonPath("$.content", hasSize(2)));

        mvc.perform(get("/api/jobs").param("location", "new york"))
                .andExpect(jsonPath("$.content[0].title").value("Product Designer"));

        mvc.perform(get("/api/companies/" + id))
                .andExpect(jsonPath("$.openJobs").value(3))
                .andExpect(jsonPath("$.lastSyncStatus").value("OK"));
    }

    @Test
    void closedJobsAreHiddenUnlessAskedFor() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(job("1", "Backend Engineer", null, null, Workplace.UNKNOWN));
        mvc.perform(post("/api/companies/" + id + "/sync"));
        feed.returns();
        mvc.perform(post("/api/sync"));

        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/jobs").param("includeClosed", "true"))
                .andExpect(jsonPath("$.content[0].open").value(false));
    }

    @Test
    void showsAJobWithItsDescription() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(job("1", "Backend Engineer", null, null, Workplace.UNKNOWN));
        mvc.perform(post("/api/companies/" + id + "/sync"));
        long jobId = jobs.findByCompanyId(id).getFirst().getId();

        mvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.description").value("Build things."));
    }

    @Test
    void unknownIdsAnswer404AsProblemDetails() throws Exception {
        mvc.perform(get("/api/jobs/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No job with id 999999"));
        mvc.perform(delete("/api/companies/999999")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnOversizedPage() throws Exception {
        mvc.perform(get("/api/jobs").param("size", "5000")).andExpect(status().isBadRequest());
    }

    private long createCompany(String careersUrl) throws Exception {
        String json = mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"" + careersUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<Integer>read(json, "$.id").longValue();
    }

    private static FetchedJob job(String id, String title, String team, String location, Workplace workplace) {
        return new FetchedJob(id, title, team, location, workplace, Instant.parse("2026-10-20T12:00:00Z"),
                "https://example.com/jobs/" + id, "Build things.");
    }
}
