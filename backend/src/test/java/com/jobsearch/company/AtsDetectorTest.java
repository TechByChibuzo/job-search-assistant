package com.jobsearch.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AtsDetectorTest {

    @Test
    void checksBoardsTypedByHand() {
        assertThat(detector.normalize(AtsType.LEVER, " Palantir ")).contains(new DetectedBoard(AtsType.LEVER, "palantir"));
        assertThat(detector.normalize(AtsType.WORKDAY, "NVIDIA.wd5/NVIDIAExternalCareerSite"))
                .contains(new DetectedBoard(AtsType.WORKDAY, "nvidia.wd5/NVIDIAExternalCareerSite"));
        assertThat(detector.normalize(AtsType.WORKDAY, "nvidia")).isEmpty();
        assertThat(detector.normalize(AtsType.AVATURE, "bloomberg")).isEmpty();
        assertThat(detector.normalize(AtsType.AMAZON, "")).isEmpty();
    }

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
        "https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite, WORKDAY, nvidia.wd5/NVIDIAExternalCareerSite",
        "https://salesforce.wd12.myworkdayjobs.com/External_Career_Site/job/CA/Engineer_JR1, WORKDAY, salesforce.wd12/External_Career_Site",
        "https://Adobe.wd5.myworkdayjobs.com/wday/cxs/adobe/external_experienced/jobs, WORKDAY, adobe.wd5/external_experienced",
        "https://bloomberg.avature.net/careers, AVATURE, bloomberg/careers",
        "https://bloomberg.avature.net/careers/JobDetail/Engineer/45808, AVATURE, bloomberg/careers",
        "https://www.amazon.jobs/en/search?base_query=engineer, AMAZON, amazon",
        "amazon.jobs, AMAZON, amazon",
        "https://www.google.com/about/careers/applications/jobs/results, GOOGLE, google",
        "https://careers.google.com/jobs/results/, GOOGLE, google",
        "https://higher.gs.com/results?search=engineer, GOLDMAN, goldman",
        "https://www.goldmansachs.com/careers/, GOLDMAN, goldman",
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
        "https://nvidia.wd5.myworkdayjobs.com/",
        "https://www.google.com/search?q=jobs",
        "https://careers.microsoft.com/v2/global/en/home.html",
    })
    void rejectsLinksItCannotRead(String url) {
        assertThat(detector.detect(url)).isEmpty();
    }
}
