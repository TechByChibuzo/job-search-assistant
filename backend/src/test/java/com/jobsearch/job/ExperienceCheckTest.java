package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExperienceCheckTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Basic qualifications - 3+ years of non-internship professional software development experience - Experience programming with at least one language | 3
            Basic qualifications - 5+ years of non-internship professional software development experience | 5
            • 8 years of experience in software development. • Bachelor's degree or equivalent practical experience. | 8
            • Must have: 9+ years of hands-on software engineering experience building, deploying and operating systems | 9
            • Minimum of five years of professional experience in machine learning, quantitative research | 5
            • A minimum of 3+ years of industry experience building, training, and deploying models | 3
            • 2 years of experience with software development or 1 year of experience with an advanced degree | 1
            Bachelor's degree and 8 years of experience, or Master's degree and 6 years of experience, or PhD and 3 years of experience. | 3
            We're looking for 3-5 years of backend experience. | 3
            You have 7 or more years of relevant experience. | 7
            """)
    void readsTheFewestYearsAPostingAsksFor(String description, int years) {
        assertThat(ExperienceCheck.check("Software Engineer", description).minYears()).isEqualTo(years);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "NVIDIA has been transforming computer graphics, PC gaming, and accelerated computing for more than 25 years.",
            "You'll build how productivity tools should work 5-10 years into the future.",
            "We plan to grow the business 10 times over the next 2 years.",
            "Experience with distributed systems is a plus.",
    })
    void ignoresYearsThatAreNotAboutExperience(String description) {
        assertThat(ExperienceCheck.check("Software Engineer", description).minYears()).isNull();
    }

    @Test
    void keepsTheSentenceItReadTheYearsFrom() {
        var result = ExperienceCheck.check("Software Engineer", "Build things. • 8 years of experience in software development. • A degree.");
        assertThat(result.evidence()).isEqualTo("8 years of experience in software development.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Principal Software Engineer", "Staff Engineer, Payments", "Senior Software Engineer - GPU", "Sr. Backend Engineer",
            "Engineering Manager", "Software Development Manager", "Director of Engineering", "Tech Lead, Platform",
            "Distinguished Engineer - Data Center", "Software Development Engineer III", "Solutions Architect"})
    void recognisesSeniorTitles(String title) {
        assertThat(ExperienceCheck.check(title, null).seniorTitle()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Software Engineer, New Grad", "Software Development Engineer II", "Software Engineer - Early Career",
            "Backend Engineer", "Leadership Development Program Engineer", "Software Engineer, Staffing Platform"})
    void leavesOtherTitlesAlone(String title) {
        assertThat(ExperienceCheck.check(title, null).seniorTitle()).isFalse();
    }

    @Test
    void aLevelNumberDefersToTheYearsThePostingStates() {
        assertThat(ExperienceCheck.check("Software Engineer III, Technical Infrastructure",
                "• 2 years of experience with software development in one or more programming languages.").seniorTitle()).isFalse();
        assertThat(ExperienceCheck.check("Software Engineer III", null).seniorTitle()).isTrue();
    }
}
