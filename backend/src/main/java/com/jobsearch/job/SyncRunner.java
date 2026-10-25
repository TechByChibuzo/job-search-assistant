package com.jobsearch.job;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jobsearch.common.ConflictException;
import com.jobsearch.company.Company;
import com.jobsearch.company.SyncStatus;

/**
 * Runs syncs in the background. A full sync pages through search feeds politely and can take minutes, which
 * is too long to hold an HTTP request open, so the API starts a run and the UI polls its progress.
 *
 * <p>State is kept in memory: right for one instance. Running several instances would need the run state
 * and a lock in the database (see docs/architecture.md, "Deployment").
 */
@Component
public class SyncRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final JobSyncService sync;
    private final List<ExtraFeed> extraFeeds;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "job-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile SyncProgress state = SyncProgress.idle();

    SyncRunner(JobSyncService sync, List<ExtraFeed> extraFeeds, Clock clock) {
        this.sync = sync;
        this.extraFeeds = extraFeeds;
        this.clock = clock;
    }

    public SyncProgress current() {
        return state;
    }

    /** Starts syncing every active company, or one when {@code companyId} is given. */
    public synchronized SyncProgress start(Long companyId) {
        if (state.running()) {
            throw new ConflictException("A sync is already running.");
        }
        state = SyncProgress.started(clock.instant());
        Tracker tracker = new Tracker();
        executor.submit(() -> {
            try {
                if (companyId == null) {
                    runAll(tracker);
                } else {
                    sync.sync(companyId, tracker);
                }
                update(s -> s.finish(clock.instant(), null));
            } catch (RuntimeException e) {
                log.warn("Sync run failed", e);
                update(s -> s.finish(clock.instant(), e.getMessage()));
            }
        });
        return state;
    }

    /** Runs one extra feed on its own, such as reading job-alert emails now. */
    public synchronized SyncProgress startFeed(String name) {
        ExtraFeed feed = extraFeeds.stream().filter(f -> f.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No feed named " + name));
        if (state.running()) {
            throw new ConflictException("A sync is already running.");
        }
        state = SyncProgress.started(clock.instant());
        executor.submit(() -> {
            try {
                runFeed(feed);
                update(s -> s.finish(clock.instant(), null));
            } catch (RuntimeException e) {
                update(s -> s.finish(clock.instant(), e.getMessage()));
            }
        });
        return state;
    }

    /** Runs a full sync on the caller's thread, for the scheduler; still visible to the UI as it goes. */
    void runNow() {
        synchronized (this) {
            if (state.running()) {
                return;
            }
            state = SyncProgress.started(clock.instant());
        }
        try {
            runAll(new Tracker());
            update(s -> s.finish(clock.instant(), null));
        } catch (RuntimeException e) {
            update(s -> s.finish(clock.instant(), e.getMessage()));
        }
    }

    /** Every company, then every extra feed that is set up, each reported as it finishes. */
    private void runAll(Tracker tracker) {
        sync.syncAll(tracker);
        for (ExtraFeed feed : extraFeeds) {
            if (feed.enabled()) {
                runFeed(feed);
            }
        }
    }

    private void runFeed(ExtraFeed feed) {
        update(s -> s.at(feed.name(), s.done(), s.total() + 1));
        SyncResult result;
        try {
            result = feed.run();
        } catch (RuntimeException e) {
            log.warn("{} failed", feed.name(), e);
            result = SyncResult.failed(null, feed.name(), SyncStatus.FAILED, e.getMessage());
        }
        SyncResult done = result;
        update(s -> s.with(done));
    }

    private synchronized void update(UnaryOperator<SyncProgress> change) {
        state = change.apply(state);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    private class Tracker implements JobSyncService.Progress {

        @Override
        public void starting(Company company, int index, int total) {
            update(s -> s.at(company.getName(), index, total));
        }

        @Override
        public void finished(SyncResult result) {
            update(s -> s.with(result));
        }
    }

    /**
     * @param current the company being synced now, or null
     * @param done    companies finished so far in this run
     * @param error   why the run stopped early, if it did; per-company failures are in {@code results}
     */
    public record SyncProgress(
            boolean running,
            Instant startedAt,
            Instant finishedAt,
            String current,
            int done,
            int total,
            List<SyncResult> results,
            String error) {

        static SyncProgress idle() {
            return new SyncProgress(false, null, null, null, 0, 0, List.of(), null);
        }

        static SyncProgress started(Instant at) {
            return new SyncProgress(true, at, null, null, 0, 0, List.of(), null);
        }

        SyncProgress at(String company, int index, int total) {
            return new SyncProgress(true, startedAt, null, company, index, total, results, null);
        }

        SyncProgress with(SyncResult result) {
            List<SyncResult> all = new ArrayList<>(results);
            all.add(result);
            return new SyncProgress(true, startedAt, null, current, all.size(), Math.max(total, all.size()), List.copyOf(all), null);
        }

        SyncProgress finish(Instant at, String error) {
            return new SyncProgress(false, startedAt, at, null, done, total, results, error);
        }
    }
}
