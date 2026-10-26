package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.time.Duration;
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
                .andExpect(jsonPath("$.board").value("airbnb"))
                .andExpect(jsonPath("$.searchBased").value(false));

        mvc.perform(post("/api/companies/detect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://www.amazon.jobs/en/\"}"))
                .andExpect(jsonPath("$.ats").value("AMAZON"))
                .andExpect(jsonPath("$.searchBased").value(true));
    }

    @Test
    void savesAndEditsSearchPhrases() throws Exception {
        String json = mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://www.amazon.jobs\", \"search\": \" software engineer, backend \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Amazon"))
                .andExpect(jsonPath("$.search").value("software engineer, backend"))
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.<Integer>read(json, "$.id").longValue();

        mvc.perform(patch("/api/companies/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"search\": \"\"}"))
                .andExpect(jsonPath("$.search").doesNotExist());
    }

    @Test
    void namesSingleCompanySourcesAfterTheirCompany() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"https://higher.gs.com/results\", \"search\": \"software engineer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Goldman Sachs"))
                .andExpect(jsonPath("$.ats").value("GOLDMAN"))
                .andExpect(jsonPath("$.searchBased").value(true));
    }

    @Test
    void acceptsAWorkdayBoardByHandKeepingTheSiteCase() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ats\": \"WORKDAY\", \"board\": \"nvidia.wd5/NVIDIAExternalCareerSite\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.board").value("nvidia.wd5/NVIDIAExternalCareerSite"))
                .andExpect(jsonPath("$.name").value("Nvidia"));

        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ats\": \"WORKDAY\", \"board\": \"nvidia\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theInboxHidesOldRolesUnlessAskedAndNewOnlySkipsTheFirstImport() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        // First import: one role posted today, one posted 10 days ago, one with no date
        feed.returns(
                job("1", "Fresh", null, null, Workplace.UNKNOWN),
                job("2", "Ten days", null, null, Workplace.UNKNOWN, Instant.now().minus(Duration.ofDays(10))),
                job("3", "Undated", null, null, Workplace.UNKNOWN, null));
        syncCompany(id);

        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/jobs").param("newOnly", "true"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Fresh"));
        mvc.perform(get("/api/jobs/summary"))
                .andExpect(jsonPath("$.newJobs").value(1))
                .andExpect(jsonPath("$.byCompany[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.maxAgeDays").value(30))
                .andExpect(jsonPath("$.usOnly").value(true))
                .andExpect(jsonPath("$.followUpDays").value(5));
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

        mvc.perform(post("/api/companies/" + id + "/sync")).andExpect(status().isAccepted());
        awaitSync()
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.results[0].status").value("OK"))
                .andExpect(jsonPath("$.results[0].newJobs").value(3));

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
    void listsTheMostRecentlyPostedFirst() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        Instant now = Instant.now();
        feed.returns(
                job("old", "Old", null, null, Workplace.UNKNOWN, now.minus(Duration.ofDays(9))),
                job("undated", "Undated", null, null, Workplace.UNKNOWN, null),
                job("mid", "Mid", null, null, Workplace.UNKNOWN, now.minus(Duration.ofDays(3))));
        syncCompany(id);

        // An undated role from the first import has no known age, so it goes last
        mvc.perform(get("/api/jobs"))
                .andExpect(jsonPath("$.content[0].title").value("Mid"))
                .andExpect(jsonPath("$.content[1].title").value("Old"))
                .andExpect(jsonPath("$.content[2].title").value("Undated"))
                .andExpect(jsonPath("$.content[2].listedAt").doesNotExist());

        // One that appears later was listed when it was first seen: just now
        feed.returns(
                job("old", "Old", null, null, Workplace.UNKNOWN, now.minus(Duration.ofDays(9))),
                job("undated", "Undated", null, null, Workplace.UNKNOWN, null),
                job("mid", "Mid", null, null, Workplace.UNKNOWN, now.minus(Duration.ofDays(3))),
                job("later", "Later undated", null, null, Workplace.UNKNOWN, null));
        syncCompany(id);
        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.content[0].title").value("Later undated"));
    }

    @Test
    void closedJobsAreHiddenUnlessAskedFor() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(job("1", "Backend Engineer", null, null, Workplace.UNKNOWN));
        syncCompany(id);
        feed.returns();
        mvc.perform(post("/api/sync")).andExpect(status().isAccepted());
        awaitSync();

        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/jobs").param("includeClosed", "true"))
                .andExpect(jsonPath("$.content[0].open").value(false));
    }

    @Test
    void showsAJobWithItsDescription() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(job("1", "Backend Engineer", null, null, Workplace.UNKNOWN));
        syncCompany(id);
        long jobId = jobs.findByCompanyId(id).getFirst().getId();

        mvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.description").value("Build things."));
    }

    @Test
    void someoneWhoNeedsSponsorshipCanHideRolesClosedToThem() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        Instant posted = Instant.now().minus(Duration.ofHours(2));
        feed.returns(
                new FetchedJob("1", "Satellite Engineer", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/1",
                        "Build ground software. Candidates must be a U.S. citizen or national. Apply today."),
                new FetchedJob("2", "Chip Engineer", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/2",
                        "Candidates for this role may need to meet certain legal status requirements."),
                job("3", "Backend Engineer", null, null, Workplace.UNKNOWN));
        syncCompany(id);

        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/jobs").param("needsSponsorship", "true"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == 'Chip Engineer')].visaRestriction").value("MAY_BE_RESTRICTED"));
        mvc.perform(get("/api/jobs/summary")).andExpect(jsonPath("$.newJobs").value(3));
        mvc.perform(get("/api/jobs/summary").param("needsSponsorship", "true")).andExpect(jsonPath("$.newJobs").value(2));
        long restricted = jobs.findByCompanyId(id).stream().filter(j -> j.getExternalId().equals("1")).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/jobs/" + restricted))
                .andExpect(jsonPath("$.summary.visaRestriction").value("CITIZENS_ONLY"))
                .andExpect(jsonPath("$.visaEvidence").value("Candidates must be a U.S. citizen or national."));

        // The owner knows this company doesn't sponsor, whatever its postings say
        mvc.perform(patch("/api/companies/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"noSponsorship\": true}"))
                .andExpect(jsonPath("$.noSponsorship").value(true));
        mvc.perform(get("/api/jobs").param("needsSponsorship", "true")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/jobs/summary").param("needsSponsorship", "true")).andExpect(jsonPath("$.newJobs").value(0));
        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.content[0].companyNoSponsorship").value(true));
    }

    @Test
    void aRoleWhoseFeedHadNoDescriptionIsDescribedOnceAndKeepsItThroughSyncs() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        Instant posted = Instant.now().minus(Duration.ofHours(2));
        FetchedJob bare = new FetchedJob("1", "Backend Engineer", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/1", null);
        feed.returns(bare);
        syncCompany(id);
        long jobId = jobs.findByCompanyId(id).getFirst().getId();
        feed.describes("Build payment rails. Candidates must be a U.S. citizen.");

        mvc.perform(post("/api/jobs/" + jobId + "/description"))
                .andExpect(jsonPath("$.description").value("Build payment rails. Candidates must be a U.S. citizen."))
                // The visa check reads the new description too
                .andExpect(jsonPath("$.summary.visaRestriction").value("CITIZENS_ONLY"));
        mvc.perform(post("/api/jobs/" + jobId + "/description")).andExpect(status().isOk());
        assertThat(feed.describeCalls).isEqualTo(1);

        // The next sync's listing has no description; the one found stays
        feed.returns(bare);
        syncCompany(id);
        mvc.perform(get("/api/jobs/" + jobId)).andExpect(jsonPath("$.description").value("Build payment rails. Candidates must be a U.S. citizen."));
    }

    @Test
    void aRoleSavedFromLinkedInIsDescribedFromTheCompanysOwnBoard() throws Exception {
        String added = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://www.linkedin.com/jobs/view/4473964041/\", \"title\": \"Software Engineer, Bridge\", \"company\": \"Stripe\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jobId = com.jayway.jsonpath.JsonPath.<Integer>read(added, "$.job.id").longValue();
        // Stripe's Greenhouse board, under its name, lists the same role with its description
        feed.returns(new FetchedJob("7277110", "Software engineer - Bridge", null, null, Workplace.UNKNOWN, null, Instant.now(),
                "https://stripe.com/jobs/search?gh_jid=7277110", "Bridge builds stablecoin infrastructure."));

        mvc.perform(post("/api/jobs/" + jobId + "/description"))
                .andExpect(jsonPath("$.description").value("Bridge builds stablecoin infrastructure."));
    }

    @Test
    void aRoleAddedFromTheExtensionKeepsTheDescriptionTheOwnerSaw() throws Exception {
        String link = "{\"url\": \"https://www.linkedin.com/jobs/view/4012345678/\", \"title\": \"Engineer\", \"company\": \"Nooks\"";
        String added = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(link + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long jobId = com.jayway.jsonpath.JsonPath.<Integer>read(added, "$.job.id").longValue();
        mvc.perform(get("/api/jobs/" + jobId)).andExpect(jsonPath("$.description").doesNotExist());

        // Added again from the extension, now with what the page showed: the saved role gets it
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(link + ", \"description\": \"Build the dialer.\"}"))
                .andExpect(jsonPath("$.alreadySaved").value(true));
        mvc.perform(get("/api/jobs/" + jobId)).andExpect(jsonPath("$.description").value("Build the dialer."));
    }

    @Test
    void aRoleNoSiteCanDescribeIsNotAskedAboutAgainForADay() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        feed.returns(new FetchedJob("1", "Backend Engineer", null, null, Workplace.UNKNOWN, null, Instant.now(), "https://example.com/jobs/1", null));
        syncCompany(id);
        long jobId = jobs.findByCompanyId(id).getFirst().getId();

        mvc.perform(post("/api/jobs/" + jobId + "/description")).andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(post("/api/jobs/" + jobId + "/description")).andExpect(status().isOk());
        assertThat(feed.describeCalls).isEqualTo(1);
    }

    @Test
    void anEarlyCareerSearchHidesSeniorTitlesAndRolesAskingForMoreYears() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        Instant posted = Instant.now().minus(Duration.ofHours(2));
        feed.returns(
                new FetchedJob("1", "Principal Engineer", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/1", "Lead us."),
                new FetchedJob("2", "Software Engineer", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/2",
                        "Basic qualifications - 8+ years of non-internship professional software development experience"),
                new FetchedJob("3", "Software Engineer II", null, null, Workplace.UNKNOWN, null, posted, "https://example.com/jobs/3",
                        "• 2 years of experience with software development."),
                job("4", "Backend Engineer", null, null, Workplace.UNKNOWN));
        syncCompany(id);

        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/jobs").param("maxYears", "4"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == 'Software Engineer II')].minYears").value(2));
        mvc.perform(get("/api/jobs/summary").param("maxYears", "4")).andExpect(jsonPath("$.newJobs").value(2));
        long eightYears = jobs.findByCompanyId(id).stream().filter(j -> j.getExternalId().equals("2")).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/jobs/" + eightYears))
                .andExpect(jsonPath("$.summary.minYears").value(8))
                .andExpect(jsonPath("$.yearsEvidence").value("Basic qualifications - 8+ years of non-internship professional software development experience"));
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

    @Test
    void reportsSyncProgressAndRefusesASecondRunWhileOneIsGoing() throws Exception {
        long id = createCompany("https://boards.greenhouse.io/acme");
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(sources.forType(any())).thenReturn(new FakeJobSource() {
            @Override
            public com.jobsearch.job.source.FetchResult fetch(String board, com.jobsearch.job.source.FetchQuery query) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.fetch(board, query);
            }
        });

        mvc.perform(post("/api/sync")).andExpect(status().isAccepted()).andExpect(jsonPath("$.running").value(true));
        mvc.perform(post("/api/companies/" + id + "/sync")).andExpect(status().isConflict());
        // The run thread may not have reached the company yet
        String progress = "";
        for (int i = 0; i < 100 && !progress.contains("\"current\":\"Acme\""); i++) {
            Thread.sleep(20);
            progress = mvc.perform(get("/api/sync")).andReturn().getResponse().getContentAsString();
        }
        org.assertj.core.api.Assertions.assertThat(progress).contains("\"current\":\"Acme\"");

        release.countDown();
        // The company, then the steps that always run after a full sync (role descriptions)
        awaitSync().andExpect(jsonPath("$.done").value(2)).andExpect(jsonPath("$.finishedAt").exists());
    }

    @Test
    void syncingAnUnknownCompanyIs404() throws Exception {
        mvc.perform(post("/api/companies/999999/sync")).andExpect(status().isNotFound());
    }

    private void syncCompany(long id) throws Exception {
        mvc.perform(post("/api/companies/" + id + "/sync")).andExpect(status().isAccepted());
        awaitSync();
    }

    /** Polls the background sync until it finishes, then returns the final progress for assertions. */
    private org.springframework.test.web.servlet.ResultActions awaitSync() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var result = mvc.perform(get("/api/sync"));
            if (!result.andReturn().getResponse().getContentAsString().contains("\"running\":true")) {
                return result;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Sync didn't finish within 10 seconds");
    }

    private long createCompany(String careersUrl) throws Exception {
        String json = mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careersUrl\": \"" + careersUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<Integer>read(json, "$.id").longValue();
    }

    private static FetchedJob job(String id, String title, String team, String location, Workplace workplace) {
        return job(id, title, team, location, workplace, Instant.now().minus(Duration.ofHours(2)));
    }

    private static FetchedJob job(String id, String title, String team, String location, Workplace workplace, Instant postedAt) {
        return new FetchedJob(id, title, team, location, workplace, null, postedAt, "https://example.com/jobs/" + id, "Build things.");
    }
}
