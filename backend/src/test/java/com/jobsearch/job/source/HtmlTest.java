package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTest {

    @Test
    void keepsParagraphAndListBreaks() {
        String html = "<p>About the team</p><p>We build&nbsp;things.</p><ul><li>Java</li><li>Postgres &amp; SQL</li></ul>";

        assertThat(Html.toText(html)).isEqualTo("About the team\nWe build things.\n• Java\n• Postgres & SQL");
    }

    @Test
    void unescapesGreenhouseContentBeforeParsing() {
        String escaped = "&lt;div&gt;&lt;p&gt;Hello &amp;amp; welcome&lt;/p&gt;&lt;/div&gt;";

        assertThat(Html.escapedToText(escaped)).isEqualTo("Hello & welcome");
    }

    @Test
    void dropsScriptsAndAttributes() {
        assertThat(Html.toText("<p style=\"x\">Hi<script>alert(1)</script></p>")).isEqualTo("Hi");
    }

    @Test
    void emptyInputGivesNull() {
        assertThat(Html.toText("  ")).isNull();
        assertThat(Html.escapedToText(null)).isNull();
    }
}
