package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jobsearch.job.source.Region;

class IngestRulesTest {

    private static final Instant CUTOFF = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void dropsRolesOutsideTheUsButKeepsUnknownOnes() {
        var rules = new IngestRules(true, CUTOFF, List.of());

        assertThat(rules.wanted("Engineer", null, Region.US)).isTrue();
        assertThat(rules.wanted("Engineer", null, Region.UNKNOWN)).isTrue();
        assertThat(rules.wanted("Engineer", null, Region.NON_US)).isFalse();
        assertThat(new IngestRules(false, CUTOFF, List.of()).wanted("Engineer", null, Region.NON_US)).isTrue();
    }

    @Test
    void aPhraseMatchesWhenEveryWordAppearsInTheTitleOrTeam() {
        var rules = new IngestRules(true, CUTOFF, List.of("software engineer", "backend"));

        assertThat(rules.wanted("Software Development Engineer II", null, Region.US)).isTrue();
        assertThat(rules.wanted("Senior Engineer", "Backend Platform", Region.US)).isTrue();
        assertThat(rules.wanted("Software Sales Lead", null, Region.US)).isFalse();
    }

    @Test
    void ageOnlyCountsWhenThereIsADate() {
        var rules = new IngestRules(true, CUTOFF, List.of());

        assertThat(rules.tooOld(Instant.parse("2026-09-01T00:00:00Z"))).isTrue();
        assertThat(rules.tooOld(Instant.parse("2026-10-01T00:00:00Z"))).isFalse();
        assertThat(rules.tooOld(null)).isFalse();
    }
}
