package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobDescriptionsTest {

    @Test
    void guessesTheBoardNamesCompaniesUse() {
        assertThat(JobDescriptions.slugs("Stripe")).containsExactly("stripe");
        assertThat(JobDescriptions.slugs("Scale AI")).containsExactly("scaleai", "scale-ai");
        assertThat(JobDescriptions.slugs("Johnson & Johnson")).containsExactly("johnsonandjohnson", "johnson-and-johnson");
    }

    @Test
    void comparesTitlesByTheirWords() {
        assertThat(JobDescriptions.normalize("Software Engineer, Bridge")).isEqualTo(JobDescriptions.normalize("Software engineer - Bridge"));
    }
}
