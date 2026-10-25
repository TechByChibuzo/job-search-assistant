package com.jobsearch.alerts;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobsearch.company.SyncStatus;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.ExtraFeed;
import com.jobsearch.job.ManualJobService;
import com.jobsearch.job.SyncResult;
import com.jobsearch.job.source.Region;

/**
 * Reads new job-alert emails from the owner's Gmail label and adds their roles to the inbox, as one more
 * feed in the daily sync. Each email is read once; roles outside the US are skipped like any other.
 */
@Component
class AlertImporter implements ExtraFeed {

    static final String NAME = "Job alert emails";
    private static final Logger log = LoggerFactory.getLogger(AlertImporter.class);

    private final GmailAuth auth;
    private final GmailApi gmail;
    private final List<AlertParser> parsers;
    private final ManualJobService jobs;
    private final ImportedEmailRepository imported;
    private final GmailAccountRepository accounts;
    private final TransactionTemplate tx;
    private final AppProperties properties;
    private final Clock clock;

    AlertImporter(GmailAuth auth, GmailApi gmail, List<AlertParser> parsers, ManualJobService jobs,
            ImportedEmailRepository imported, GmailAccountRepository accounts, TransactionTemplate tx,
            AppProperties properties, Clock clock) {
        this.auth = auth;
        this.gmail = gmail;
        this.parsers = parsers;
        this.jobs = jobs;
        this.imported = imported;
        this.accounts = accounts;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return auth.configured() && auth.account().isPresent();
    }

    @Override
    public SyncResult run() {
        Instant now = clock.instant();
        int days = (int) Math.max(1, properties.jobs().maxAge().toDays());
        List<AlertEmail> emails;
        try {
            emails = gmail.alertEmails(auth.accessToken(), properties.gmail().label(), days, imported::existsById);
        } catch (RuntimeException e) {
            String error = e.getMessage();
            tx.executeWithoutResult(s -> accounts.findById(GmailAccount.ID).ifPresent(a -> a.recordFailure(now, error)));
            return SyncResult.failed(null, NAME, SyncStatus.FAILED, error);
        }
        return importAll(emails, now);
    }

    /** Reads the roles out of each email and saves the new ones. Public to the package for tests. */
    SyncResult importAll(List<AlertEmail> emails, Instant now) {
        boolean usOnly = properties.jobs().usOnly();
        int added = 0;
        int skipped = 0;
        int unreadable = 0;
        for (AlertEmail email : emails) {
            List<AlertJob> found = parse(email);
            if (found.isEmpty()) {
                unreadable++;
                log.info("No roles found in alert email from {}: {}", email.from(), email.subject());
            }
            for (AlertJob job : found) {
                if (usOnly && Region.fromLocation(job.location()) == Region.NON_US) {
                    skipped++;
                    continue;
                }
                boolean isNew = jobs.addFromAlert(job.url(), job.title(), job.company(), job.location(), email.receivedAt()).isPresent();
                if (isNew) {
                    added++;
                } else {
                    skipped++;
                }
            }
            imported.save(new ImportedEmail(email, now, found.size()));
        }
        int read = emails.size();
        int addedTotal = added;
        int unreadableTotal = unreadable;
        tx.executeWithoutResult(s -> accounts.findById(GmailAccount.ID).ifPresent(a -> a.recordImport(now, read, addedTotal, unreadableTotal)));
        log.info("Read {} alert emails: {} roles added, {} skipped, {} emails with no readable roles", read, added, skipped, unreadable);
        return new SyncResult(null, NAME, SyncStatus.OK, added, 0, 0, skipped, null);
    }

    /** The first parser that handles the sender; the careers-site parser handles everyone else. */
    private List<AlertJob> parse(AlertEmail email) {
        for (AlertParser parser : parsers) {
            if (parser.handles(email)) {
                return parser.parse(email);
            }
        }
        return List.of();
    }
}
