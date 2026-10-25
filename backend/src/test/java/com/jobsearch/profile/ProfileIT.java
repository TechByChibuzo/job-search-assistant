package com.jobsearch.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** The applicant profile, the resume download, and recording applications, as the extension uses them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileIT {

    static final Path RESUME_DIR;

    static {
        try {
            RESUME_DIR = Files.createTempDirectory("resume");
            Files.write(RESUME_DIR.resolve("Jane_Doe_Resume.pdf"), "%PDF-1.4 jane".getBytes());
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

    @BeforeEach
    void clean() {
        db.execute("TRUNCATE company, job, contact, pursuit, outreach, outreach_event, applicant_profile RESTART IDENTITY CASCADE");
    }

    @Test
    void savesTheProfileTidyingLinks() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.fields.email").doesNotExist())
                .andExpect(jsonPath("$.resumeFileName").value("Jane_Doe_Resume.pdf"));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName": " Jane ", "lastName": "Doe", "email": "jane@example.com",
                         "linkedinUrl": "linkedin.com/in/jane", "school": ""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.firstName").value("Jane"))
                .andExpect(jsonPath("$.fields.linkedinUrl").value("https://linkedin.com/in/jane"))
                .andExpect(jsonPath("$.fields.school").doesNotExist());

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{\"githubUrl\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestsAProfileFromTheResumeWithoutSavingIt() throws Exception {
        when(claude.configured()).thenReturn(true);
        when(claude.ask(anyString(), any(), anyString(), any(), any())).thenReturn(new ObjectMapper().readTree("""
                {"firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "phone": "", "city": "Austin",
                 "state": "TX", "country": "", "linkedinUrl": "", "githubUrl": "", "websiteUrl": "",
                 "currentCompany": "Oracle", "currentTitle": "Software Engineer", "school": "Purdue University",
                 "degree": "BS Computer Engineering", "graduationYear": "2021"}"""));

        mvc.perform(post("/api/profile/from-resume"))
                .andExpect(jsonPath("$.currentCompany").value("Oracle"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(get("/api/profile")).andExpect(jsonPath("$.fields.firstName").doesNotExist());
    }

    @Test
    void servesTheResumeForTheExtensionToAttach() throws Exception {
        mvc.perform(get("/api/profile/resume"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Jane_Doe_Resume.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void recordsAnApplicationAgainstTheRoleWhateverTheLinkLooksLike() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://jobs.lever.co/acme/1a2b3c4d-0000\", \"title\": \"Backend Engineer\", \"company\": \"Acme\"}"));

        // Lever's apply page is the posting link plus /apply
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://jobs.lever.co/acme/1a2b3c4d-0000/apply?source=x\", \"resumeVersion\": \"Jane_Doe_Resume.pdf\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andExpect(jsonPath("$.role.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.resumeVersion").value("Jane_Doe_Resume.pdf"))
                .andExpect(jsonPath("$.appliedOn").exists());
    }

    @Test
    void anUnknownApplicationPageIsSavedOnceTheOwnerNamesIt() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://careers.example.com/apply/987654\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://careers.example.com/apply/987654\", \"title\": \"Engineer\", \"company\": \"Example\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role.companyName").value("Example"))
                .andExpect(jsonPath("$.stage").value("APPLIED"));
    }

    @Test
    void theExtensionMayCallTheApi() throws Exception {
        mvc.perform(options("/api/contacts")
                        .header("Origin", "chrome-extension://ohabbfmnmocehoafnooeknchjhanfomk")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Requested-With, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "chrome-extension://ohabbfmnmocehoafnooeknchjhanfomk"));
        mvc.perform(options("/api/contacts")
                        .header("Origin", "chrome-extension://someotherextensionid")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
