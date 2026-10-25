package com.jobsearch.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PursuitTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Test
    void movesForwardOnItsOwnButNeverBack() {
        Pursuit p = new Pursuit(null, PursuitStage.SAVED, NOW);

        p.advanceTo(PursuitStage.REFERRED, NOW);
        p.advanceTo(PursuitStage.REACHING_OUT, NOW);

        assertThat(p.getStage()).isEqualTo(PursuitStage.REFERRED);
    }

    @Test
    void aClosedRoleStaysClosed() {
        Pursuit p = new Pursuit(null, PursuitStage.CLOSED, NOW);

        p.advanceTo(PursuitStage.REFERRED, NOW);

        assertThat(p.getStage()).isEqualTo(PursuitStage.CLOSED);
    }

    @Test
    void theOwnerCanSetAnyStageAndApplyingRecordsTheDate() {
        Pursuit p = new Pursuit(null, PursuitStage.INTERVIEWING, NOW);

        p.setStage(PursuitStage.APPLIED, TODAY, NOW);

        assertThat(p.getStage()).isEqualTo(PursuitStage.APPLIED);
        assertThat(p.getAppliedOn()).isEqualTo(TODAY);
    }
}
