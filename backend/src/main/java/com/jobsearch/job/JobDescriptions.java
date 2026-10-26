package com.jobsearch.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobsearch.company.AtsType;
import com.jobsearch.job.source.BoardNotFoundException;
import com.jobsearch.job.source.FetchQuery;
import com.jobsearch.job.source.FetchedJob;
import com.jobsearch.job.source.JobSources;

/**
 * Finds the description of a role whose feed left it out, once, when it's needed: when the owner opens the
 * role, or before Claude reads it. Workday and Avature roles are described from their own page on the same
 * site. A role saved from LinkedIn or an alert is looked up by title on the company's own Greenhouse, Lever or
 * Ashby board, when it has one under its name; the app never reads LinkedIn. A failed attempt is remembered and
 * retried after a day.
 */
@Service
public class JobDescriptions {

    static final Duration RETRY_AFTER = Duration.ofDays(1);
    /** A company's own board, once read, is kept this long for its other roles. */
    private static final Duration BOARD_KEPT = Duration.ofHours(1);
    private static final List<AtsType> OWN_BOARDS = List.of(AtsType.GREENHOUSE, AtsType.LEVER, AtsType.ASHBY);
    private static final Logger log = LoggerFactory.getLogger(JobDescriptions.class);

    private final JobRepository jobs;
    private final JobSources sources;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Map<String, Board> boards = new ConcurrentHashMap<>();

    private record Board(List<FetchedJob> jobs, Instant readAt) {}

    private record Role(String title, String company, AtsType ats, String board, String externalId, String url,
            boolean described, Instant checkedAt) {}

    JobDescriptions(JobRepository jobs, JobSources sources, PlatformTransactionManager transactions, Clock clock) {
        this.jobs = jobs;
        this.sources = sources;
        // Its own short transactions, even when called inside a caller's (read-only) one: the result is saved
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Fetches and stores the role's description if it has none and none was sought in the last day. */
    public void ensure(long jobId) {
        Instant now = clock.instant();
        Role role = tx.execute(s -> jobs.findWithCompanyById(jobId).map(j -> new Role(j.getTitle(), j.getCompany().getName(),
                j.getCompany().getAts(), j.getCompany().getBoard(), j.getExternalId(), j.getUrl(),
                j.getDescription() != null && !j.getDescription().isBlank(), j.getDescriptionCheckedAt())).orElse(null));
        if (role == null || role.described() || (role.checkedAt() != null && role.checkedAt().isAfter(now.minus(RETRY_AFTER)))) {
            return;
        }
        // No transaction is held while other sites answer
        String found = null;
        try {
            Optional<String> text = role.ats() != AtsType.MANUAL && role.board() != null
                    ? sources.forType(role.ats()).describe(role.board(), role.externalId(), role.url())
                    : Optional.empty();
            found = text.or(() -> fromOwnBoard(role.company(), role.title(), now)).orElse(null);
        } catch (RuntimeException e) {
            log.info("No description for job {} ({}): {}", jobId, role.company(), e.getMessage());
        }
        String description = found;
        tx.executeWithoutResult(s -> jobs.findById(jobId).ifPresent(j -> j.recordDescription(description, now)));
    }

    /** The same role, by title, on a public board the company has under its own name. */
    private Optional<String> fromOwnBoard(String company, String title, Instant now) {
        String wanted = normalize(title);
        return board(company, now).stream()
                .filter(j -> normalize(j.title()).equals(wanted))
                .map(FetchedJob::description)
                .filter(d -> d != null && !d.isBlank())
                .findFirst();
    }

    private List<FetchedJob> board(String company, Instant now) {
        String key = company.toLowerCase(Locale.ROOT);
        Board cached = boards.get(key);
        if (cached != null && cached.readAt().isAfter(now.minus(BOARD_KEPT))) {
            return cached.jobs();
        }
        List<FetchedJob> found = List.of();
        FetchQuery everything = new FetchQuery(now, Instant.EPOCH, List.of(), false);
        search:
        for (AtsType ats : OWN_BOARDS) {
            for (String slug : slugs(company)) {
                try {
                    found = sources.forType(ats).fetch(slug, everything).jobs();
                    break search;
                } catch (BoardNotFoundException e) {
                    // Not on this system under this name: try the next
                } catch (RuntimeException e) {
                    log.info("Couldn't read {}'s {} board: {}", company, ats, e.getMessage());
                }
            }
        }
        boards.put(key, new Board(found, now));
        return found;
    }

    /** "Stripe" → stripe; "Scale AI" → scaleai, scale-ai: how companies name their boards. */
    static Set<String> slugs(String company) {
        String lower = company.toLowerCase(Locale.ROOT).replace("&", "and").strip();
        Set<String> slugs = new LinkedHashSet<>();
        slugs.add(lower.replaceAll("[^a-z0-9]", ""));
        slugs.add(lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
        slugs.removeIf(String::isBlank);
        return slugs;
    }

    static String normalize(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
