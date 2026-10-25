package com.jobsearch.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.ConflictException;

class OutreachTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final Instant AT = Instant.parse("2026-10-19T15:00:00Z");

    private Outreach sentMonday() {
        return Outreach.sent(null, null, OutreachChannel.LINKEDIN, "Hi", AT, MON, 5);
    }

    @Test
    void aSentMessageWaitsForAReplyAndSchedulesAFollowUp() {
        Outreach o = sentMonday();

        assertThat(o.getStatus()).isEqualTo(OutreachStatus.AWAITING_REPLY);
        assertThat(o.getFollowUpOn()).isEqualTo(MON.plusDays(5));
        assertThat(o.isFollowUpDue(MON.plusDays(4))).isFalse();
        assertThat(o.isFollowUpDue(MON.plusDays(5))).isTrue();
        assertThat(o.getEvents()).extracting(OutreachEvent::getType).containsExactly(OutreachEventType.SENT);
    }

    @Test
    void aFollowUpCountsAndPushesTheReminderOut() {
        Outreach o = sentMonday();
        LocalDate sat = MON.plusDays(5);

        o.record(OutreachEventType.FOLLOW_UP, null, AT.plusSeconds(5 * 86400), sat, 5);

        assertThat(o.getFollowUps()).isEqualTo(1);
        assertThat(o.getFollowUpOn()).isEqualTo(sat.plusDays(5));
        assertThat(o.getLastContactedAt()).isEqualTo(AT.plusSeconds(5 * 86400));
    }

    @Test
    void aReplyEndsTheReminders() {
        Outreach o = sentMonday();

        o.record(OutreachEventType.REPLIED, "Happy to chat", AT, MON, 5);

        assertThat(o.getStatus()).isEqualTo(OutreachStatus.REPLIED);
        assertThat(o.getFollowUpOn()).isNull();
        assertThat(o.isFollowUpDue(MON.plusDays(30))).isFalse();
        assertThat(o.getEvents().get(1).getNote()).isEqualTo("Happy to chat");
    }

    @Test
    void thereIsNothingToFollowUpOnceItHasMovedOn() {
        Outreach o = sentMonday();
        o.record(OutreachEventType.NO_REPLY, null, AT, MON, 5);

        assertThatThrownBy(() -> o.record(OutreachEventType.FOLLOW_UP, null, AT, MON, 5)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aLateReplyStillCounts() {
        Outreach o = sentMonday();
        o.record(OutreachEventType.NO_REPLY, null, AT, MON, 5);

        o.record(OutreachEventType.REFERRED, null, AT, MON, 5);

        assertThat(o.getStatus()).isEqualTo(OutreachStatus.REFERRED);
    }

    @Test
    void aNoteNeedsTextAndAConversationStartsOnce() {
        Outreach o = sentMonday();

        assertThatThrownBy(() -> o.record(OutreachEventType.NOTE, " ", AT, MON, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> o.record(OutreachEventType.SENT, null, AT, MON, 5)).isInstanceOf(BadRequestException.class);
    }
}
