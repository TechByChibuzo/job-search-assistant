package com.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jobsearch.TestcontainersConfiguration;
import com.jobsearch.job.ManualJobService;

/** Alert emails end to end, with Google faked: read, parsed, filtered, saved once. */
@SpringBootTest(properties = {"app.gmail.client-id=test-client", "app.gmail.client-secret=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AlertImportIT {

    private static final String LINKEDIN_HTML = """
            <table><tr><td>
              <a href="https://www.linkedin.com/comm/jobs/view/111/?trackingId=a">Software Engineer II</a>
              <p>Microsoft · Redmond, WA</p>
            </td></tr><tr><td>
              <a href="https://www.linkedin.com/comm/jobs/view/222/?trackingId=b">Backend Engineer</a>
              <p>Shopify · Toronto, Ontario, Canada</p>
            </td></tr></table>""";

    @Autowired
    MockMvc mvc;

    @Autowired
    AlertImporter importer;

    @Autowired
    GmailAccountRepository accounts;

    @Autowired
    JdbcTemplate db;

    @MockitoBean
    GmailApi gmail;

    @MockitoBean
    GmailAuth auth;

    @MockitoSpyBean
    ManualJobService manualJobs;

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE company, job, contact, pursuit, outreach, outreach_event, gmail_account, imported_email RESTART IDENTITY CASCADE");
        accounts.save(new GmailAccount("me@gmail.com", "rt", Instant.now()));
        when(auth.configured()).thenReturn(true);
        when(auth.account()).thenAnswer(i -> accounts.findById(GmailAccount.ID));
        when(auth.accessToken()).thenReturn("token");
    }

    @Test
    void addsUsRolesFromNewAlertEmailsOnce() throws Exception {
        AlertEmail email = new AlertEmail("m1", "LinkedIn <jobalerts-noreply@linkedin.com>", "New jobs", Instant.now(), LINKEDIN_HTML, null);
        AlertEmail junk = new AlertEmail("m2", "Someone <a@b.com>", "Hello", Instant.now(), "<p>No jobs here</p>", null);
        when(gmail.alertEmails(anyString(), anyString(), anyInt(), any())).thenReturn(List.of(email, junk));

        var result = importer.run();

        assertThat(result.newJobs()).isEqualTo(1);
        assertThat(result.skippedJobs()).isEqualTo(1);
        mvc.perform(get("/api/jobs"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Software Engineer II"))
                .andExpect(jsonPath("$.content[0].companyName").value("Microsoft"))
                .andExpect(jsonPath("$.content[0].url").value("https://www.linkedin.com/jobs/view/111/"));
        mvc.perform(get("/api/gmail"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.lastEmailsRead").value(2))
                .andExpect(jsonPath("$.lastRolesAdded").value(1))
                .andExpect(jsonPath("$.lastUnreadable").value(1));

        // Both emails are now remembered; the importer tells Gmail to skip them next time
        assertThat(db.queryForObject("select count(*) from imported_email", Integer.class)).isEqualTo(2);
        // And the same role from a later email isn't saved twice
        assertThat(importer.run().newJobs()).isZero();
    }

    @Test
    void aRoleThatCantBeSavedDoesNotStopTheEmailOrTheRun() throws Exception {
        AlertEmail bad = new AlertEmail("m3", "LinkedIn <jobalerts-noreply@linkedin.com>", "New jobs", Instant.now(), """
                <table><tr><td>
                  <a href="https://www.linkedin.com/comm/jobs/view/333/">Engineer</a>
                  <p>Microsoft · Redmond, WA</p>
                </td></tr><tr><td>
                  <a href="https://www.linkedin.com/comm/jobs/view/444/">Data Engineer</a>
                  <p>Microsoft · Redmond, WA</p>
                </td></tr></table>""", null);
        when(gmail.alertEmails(anyString(), anyString(), anyInt(), any())).thenReturn(List.of(bad));
        doThrow(new IllegalStateException("database hiccup"))
                .when(manualJobs).addFromAlert(eq("https://www.linkedin.com/jobs/view/333/"), any(), any(), any(), any());

        var result = importer.run();

        assertThat(result.status().name()).isEqualTo("OK");
        assertThat(result.newJobs()).isEqualTo(1);
        assertThat(result.skippedJobs()).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from imported_email where message_id = 'm3'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aMissingLabelIsReportedAsAFailedImport() throws Exception {
        when(gmail.alertEmails(anyString(), anyString(), anyInt(), any()))
                .thenThrow(new com.jobsearch.job.source.FeedFormatException("There's no Gmail label called \"Job alerts\"."));

        assertThat(importer.run().status().name()).isEqualTo("FAILED");
        mvc.perform(get("/api/gmail")).andExpect(jsonPath("$.lastImportError").value(Matchers.containsString("no Gmail label")));
    }

    @Test
    void googleSendingTheOwnerBackWithAnErrorReturnsThemToTheApp() throws Exception {
        mvc.perform(get("/api/gmail/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/companies?gmail=declined"));
    }

    @Test
    void importNowRunsInTheBackground() throws Exception {
        when(gmail.alertEmails(anyString(), anyString(), anyInt(), any())).thenReturn(List.of());

        mvc.perform(post("/api/gmail/import")).andExpect(status().isAccepted());
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(get("/api/sync")).andReturn().getResponse().getContentAsString();
            if (body.contains("\"running\":false")) {
                assertThat(body).contains("Job alert emails");
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Import didn't finish");
    }
}
