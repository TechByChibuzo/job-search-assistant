package com.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AlertParsersTest {

    private final LinkedInAlertParser linkedIn = new LinkedInAlertParser();
    private final CareersSiteAlertParser careers = new CareersSiteAlertParser();

    @Test
    void readsLinkedInAlertCardsWithCompanyAndPlace() {
        AlertEmail email = email("LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>", read("linkedin.html"));

        assertThat(linkedIn.handles(email)).isTrue();
        assertThat(linkedIn.parse(email)).containsExactly(
                new AlertJob("https://www.linkedin.com/jobs/view/4012345678/", "Software Engineer II", "Microsoft", "Redmond, WA (Hybrid)"),
                new AlertJob("https://www.linkedin.com/jobs/view/4087654321/", "Software Engineer, Infrastructure", "Meta", "Menlo Park, CA"),
                new AlertJob("https://www.linkedin.com/jobs/view/4011112222/", "Backend Engineer", "Shopify", "Toronto, Ontario, Canada"));
    }

    @Test
    void readsJobLinksFromACompanysOwnAlertAndNamesTheCompanyFromTheSender() {
        AlertEmail email = email("Microsoft Careers <careers-noreply@microsoft.com>", read("careers-site.html"));

        assertThat(linkedIn.handles(email)).isFalse();
        assertThat(careers.parse(email)).containsExactly(
                new AlertJob("https://jobs.careers.microsoft.com/global/en/job/1789012/Software-Engineer", "Software Engineer", "Microsoft", null),
                new AlertJob("https://jobs.careers.microsoft.com/global/en/job/1789013/Senior-Software-Engineer", "Senior Software Engineer", "Microsoft", null));
    }

    @Test
    void namesTheCompanyFromTheDisplayNameWhenTheDomainIsUnknown() {
        assertThat(CareersSiteAlertParser.company(email("\"Stripe Careers\" <jobs@greenhouse-mail.io>", ""))).isEqualTo("Stripe");
        assertThat(CareersSiteAlertParser.company(email("no-reply@acme.co.uk", ""))).isEqualTo("Co");
        assertThat(CareersSiteAlertParser.company(email("Talent Team <talent@figma.com>", ""))).isEqualTo("Figma");
    }

    @Test
    void anEmailWithNoJobLinksHasNoRoles() {
        AlertEmail newsletter = email("News <news@example.com>",
                "<p>Read our <a href=\"https://example.com/blog/2026/10/post-1234\">blog</a> and <a href=\"https://example.com/about\">about us</a></p>");

        assertThat(careers.parse(newsletter)).isEmpty();
    }

    private static AlertEmail email(String from, String html) {
        return new AlertEmail("m1", from, "New jobs", Instant.parse("2026-10-25T13:00:00Z"), html, null);
    }

    private static String read(String name) {
        try {
            return new ClassPathResource("fixtures/alerts/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
