package com.jobsearch.tracker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.contact.ContactKind;

/**
 * What the owner's outreach shows so far: how often people reply and refer, and how that varies by channel,
 * by who was messaged, by message length, by whether Claude drafted it, and by follow-ups. Rates count only
 * conversations that have had a fair chance to get a reply: answered ones, and unanswered ones older than
 * a week. Groups too small to mean much are marked so.
 */
@Service
public class InsightsService {

    /** A conversation younger than this, still unanswered, is left out of rates: it may yet get a reply. */
    static final Duration SETTLED_AFTER = Duration.ofDays(7);
    /** Fewer conversations than this in a group, and its rate is shown as an early signal only. */
    static final int ENOUGH = 5;
    /** LinkedIn's connection-note limit: anything this short reads as a note, not a message. */
    static final int SHORT = 300;

    private final OutreachRepository outreach;
    private final PursuitRepository pursuits;
    private final Clock clock;

    InsightsService(OutreachRepository outreach, PursuitRepository pursuits, Clock clock) {
        this.outreach = outreach;
        this.pursuits = pursuits;
        this.clock = clock;
    }

    /** One group's numbers. {@code rate} is replies over settled conversations, null when none are settled. */
    public record Group(String label, int sent, int settled, int replied, int referred, Double replyRate, Double referralRate,
            boolean enough) {}

    public record Insights(
            Group overall,
            Double medianDaysToReply,
            List<Group> byChannel,
            List<Group> byWho,
            List<Group> byLength,
            List<Group> byDrafted,
            List<Group> repliesByFollowUps,
            Map<String, Long> roleStages,
            long appliedLast7Days) {}

    @Transactional(readOnly = true)
    public Insights insights() {
        Instant now = clock.instant();
        List<Outreach> all = outreach.findAllByOrderByLastContactedAtDesc();
        all.forEach(o -> o.getEvents().size());

        List<Double> daysToReply = new ArrayList<>();
        for (Outreach o : all) {
            firstReply(o).ifPresent(at -> daysToReply.add(Duration.between(o.getFirstSentAt(), at).toHours() / 24.0));
        }

        Map<String, Long> stages = new LinkedHashMap<>();
        Arrays.stream(PursuitStage.values()).forEach(s -> stages.put(s.name(), 0L));
        LocalDate weekAgo = LocalDate.now(clock).minusDays(7);
        long applied = 0;
        for (Pursuit p : pursuits.findAll()) {
            stages.merge(p.getStage().name(), 1L, Long::sum);
            if (p.getAppliedOn() != null && !p.getAppliedOn().isBefore(weekAgo)) {
                applied++;
            }
        }

        return new Insights(
                group("All conversations", all, now),
                median(daysToReply),
                by(all, now, o -> o.getChannel() == OutreachChannel.LINKEDIN ? "LinkedIn" : o.getChannel() == OutreachChannel.EMAIL ? "Email" : "Other"),
                by(all, now, o -> who(o.getContact().getKind())),
                by(all, now, o -> o.getMessage() == null ? "Not recorded" : o.getMessage().length() <= SHORT ? "Short note" : "Longer message"),
                by(all, now, o -> o.isDrafted() ? "Drafted with Claude" : "Written yourself"),
                repliesByFollowUps(all, now),
                stages,
                applied);
    }

    private static String who(ContactKind kind) {
        return switch (kind) {
            case ALUMNI -> "Alumni";
            case RECRUITER -> "Recruiters";
            case ENGINEER -> "Engineers";
            case HIRING_MANAGER -> "Hiring managers";
            case OTHER -> "Others";
        };
    }

    private List<Group> by(List<Outreach> all, Instant now, Function<Outreach, String> key) {
        Map<String, List<Outreach>> groups = new LinkedHashMap<>();
        for (Outreach o : all) {
            groups.computeIfAbsent(key.apply(o), k -> new ArrayList<>()).add(o);
        }
        return groups.entrySet().stream()
                .map(e -> group(e.getKey(), e.getValue(), now))
                .sorted((a, b) -> Integer.compare(b.sent(), a.sent()))
                .toList();
    }

    /** Of the conversations that got a reply, how many follow-ups it took. */
    private List<Group> repliesByFollowUps(List<Outreach> all, Instant now) {
        Map<String, List<Outreach>> groups = new LinkedHashMap<>();
        groups.put("No follow-up", new ArrayList<>());
        groups.put("After 1 follow-up", new ArrayList<>());
        groups.put("After 2 or more", new ArrayList<>());
        for (Outreach o : all) {
            String k = o.getFollowUps() == 0 ? "No follow-up" : o.getFollowUps() == 1 ? "After 1 follow-up" : "After 2 or more";
            groups.get(k).add(o);
        }
        return groups.entrySet().stream().map(e -> group(e.getKey(), e.getValue(), now)).toList();
    }

    static Group group(String label, List<Outreach> conversations, Instant now) {
        int settled = 0;
        int replied = 0;
        int referred = 0;
        for (Outreach o : conversations) {
            boolean answered = o.getStatus() != OutreachStatus.AWAITING_REPLY && o.getStatus() != OutreachStatus.NO_REPLY;
            boolean old = Duration.between(o.getFirstSentAt(), now).compareTo(SETTLED_AFTER) >= 0;
            if (answered || old || o.getStatus() == OutreachStatus.NO_REPLY) {
                settled++;
            }
            if (answered) {
                replied++;
            }
            if (o.getStatus() == OutreachStatus.REFERRED) {
                referred++;
            }
        }
        Double replyRate = settled == 0 ? null : (double) replied / settled;
        Double referralRate = settled == 0 ? null : (double) referred / settled;
        return new Group(label, conversations.size(), settled, replied, referred, replyRate, referralRate, settled >= ENOUGH);
    }

    private static Optional<Instant> firstReply(Outreach o) {
        return o.getEvents().stream()
                .filter(e -> e.getType() == OutreachEventType.REPLIED || e.getType() == OutreachEventType.REFERRED
                        || e.getType() == OutreachEventType.DECLINED)
                .map(OutreachEvent::getAt)
                .findFirst();
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }
}
