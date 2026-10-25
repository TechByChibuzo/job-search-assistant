package com.jobsearch.answers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionMatcherTest {

    @Test
    void theSameQuestionAskedDifferentlyMatches() {
        assertThat(QuestionMatcher.similarity("Why do you want to work at Nooks?", "Why do you want to work here?"))
                .isGreaterThanOrEqualTo(QuestionMatcher.THRESHOLD);
        assertThat(QuestionMatcher.similarity("Tell us about a project you're proud of", "Describe a project you are proud of."))
                .isGreaterThanOrEqualTo(QuestionMatcher.THRESHOLD);
    }

    @Test
    void differentQuestionsDoNot() {
        assertThat(QuestionMatcher.similarity("Why do you want to work here?", "Tell us about a project you're proud of"))
                .isLessThan(QuestionMatcher.THRESHOLD);
        assertThat(QuestionMatcher.similarity("What are your salary expectations?", "Why do you want to work here?"))
                .isLessThan(QuestionMatcher.THRESHOLD);
        assertThat(QuestionMatcher.similarity("", "Why?")).isZero();
    }
}
