package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jobsearch.TestcontainersConfiguration;

/** Match scores and drafts end to end, with Claude faked and a resume in a temporary folder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AiIT {

    static final Path RESUME_DIR;

    static {
        try {
            RESUME_DIR = Files.createTempDirectory("resume");
            Files.writeString(RESUME_DIR.resolve("resume.md"), "Jane Doe. Purdue BS CompE. Java, Spring Boot, React.");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void resumeFolder(DynamicPropertyRegistry registry) {
        registry.add("app.ai.resume-dir", RESUME_DIR::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Autowired
    MatchStep step;

    @MockitoBean
    Claude claude;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        db.execute("TRUNCATE company, job, contact, pursuit, outreach, outreach_event, gmail_account, imported_email RESTART IDENTITY CASCADE");
        when(claude.configured()).thenReturn(true);
        when(claude.model()).thenReturn("claude-opus-5-5");
    }

    @Test
    void scoresARoleStoresItAndSortsTheInboxByFit() throws Exception {
        long strong = pasteRole("https://example.com/jobs/1", "Backend Engineer");
        long weak = pasteRole("https://example.com/jobs/2", "Principal Hardware Architect");
        when(claude.ask(eq(Prompts.MATCH), any(), Mockito.contains("Backend Engineer"), any(), any())).thenReturn(json.readTree(
                "{\"score\": 88, \"summary\": \"Strong Java and Spring match.\", \"strengths\": [\"Java\", \"Spring Boot\"], \"gaps\": []}"));
        when(claude.ask(eq(Prompts.MATCH), any(), Mockito.contains("Hardware"), any(), any())).thenReturn(json.readTree(
                "{\"score\": 140, \"summary\": \"Different discipline.\", \"strengths\": [], \"gaps\": [\"ASIC design\"]}"));

        mvc.perform(post("/api/jobs/" + strong + "/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(88))
                .andExpect(jsonPath("$.strengths[1]").value("Spring Boot"));
        // Scores are kept within 0 to 100 whatever the model says
        mvc.perform(post("/api/jobs/" + weak + "/match")).andExpect(jsonPath("$.score").value(100));

        mvc.perform(get("/api/jobs/" + strong))
                .andExpect(jsonPath("$.summary.matchScore").value(88))
                .andExpect(jsonPath("$.matchStrengths[0]").value("Java"));
        mvc.perform(get("/api/jobs").param("sort", "fit"))
                .andExpect(jsonPath("$.content[0].title").value("Principal Hardware Architect"))
                .andExpect(jsonPath("$.content[1].matchSummary").value("Strong Java and Spring match."));
    }

    @Test
    void theDailyStepScoresNewUnscoredRolesOnly() throws Exception {
        long scored = pasteRole("https://example.com/jobs/3", "Backend Engineer");
        pasteRole("https://example.com/jobs/4", "Data Engineer");
        when(claude.ask(anyString(), any(), anyString(), any(), any()))
                .thenReturn(json.readTree("{\"score\": 70, \"summary\": \"OK\", \"strengths\": [], \"gaps\": []}"));
        mvc.perform(post("/api/jobs/" + scored + "/match"));
        Mockito.clearInvocations(claude);

        var result = step.run();

        assertThat(step.enabled()).isTrue();
        assertThat(result.newJobs()).isEqualTo(1);
        Mockito.verify(claude, Mockito.times(1)).ask(anyString(), any(), Mockito.contains("Data Engineer"), any(), any());
    }

    @Test
    void draftsAConnectionNoteFromTheResumeRoleAndPerson() throws Exception {
        long job = pasteRole("https://example.com/jobs/5", "Software Engineer II");
        when(claude.ask(eq(Prompts.DRAFT), any(), anyString(), any(), any())).thenReturn(json.readTree(
                "{\"message\": \"Hi Ada, fellow Boilermaker here.\", \"subject\": \"ignored on LinkedIn\"}"));

        mvc.perform(post("/api/drafts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "CONNECTION_NOTE", "channel": "LINKEDIN", "jobId": %d,
                         "personName": "Ada", "personKind": "ALUMNI"}""".formatted(job)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hi Ada, fellow Boilermaker here."))
                .andExpect(jsonPath("$.subject").value(""))
                .andExpect(jsonPath("$.limit").value(300))
                .andExpect(jsonPath("$.characters").value(32));

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        Mockito.verify(claude).ask(eq(Prompts.DRAFT), any(), request.capture(), any(), any());
        assertThat(request.getValue()).contains("Software Engineer II", "Name: Ada", "alumni");
    }

    @Test
    void explainsWhenClaudeIsNotSetUp() throws Exception {
        long job = pasteRole("https://example.com/jobs/6", "Engineer");
        when(claude.ask(anyString(), any(), anyString(), any(), any()))
                .thenThrow(new AiException("Claude isn't set up: add ANTHROPIC_API_KEY to .env and restart the app."));

        mvc.perform(post("/api/jobs/" + job + "/match"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
        mvc.perform(get("/api/ai"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.resume.fileName").value("resume.md"));
    }

    private long pasteRole(String url, String title) throws Exception {
        String body = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"%s\", \"title\": \"%s\", \"company\": \"Acme\", \"location\": \"Austin, TX\"}"
                                .formatted(url, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(body, "$.job.id");
    }
}
