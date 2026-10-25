package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.jobsearch.config.AppProperties;

class AiBudgetTest {

    private static AiBudget budget(int cap, Instant now) {
        var properties = new AppProperties(null, null, null, null, null, null, new AppProperties.Ai("k", "m", 30, ".", cap));
        return new AiBudget(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void refusesRequestsBeyondTheDailyCap() {
        AiBudget b = budget(2, Instant.parse("2026-10-25T10:00:00Z"));
        b.spend();
        b.spend();

        assertThat(b.usedToday()).isEqualTo(2);
        assertThatThrownBy(b::spend).isInstanceOf(AiException.class).hasMessageContaining("limit of 2");
    }

    @Test
    void theAllowanceResetsEachDay() {
        var properties = new AppProperties(null, null, null, null, null, null, new AppProperties.Ai("k", "m", 30, ".", 1));
        var clock = new MutableClock(Instant.parse("2026-10-25T23:00:00Z"));
        AiBudget b = new AiBudget(properties, clock);
        b.spend();

        clock.now = Instant.parse("2026-10-26T01:00:00Z");
        b.spend();

        assertThat(b.usedToday()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
