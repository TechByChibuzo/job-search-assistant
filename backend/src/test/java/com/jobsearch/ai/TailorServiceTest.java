package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class TailorServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final TailorService service = new TailorService(null, null, null, json);
    private final SourceCheck check = new SourceCheck("""
            Experience. Software Engineer, July 2022 – September 2025, Oracle Corporation, Austin, TX.
            Built REST APIs in Java and Spring Boot. Cut query latency by 40% with indexes.
            Technical Skills. Languages: Java, Python. Developer Tools: PostgreSQL, Docker""");

    @Test
    void flagsUnsupportedBulletsChangedHeadersAndDropsSkillsTheResumeDoesntName() throws Exception {
        JsonNode answer = json.readTree("""
                {"name": "Jane Doe", "contact": ["jane@example.com"],
                 "sections": [
                   {"heading": "Experience", "skillGroups": [], "entries": [
                     {"title": "Software Engineer", "detail": "", "titleRight": "July 2022 – September 2025",
                      "subtitle": "Oracle Corporation", "subtitleRight": "Austin, TX", "bullets": [
                       {"text": "Built **Java** and **Spring Boot** REST APIs", "source": "Built REST APIs in Java and Spring Boot"},
                       {"text": "Cut query latency by **75%**", "source": "Cut query latency by 40% with indexes"},
                       {"text": "Mentored 4 interns", "source": "Mentored 4 interns over two summers"}]},
                     {"title": "Senior Software Engineer", "detail": "", "titleRight": "2021 – 2024",
                      "subtitle": "Oracle Corporation", "subtitleRight": "Austin, TX", "bullets": []}]},
                   {"heading": "Technical Skills", "entries": [], "skillGroups": [
                     {"label": "Languages", "items": ["Java", "Rust", "Python"]},
                     {"label": "Developer Tools", "items": ["Docker", "Kubernetes"]}]}]}""");

        JsonNode doc = service.checkResume(answer, check);

        JsonNode entries = doc.path("sections").get(0).path("entries");
        JsonNode bullets = entries.get(0).path("bullets");
        assertThat(entries.get(0).path("problems")).isEmpty();
        assertThat(bullets.get(0).path("problems")).isEmpty();
        assertThat(bullets.get(1).path("problems").toString()).contains("75%");
        assertThat(bullets.get(2).path("problems").toString()).contains("source wasn't found");
        // A header must be copied as written: a new title or dates are caught
        assertThat(entries.get(1).path("problems").toString()).contains("Senior Software Engineer", "2021 – 2024");
        assertThat(doc.path("flagged").asInt()).isEqualTo(3);

        JsonNode groups = doc.path("sections").get(1).path("skillGroups");
        assertThat(groups.get(0).path("items").toString()).isEqualTo("[\"Java\",\"Python\"]");
        assertThat(groups.get(1).path("items").toString()).isEqualTo("[\"Docker\"]");
        assertThat(doc.path("droppedSkills").toString()).contains("Rust", "Kubernetes");
        assertThat(doc.path("checked").asBoolean()).isTrue();
    }

    @Test
    void dropsContactDetailsTheResumeDoesntHave() throws Exception {
        SourceCheck withContact = new SourceCheck("""
                Jane Doe. jane@example.com | github.com/janedoe. Experience. Software Engineer at Oracle Corporation.""");
        JsonNode answer = json.readTree("""
                {"name": "Jane Doe", "contact": ["jane@example.com", "github.com/janedoe", "evil.example/login"], "sections": []}""");

        JsonNode doc = service.checkResume(answer, withContact);

        assertThat(doc.path("contact").toString()).isEqualTo("[\"jane@example.com\",\"github.com/janedoe\"]");
        assertThat(doc.path("droppedContact").toString()).contains("evil.example/login");
    }

    @Test
    void theResumeRequestKeepsTheLayoutAndAddsNoSummary() {
        assertThat(TailorService.RESUME).contains("no summary", "same headings, in the same order", "**double asterisks**");
        assertThat(TailorService.RESUME_SCHEMA.toString()).doesNotContain("summary").contains("skillGroups", "titleRight");
    }

    @Test
    void flagsInventedNumbersInACoverLetter() throws Exception {
        JsonNode answer = json.readTree("""
                {"greeting": "Dear Acme hiring team,", "closing": "Best,", "signature": "Jane",
                 "paragraphs": ["I cut latency by 40%.", "I grew revenue by $2M."]}""");

        JsonNode doc = service.checkLetter(answer, check);

        assertThat(doc.path("paragraphs").get(0).path("problems")).isEmpty();
        assertThat(doc.path("paragraphs").get(1).path("problems").toString()).contains("$2m");
        assertThat(doc.path("flagged").asInt()).isEqualTo(1);
    }
}
