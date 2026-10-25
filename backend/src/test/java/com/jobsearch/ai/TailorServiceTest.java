package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class TailorServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final TailorService service = new TailorService(null, null, null, json);
    private final SourceCheck check = new SourceCheck("""
            Oracle, Software Engineer. Built REST APIs in Java and Spring Boot. Cut query latency by 40% with indexes.
            Skills: Java, Spring Boot, PostgreSQL""");

    @Test
    void flagsUnsupportedBulletsAndDropsSkillsTheResumeDoesntName() throws Exception {
        JsonNode answer = json.readTree("""
                {"name": "Jane Doe", "contactLine": "jane@example.com", "summary": "Backend engineer.",
                 "skills": ["Java", "Kubernetes", "PostgreSQL"],
                 "sections": [{"heading": "Experience", "entries": [{"title": "Software Engineer", "subtitle": "Oracle",
                   "dates": "2021-2024", "bullets": [
                     {"text": "Built Java and Spring Boot REST APIs", "source": "Built REST APIs in Java and Spring Boot"},
                     {"text": "Cut query latency by 75%", "source": "Cut query latency by 40% with indexes"},
                     {"text": "Mentored 4 interns", "source": "Mentored 4 interns over two summers"}]}]}]}""");

        JsonNode doc = service.checkResume(answer, check);

        JsonNode bullets = doc.path("sections").get(0).path("entries").get(0).path("bullets");
        assertThat(bullets.get(0).path("problems")).isEmpty();
        assertThat(bullets.get(1).path("problems").toString()).contains("75%");
        assertThat(bullets.get(2).path("problems").toString()).contains("source wasn't found");
        assertThat(doc.path("flagged").asInt()).isEqualTo(2);
        assertThat(doc.path("skills").toString()).contains("Java", "PostgreSQL").doesNotContain("Kubernetes");
        assertThat(doc.path("droppedSkills").toString()).contains("Kubernetes");
        assertThat(doc.path("checked").asBoolean()).isTrue();
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
