package com.jobsearch.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AtsDetectorTest {

    private final AtsDetector detector = new AtsDetector();

    @ParameterizedTest
    @CsvSource({
        "https://boards.greenhouse.io/airbnb, GREENHOUSE, airbnb",
        "https://job-boards.greenhouse.io/airbnb/jobs/7789554, GREENHOUSE, airbnb",
        "https://boards.greenhouse.io/embed/job_board?for=Stripe, GREENHOUSE, stripe",
        "https://boards-api.greenhouse.io/v1/boards/airbnb/jobs, GREENHOUSE, airbnb",
        "https://jobs.lever.co/palantir, LEVER, palantir",
        "jobs.lever.co/spotify/2a55d6c9-c564-443a-be3e-a3851db5a93b, LEVER, spotify",
        "https://api.lever.co/v0/postings/spotify?mode=json, LEVER, spotify",
        "https://jobs.ashbyhq.com/openai, ASHBY, openai",
        "https://JOBS.ASHBYHQ.COM/OpenAI/240d459b, ASHBY, openai",
        "https://api.ashbyhq.com/posting-api/job-board/openai, ASHBY, openai",
    })
    void recognisesBoardLinks(String url, AtsType ats, String board) {
        assertThat(detector.detect(url)).contains(new DetectedBoard(ats, board));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "not a url at all",
        "https://careers.airbnb.com/positions/7789554",
        "https://www.linkedin.com/jobs/view/123",
        "https://boards.greenhouse.io/",
        "https://boards.greenhouse.io/embed/job_board",
        "https://jobs.lever.co/<script>",
        "https://myworkdayjobs.com/en-US/External",
    })
    void rejectsLinksItCannotRead(String url) {
        assertThat(detector.detect(url)).isEmpty();
    }
}
