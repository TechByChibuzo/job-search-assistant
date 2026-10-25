package com.jobsearch.job;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobsearch.common.ConflictException;
import com.jobsearch.company.Company;
import com.jobsearch.company.CompanyRepository;
import com.jobsearch.company.CompanyService;
import com.jobsearch.company.SyncStatus;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.source.BoardNotFoundException;
import com.jobsearch.job.source.FeedFormatException;
import com.jobsearch.job.source.FetchQuery;
import com.jobsearch.job.source.FetchResult;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSources;

/**
 * Pulls each company's open roles and reconciles them with what is stored: unseen roles are added,
 * seen ones refreshed, and stored ones missing from the feed are closed.
 *
 * <p>The HTTP fetch happens outside any transaction, and each company is saved in its own transaction, so
 * a slow or broken board never holds a connection or rolls back another company's results.
 */
@Service
public class JobSyncService {

    private static final Logger log = LoggerFactory.getLogger(JobSyncService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final CompanyService companyService;
    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final JobSources sources;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AppProperties.Jobs jobRules;

    /** The scheduler and the "Sync now" button must not reconcile the same company at the same time. */
    private final ReentrantLock running = new ReentrantLock();

    JobSyncService(
            CompanyService companyService,
            CompanyRepository companies,
            JobRepository jobs,
            JobSources sources,
            TransactionTemplate tx,
            Clock clock,
            AppProperties properties) {
        this.companyService = companyService;
        this.companies = companies;
        this.jobs = jobs;
        this.sources = sources;
        this.tx = tx;
        this.clock = clock;
        this.jobRules = properties.jobs();
    }

    /** Notified as a sync moves through companies, so the UI can show progress. */
    public interface Progress {
        Progress NONE = new Progress() {};

        default void starting(Company company, int index, int total) {}

        default void finished(SyncResult result) {}
    }

    public List<SyncResult> syncAll() {
        return syncAll(Progress.NONE);
    }

    public List<SyncResult> syncAll(Progress progress) {
        return whileHoldingLock(() -> {
            List<SyncResult> results = new ArrayList<>();
            List<Company> active = companyService.listActive();
            for (int i = 0; i < active.size(); i++) {
                progress.starting(active.get(i), i, active.size());
                SyncResult result = syncOne(active.get(i));
                progress.finished(result);
                results.add(result);
            }
            log.info("Synced {} companies: {} new, {} closed, {} failed",
                    results.size(),
                    results.stream().mapToInt(SyncResult::newJobs).sum(),
                    results.stream().mapToInt(SyncResult::closedJobs).sum(),
                    results.stream().filter(r -> r.status() != SyncStatus.OK).count());
            return results;
        });
    }

    public SyncResult sync(long companyId) {
        return sync(companyId, Progress.NONE);
    }

    public SyncResult sync(long companyId, Progress progress) {
        Company company = companyService.get(companyId);
        return whileHoldingLock(() -> {
            progress.starting(company, 0, 1);
            SyncResult result = syncOne(company);
            progress.finished(result);
            return result;
        });
    }

    private SyncResult syncOne(Company company) {
        Instant now = clock.instant();
        IngestRules rules = new IngestRules(jobRules.usOnly(), now.minus(jobRules.maxAge()), company.getSearches());
        FetchResult fetched;
        try {
            fetched = sources.forType(company.getAts())
                    .fetch(company.getBoard(), new FetchQuery(now, rules.postedAfter(), rules.searches(), rules.usOnly()));
        } catch (BoardNotFoundException e) {
            return recordFailure(company, SyncStatus.BOARD_NOT_FOUND, e.getMessage());
        } catch (FeedFormatException e) {
            log.warn("Sync failed for {}: {}", company.getName(), e.getMessage());
            return recordFailure(company, SyncStatus.FAILED, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Sync failed for {} ({} {})", company.getName(), company.getAts(), company.getBoard(), e);
            return recordFailure(company, SyncStatus.FAILED, describe(e));
        }
        SyncResult result = tx.execute(status -> reconcile(company.getId(), fetched, rules, now));
        log.info("Synced {}: {} new, {} updated, {} closed, {} skipped",
                company.getName(), result.newJobs(), result.updatedJobs(), result.closedJobs(), result.skippedJobs());
        return result;
    }

    private SyncResult reconcile(long companyId, FetchResult fetched, IngestRules rules, Instant now) {
        Company company = companies.getReferenceById(companyId);
        Instant newSince = now.minus(jobRules.newWindow());

        // A feed can list the same role twice; the last copy wins
        Map<String, FetchedJob> incoming = new LinkedHashMap<>();
        fetched.jobs().forEach(f -> incoming.put(f.externalId(), f));

        Map<String, Job> stored = new HashMap<>();
        jobs.findByCompanyId(companyId).forEach(j -> stored.put(j.getExternalId(), j));
        boolean firstImport = stored.isEmpty();

        int added = 0;
        int updated = 0;
        int skipped = 0;
        for (FetchedJob f : incoming.values()) {
            if (!rules.wanted(f.title(), f.team(), f.region())) {
                skipped++;
                continue;
            }
            Job existing = stored.remove(f.externalId());
            if (existing != null) {
                existing.apply(f, now);
                updated++;
            } else if (rules.tooOld(f.postedAt())) {
                skipped++;
            } else {
                boolean baseline = firstImport && (f.postedAt() == null || f.postedAt().isBefore(newSince));
                jobs.save(new Job(company, f, now, baseline));
                added++;
            }
        }
        int closed = 0;
        for (Job missing : stored.values()) {
            // A missing role has left the board only if the feed covered it; a role the rules now leave out
            // (a new search phrase, say) is closed either way
            boolean gone = fetched.covers(missing.getPostedAt())
                    || !rules.wanted(missing.getTitle(), missing.getTeam(), missing.getRegion());
            if (missing.isOpen() && gone) {
                missing.close(now);
                closed++;
            }
        }
        company.recordSync(SyncStatus.OK, now, null);
        return new SyncResult(companyId, company.getName(), SyncStatus.OK, added, updated, closed, skipped, null);
    }

    private SyncResult recordFailure(Company company, SyncStatus status, String error) {
        tx.executeWithoutResult(s -> companies.getReferenceById(company.getId()).recordSync(status, clock.instant(), error));
        return SyncResult.failed(company.getId(), company.getName(), status, error);
    }

    private <T> T whileHoldingLock(Supplier<T> work) {
        if (!running.tryLock()) {
            throw new ConflictException("A sync is already running. Try again in a minute.");
        }
        try {
            return work.get();
        } finally {
            running.unlock();
        }
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
