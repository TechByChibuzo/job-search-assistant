package com.jobsearch.answers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.hamcrest.Matchers;
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
import com.jobsearch.ai.Claude;

/** The answer bank, tailored documents and insights through the API, with Claude faked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PhaseSixIT {

    static final Path RESUME_DIR;

    static {
        try {
            RESUME_DIR = Files.createTempDirectory("resume");
            Files.writeString(RESUME_DIR.resolve("resume.md"), """
                    Jane Doe, jane@example.com
                    Oracle, Software Engineer, 2021-2024
                    - Built REST APIs in Java and Spring Boot serving 3,000 requests per second
                    - Cut query latency by 40% by adding PostgreSQL indexes
                    Skills: Java, Spring Boot, PostgreSQL, React""");
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

    @MockitoBean
    Claude claude;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE company, job, contact, pursuit, outreach, outreach_event, answer, tailored_document RESTART IDENTITY CASCADE");
        when(claude.configured()).thenReturn(true);
        when(claude.model()).thenReturn("claude-opus-5-5");
    }

    @Test
    void answerBankSavesMatchesAndDrafts() throws Exception {
        long id = JsonPath.<Integer>read(mvc.perform(post("/api/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Why do you want to work here?\", \"answer\": \"Because of the team.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/answers/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\": [\"Why do you want to work at Nooks?\", \"What is your favorite color?\"]}"))
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].answer").value("Because of the team."))
                .andExpect(jsonPath("$[0].question").value("Why do you want to work at Nooks?"));

        mvc.perform(post("/api/answers/" + id + "/used")).andExpect(status().isNoContent());
        mvc.perform(patch("/api/answers/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"answer\": \"Because of the product.\"}"))
                .andExpect(jsonPath("$.answer").value("Because of the product."))
                .andExpect(jsonPath("$.uses").value(1));

        when(claude.ask(anyString(), any(), anyString(), any(), any())).thenReturn(json.readTree("{\"answer\": \"I built REST APIs at Oracle.\"}"));
        mvc.perform(post("/api/answers/draft").contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"Tell us about a project\"}"))
                .andExpect(jsonPath("$.answer").value("I built REST APIs at Oracle."));
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        Mockito.verify(claude).ask(anyString(), any(), request.capture(), any(), any());
        assertThat(request.getValue()).contains("<earlier_answers>", "Because of the product.", "Tell us about a project");

        mvc.perform(delete("/api/answers/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/answers")).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }

    @Test
    void tailorsAResumeKeepsItAndFlagsWhatTheResumeDoesntSay() throws Exception {
        long job = pasteRole();
        when(claude.ask(anyString(), any(), anyString(), eq(com.jobsearch.ai.TailorServiceTestAccess.resumeSchema()), any()))
                .thenReturn(json.readTree("""
                        {"name": "Jane Doe", "contactLine": "jane@example.com", "summary": "Backend engineer.",
                         "skills": ["Java", "Go"],
                         "sections": [{"heading": "Experience", "entries": [{"title": "Software Engineer", "subtitle": "Oracle",
                           "dates": "2021-2024", "bullets": [
                             {"text": "Built Java REST APIs at 3,000 requests per second",
                              "source": "Built REST APIs in Java and Spring Boot serving 3,000 requests per second"},
                             {"text": "Cut latency by 90%", "source": "Cut query latency by 40% by adding PostgreSQL indexes"}]}]}]}"""));

        long doc = JsonPath.<Integer>read(mvc.perform(post("/api/jobs/" + job + "/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"RESUME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.flagged").value(1))
                .andExpect(jsonPath("$.content.skills", Matchers.contains("Java")))
                .andExpect(jsonPath("$.content.droppedSkills", Matchers.contains("Go")))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/jobs/" + job + "/documents")).andExpect(jsonPath("$[0].kind").value("RESUME"));
        mvc.perform(patch("/api/documents/" + doc).contentType(MediaType.APPLICATION_JSON).content("{\"content\": {\"summary\": \"Edited.\"}}"))
                .andExpect(jsonPath("$.content.summary").value("Edited."));
        mvc.perform(delete("/api/documents/" + doc)).andExpect(status().isNoContent());
    }

    @Test
    void insightsCountDraftedMessagesAndReplies() throws Exception {
        long contact = JsonPath.<Integer>read(mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\": \"Acme\", \"name\": \"Ada\", \"kind\": \"ALUMNI\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String old = LocalDate.now().minusDays(10).toString();
        long o1 = JsonPath.<Integer>read(mvc.perform(post("/api/outreach").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactId\": %d, \"message\": \"Hi Ada\", \"sentOn\": \"%s\", \"drafted\": true}".formatted(contact, old)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/outreach").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactId\": %d, \"sentOn\": \"%s\"}".formatted(contact, old)));
        mvc.perform(post("/api/outreach/" + o1 + "/events").contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"REPLIED\"}"));

        mvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.sent").value(2))
                .andExpect(jsonPath("$.overall.replied").value(1))
                .andExpect(jsonPath("$.overall.replyRate").value(0.5))
                .andExpect(jsonPath("$.byWho[0].label").value("Alumni"))
                .andExpect(jsonPath("$.byDrafted[?(@.label == 'Drafted with Claude')].replied").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.medianDaysToReply").exists());
    }

    private long pasteRole() throws Exception {
        return JsonPath.<Integer>read(mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/jobs/1\", \"title\": \"Backend Engineer\", \"company\": \"Acme\"}"))
                .andReturn().getResponse().getContentAsString(), "$.job.id");
    }
}
