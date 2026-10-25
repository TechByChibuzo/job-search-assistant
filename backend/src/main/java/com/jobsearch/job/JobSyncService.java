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
import com.jobsearch.job.source.BoardNotFoundException;
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

    /** The scheduler and the "Sync now" button must not reconcile the same company at the same time. */
    private final ReentrantLock running = new ReentrantLock();

    JobSyncService(
            CompanyService companyService,
            CompanyRepository companies,
            JobRepository jobs,
            JobSources sources,
            TransactionTemplate tx,
            Clock clock) {
        this.companyService = companyService;
        this.companies = companies;
        this.jobs = jobs;
        this.sources = sources;
        this.tx = tx;
        this.clock = clock;
    }

    public List<SyncResult> syncAll() {
        return whileHoldingLock(() -> {
            List<SyncResult> results = new ArrayList<>();
            for (Company company : companyService.listActive()) {
                results.add(syncOne(company));
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
        Company company = companyService.get(companyId);
        return whileHoldingLock(() -> syncOne(company));
    }

    private SyncResult syncOne(Company company) {
        List<FetchedJob> fetched;
        try {
            fetched = sources.forType(company.getAts()).fetch(company.getBoard());
        } catch (BoardNotFoundException e) {
            return recordFailure(company, SyncStatus.BOARD_NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Sync failed for {} ({} {})", company.getName(), company.getAts(), company.getBoard(), e);
            return recordFailure(company, SyncStatus.FAILED, describe(e));
        }
        SyncResult result = tx.execute(status -> reconcile(company.getId(), fetched));
        log.info("Synced {}: {} new, {} updated, {} closed",
                company.getName(), result.newJobs(), result.updatedJobs(), result.closedJobs());
        return result;
    }

    private SyncResult reconcile(long companyId, List<FetchedJob> fetched) {
        Instant now = clock.instant();
        Company company = companies.getReferenceById(companyId);

        // A feed can list the same role twice; the last copy wins
        Map<String, FetchedJob> incoming = new LinkedHashMap<>();
        fetched.forEach(f -> incoming.put(f.externalId(), f));

        Map<String, Job> stored = new HashMap<>();
        jobs.findByCompanyId(companyId).forEach(j -> stored.put(j.getExternalId(), j));

        int added = 0;
        int updated = 0;
        for (FetchedJob f : incoming.values()) {
            Job existing = stored.remove(f.externalId());
            if (existing == null) {
                jobs.save(new Job(company, f, now));
                added++;
            } else {
                existing.apply(f, now);
                updated++;
            }
        }
        int closed = 0;
        for (Job gone : stored.values()) {
            if (gone.isOpen()) {
                gone.close(now);
                closed++;
            }
        }
        company.recordSync(SyncStatus.OK, now, null);
        return new SyncResult(companyId, company.getName(), SyncStatus.OK, added, updated, closed, null);
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
