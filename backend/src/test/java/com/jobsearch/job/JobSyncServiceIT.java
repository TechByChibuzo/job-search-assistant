package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.jobsearch.TestcontainersConfiguration;
import com.jobsearch.company.AtsType;
import com.jobsearch.company.Company;
import com.jobsearch.company.CompanyRepository;
import com.jobsearch.company.DetectedBoard;
import com.jobsearch.company.SyncStatus;
import com.jobsearch.job.source.BoardNotFoundException;
import com.jobsearch.job.source.FeedFormatException;
import com.jobsearch.job.source.FetchResult;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSources;
import com.jobsearch.job.source.Workplace;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobSyncServiceIT {

    @Autowired
    JobSyncService sync;

    @Autowired
    CompanyRepository companies;

    @Autowired
    JobRepository jobs;

    @MockitoBean
    JobSources sources;

    private final FakeJobSource feed = new FakeJobSource();
    private Company acme;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        companies.deleteAll();
        acme = companies.save(new Company("Acme", new DetectedBoard(AtsType.GREENHOUSE, "acme"), null));
        when(sources.forType(any())).thenReturn(feed);
    }

    @Test
    void addsRolesItHasNotSeen() {
        feed.returns(job("1", "Backend Engineer"), job("2", "Data Engineer"));

        SyncResult result = sync.sync(acme.getId());

        assertThat(result.status()).isEqualTo(SyncStatus.OK);
        assertThat(result.newJobs()).isEqualTo(2);
        assertThat(jobs.findByCompanyId(acme.getId()))
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder("Backend Engineer", "Data Engineer");
    }

    @Test
    void refreshesSeenRolesWithoutResettingWhenTheyWereFirstSeen() {
        feed.returns(job("1", "Backend Engineer"));
        sync.sync(acme.getId());
        Instant firstSeen = only().getFirstSeenAt();

        feed.returns(job("1", "Senior Backend Engineer"));
        SyncResult result = sync.sync(acme.getId());

        assertThat(result.newJobs()).isZero();
        assertThat(result.updatedJobs()).isEqualTo(1);
        Job job = only();
        assertThat(job.getTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(job.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(job.getLastSeenAt()).isAfterOrEqualTo(firstSeen);
    }

    @Test
    void closesRolesThatLeaveTheFeedAndReopensThemIfTheyReturn() {
        feed.returns(job("1", "Backend Engineer"));
        sync.sync(acme.getId());

        feed.returns();
        SyncResult closing = sync.sync(acme.getId());
        assertThat(closing.closedJobs()).isEqualTo(1);
        assertThat(only().isOpen()).isFalse();

        // Closing twice isn't counted twice
        assertThat(sync.sync(acme.getId()).closedJobs()).isZero();

        feed.returns(job("1", "Backend Engineer"));
        sync.sync(acme.getId());
        assertThat(only().isOpen()).isTrue();
    }

    @Test
    void aRoleListedTwiceInOneFeedIsStoredOnce() {
        feed.returns(job("1", "Backend Engineer"), job("1", "Backend Engineer (copy)"));

        assertThat(sync.sync(acme.getId()).newJobs()).isEqualTo(1);
        assertThat(only().getTitle()).isEqualTo("Backend Engineer (copy)");
    }

    @Test
    void recordsAMissingBoardSoTheOwnerCanReDetectIt() {
        feed.fails(new BoardNotFoundException(AtsType.GREENHOUSE, "acme"));

        SyncResult result = sync.sync(acme.getId());

        assertThat(result.status()).isEqualTo(SyncStatus.BOARD_NOT_FOUND);
        Company saved = companies.findById(acme.getId()).orElseThrow();
        assertThat(saved.getLastSyncStatus()).isEqualTo(SyncStatus.BOARD_NOT_FOUND);
        assertThat(saved.getLastSyncedAt()).isNotNull();
    }

    @Test
    void oneBrokenBoardDoesNotStopTheOthers() {
        Company globex = companies.save(new Company("Globex", new DetectedBoard(AtsType.LEVER, "globex"), null));
        FakeJobSource broken = new FakeJobSource();
        broken.fails(new ResourceAccessException("Read timed out"));
        when(sources.forType(AtsType.LEVER)).thenReturn(broken);
        feed.returns(job("1", "Backend Engineer"));

        var results = sync.syncAll();

        assertThat(results).extracting(SyncResult::companyName, SyncResult::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Acme", SyncStatus.OK),
                        org.assertj.core.groups.Tuple.tuple("Globex", SyncStatus.FAILED));
        assertThat(companies.findById(globex.getId()).orElseThrow().getLastSyncError()).contains("Read timed out");
        assertThat(jobs.findByCompanyId(acme.getId())).hasSize(1);
    }

    @Test
    void inactiveCompaniesAreSkipped() {
        acme.setActive(false);
        companies.save(acme);
        feed.returns(job("1", "Backend Engineer"));

        assertThat(sync.syncAll()).isEmpty();
    }

    @Test
    void skipsRolesOutsideTheUs() {
        feed.returns(job("1", "Backend Engineer", "New York, NY", hoursAgo(2)),
                job("2", "Backend Engineer", "Tokyo, Japan", hoursAgo(2)),
                job("3", "Backend Engineer", "Remote", hoursAgo(2)));

        SyncResult result = sync.sync(acme.getId());

        assertThat(result.newJobs()).isEqualTo(2);
        assertThat(result.skippedJobs()).isEqualTo(1);
        assertThat(jobs.findByCompanyId(acme.getId())).extracting(Job::getExternalId).containsExactlyInAnyOrder("1", "3");
    }

    @Test
    void doesNotImportOldRolesButKeepsRefreshingOnesItAlreadyHas() {
        feed.returns(job("1", "Backend Engineer", "Remote - US", daysAgo(20)));
        sync.sync(acme.getId());

        // A month later the stored role has aged past the cutoff, and a new-to-us old role appears
        feed.returns(job("1", "Backend Engineer II", "Remote - US", daysAgo(50)),
                job("2", "Data Engineer", "Remote - US", daysAgo(45)));
        SyncResult result = sync.sync(acme.getId());

        assertThat(result.newJobs()).isZero();
        assertThat(result.updatedJobs()).isEqualTo(1);
        assertThat(result.skippedJobs()).isEqualTo(1);
        assertThat(only().getTitle()).isEqualTo("Backend Engineer II");
    }

    @Test
    void aCompanysFirstImportOnlyMarksRecentlyPostedRolesAsNew() {
        feed.returns(job("1", "Backend Engineer", "Remote - US", hoursAgo(3)),
                job("2", "Data Engineer", "Remote - US", daysAgo(10)),
                job("3", "Designer", "Remote - US", null));
        sync.sync(acme.getId());

        feed.returns(job("1", "Backend Engineer", "Remote - US", hoursAgo(3)),
                job("2", "Data Engineer", "Remote - US", daysAgo(10)),
                job("3", "Designer", "Remote - US", null),
                job("4", "Product Manager", "Remote - US", daysAgo(10)));
        sync.sync(acme.getId());

        Instant newSince = Instant.now().minus(Duration.ofHours(24));
        assertThat(jobs.findByCompanyId(acme.getId()))
                .filteredOn(j -> j.isNewSince(newSince))
                .extracting(Job::getExternalId)
                .containsExactlyInAnyOrder("1", "4");
    }

    @Test
    void aFeedThatStoppedAtTheCutoffOnlyClosesRolesItCovered() {
        feed.returns(job("recent", "Backend Engineer", "Remote - US", daysAgo(5)),
                job("older", "Data Engineer", "Remote - US", daysAgo(25)));
        sync.sync(acme.getId());

        // A search feed that only read back to 10 days ago, and listed neither role
        feed.returns(new FetchResult(List.of(), daysAgo(10)));
        SyncResult result = sync.sync(acme.getId());

        assertThat(result.closedJobs()).isEqualTo(1);
        assertThat(jobs.findByCompanyId(acme.getId()))
                .filteredOn(Job::isOpen)
                .extracting(Job::getExternalId)
                .containsExactly("older");
    }

    @Test
    void searchPhrasesNarrowTheBoardAndReachTheSource() {
        acme.setSearch("backend, platform engineer");
        companies.save(acme);
        feed.returns(job("1", "Backend Engineer"), job("2", "Sales Lead"));

        SyncResult result = sync.sync(acme.getId());

        assertThat(feed.lastQuery.searches()).containsExactly("backend", "platform engineer");
        assertThat(feed.lastQuery.usOnly()).isTrue();
        assertThat(result.newJobs()).isEqualTo(1);
        assertThat(result.skippedJobs()).isEqualTo(1);
    }

    @Test
    void changingTheSearchClosesRolesThatNoLongerMatch() {
        feed.returns(new FetchResult(List.of(job("1", "Backend Engineer"), job("2", "Sales Lead")), daysAgo(30)));
        sync.sync(acme.getId());

        acme.setSearch("backend");
        companies.save(acme);
        feed.returns(new FetchResult(List.of(job("1", "Backend Engineer")), daysAgo(30)));
        sync.sync(acme.getId());

        assertThat(jobs.findByCompanyId(acme.getId()))
                .filteredOn(Job::isOpen)
                .extracting(Job::getExternalId)
                .containsExactly("1");
    }

    @Test
    void aChangedSitePageIsReportedAsAFailedSync() {
        feed.fails(new FeedFormatException("Google's careers page no longer has the job data this app reads"));

        SyncResult result = sync.sync(acme.getId());

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.error()).contains("no longer has the job data");
    }

    private Job only() {
        var all = jobs.findByCompanyId(acme.getId());
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    static FetchedJob job(String id, String title) {
        return job(id, title, "Remote - US", hoursAgo(2));
    }

    static FetchedJob job(String id, String title, String location, Instant postedAt) {
        return new FetchedJob(id, title, "Platform", location, Workplace.REMOTE, null, postedAt,
                "https://example.com/jobs/" + id, "Build things.");
    }

    static Instant hoursAgo(long hours) {
        return Instant.now().minus(Duration.ofHours(hours));
    }

    static Instant daysAgo(long days) {
        return Instant.now().minus(Duration.ofDays(days));
    }
}
