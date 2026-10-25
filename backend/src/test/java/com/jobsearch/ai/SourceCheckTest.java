package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceCheckTest {

    private static final String RESUME = """
            Jane Doe — jane@example.com
            Oracle, Software Engineer, 2021–2024
            • Built REST APIs in Java and Spring Boot serving 3,000 requests per second
            • Cut query latency by 40% by adding PostgreSQL indexes
            Skills: Java, Spring Boot, React, PostgreSQL, AWS (ECS, RDS), Docker
            """;

    private final SourceCheck check = new SourceCheck(RESUME);

    @Test
    void aRewordedBulletWithARealSourcePasses() {
        assertThat(check.checkBullet("Designed Java/Spring Boot REST APIs handling 3,000 requests per second",
                "Built REST APIs in Java and Spring Boot serving 3,000 requests per second")).isEmpty();
    }

    @Test
    void anInventedSourceIsFlagged() {
        assertThat(check.checkBullet("Led a team of engineers", "Led a team of 8 engineers building Kubernetes operators"))
                .contains("Its source wasn't found in your resume");
    }

    @Test
    void anInventedNumberIsFlaggedEvenWithARealSource() {
        assertThat(check.checkBullet("Cut query latency by 60% with PostgreSQL indexes",
                "Cut query latency by 40% by adding PostgreSQL indexes")).containsExactly("\"60%\" isn't in your resume");
    }

    @Test
    void yearsAndSmallCountsArentTreatedAsMetrics() {
        assertThat(check.checkNumbers("Since 2021, across 3 teams")).isEmpty();
    }

    @Test
    void sourcesSurviveLineBreaksAndPunctuationFromThePdf() {
        assertThat(check.quoteFound("Cut query latency by 40%\nby adding PostgreSQL indexes.")).isTrue();
    }

    @Test
    void knowsWhichSkillsTheResumeNames() {
        assertThat(check.mentions("Spring Boot")).isTrue();
        assertThat(check.mentions("aws")).isTrue();
        assertThat(check.mentions("Kubernetes")).isFalse();
    }

    @Test
    void withoutResumeTextNothingCanBeCheckedAndItSaysSo() {
        SourceCheck none = new SourceCheck("");

        assertThat(none.available()).isFalse();
        assertThat(none.checkBullet("Anything", "Anything at all")).isEmpty();
    }
}
