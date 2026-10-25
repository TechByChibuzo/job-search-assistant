package com.jobsearch.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class InsightsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private static Outreach sent(int daysAgo, OutreachEventType... outcome) {
        Instant at = NOW.minus(Duration.ofDays(daysAgo));
        Outreach o = Outreach.sent(null, null, OutreachChannel.LINKEDIN, "Hi", at, LocalDate.of(2026, 10, 1), 5);
        for (OutreachEventType t : outcome) {
            o.record(t, null, at.plus(Duration.ofDays(1)), LocalDate.of(2026, 10, 2), 5);
        }
        return o;
    }

    @Test
    void ratesCountOnlyConversationsThatHaveHadAChance() {
        var group = InsightsService.group("All", List.of(
                sent(20, OutreachEventType.REPLIED),
                sent(20, OutreachEventType.REFERRED),
                sent(20),                       // old and unanswered: counts as no reply
                sent(2)),                       // fresh: left out of the rate
                NOW);

        assertThat(group.sent()).isEqualTo(4);
        assertThat(group.settled()).isEqualTo(3);
        assertThat(group.replied()).isEqualTo(2);
        assertThat(group.referred()).isEqualTo(1);
        assertThat(group.replyRate()).isEqualTo(2.0 / 3);
        assertThat(group.enough()).isFalse();
    }

    @Test
    void aDeclineIsStillAReplyAndGivingUpIsNot() {
        var group = InsightsService.group("All", List.of(sent(1, OutreachEventType.DECLINED), sent(1, OutreachEventType.NO_REPLY)), NOW);

        assertThat(group.settled()).isEqualTo(2);
        assertThat(group.replied()).isEqualTo(1);
    }

    @Test
    void medianOfOddAndEvenLists() {
        assertThat(InsightsService.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
        assertThat(InsightsService.median(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(2.5);
        assertThat(InsightsService.median(List.of())).isNull();
    }
}
