package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

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

    private Job only() {
        var all = jobs.findByCompanyId(acme.getId());
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    static FetchedJob job(String id, String title) {
        return new FetchedJob(id, title, "Platform", "Remote - US", Workplace.REMOTE, Instant.parse("2026-10-20T12:00:00Z"),
                "https://example.com/jobs/" + id, "Build things.");
    }
}
