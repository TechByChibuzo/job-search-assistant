package com.jobsearch.tracker;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jobsearch.TestcontainersConfiguration;

/** The tracker end to end: paste a role, add people, record messages, follow up, get referred. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TrackerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @BeforeEach
    void clean() {
        db.execute("TRUNCATE company, job, contact, pursuit, outreach, outreach_event RESTART IDENTITY CASCADE");
    }

    @Test
    void aPastedRoleJoinsTheInboxUnderACompanyAddedByHand() throws Exception {
        long jobId = pasteRole("https://www.linkedin.com/jobs/view/4012345678", "Software Engineer II", "Microsoft");

        mvc.perform(get("/api/jobs/" + jobId))
                .andExpect(jsonPath("$.summary.companyName").value("Microsoft"))
                .andExpect(jsonPath("$.summary.manual").value(true))
                .andExpect(jsonPath("$.summary.isNew").value(true));
        mvc.perform(get("/api/companies"))
                .andExpect(jsonPath("$[0].ats").value("MANUAL"))
                .andExpect(jsonPath("$[0].openJobs").value(1));
        mvc.perform(get("/api/jobs")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pastingTheSameLinkTwiceReturnsTheSavedRole() throws Exception {
        long first = pasteRole("https://careers.microsoft.com/job/1", "Engineer", "Microsoft");

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(json("url", "https://careers.microsoft.com/job/1", "title", "Engineer", "company", "microsoft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySaved").value(true))
                .andExpect(jsonPath("$.job.id").value(first));
    }

    @Test
    void aPastedLinkOnASyncableBoardOffersToTrackTheCompany() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(json("url", "https://jobs.lever.co/palantir/abc", "title", "Engineer", "company", "Palantir")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackableBoard.ats").value("LEVER"))
                .andExpect(jsonPath("$.trackableBoard.board").value("palantir"));
    }

    @Test
    void rejectsSomethingThatIsNotALink() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(json("url", "javascript:alert(1)", "title", "x", "company", "y")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aHandAddedRoleIsClosedByTheOwnerAndItsCompanyIsNeverSynced() throws Exception {
        long jobId = pasteRole("https://careers.microsoft.com/job/2", "Engineer", "Microsoft");
        long companyId = JsonPath.<Integer>read(mvc.perform(get("/api/jobs/" + jobId)).andReturn().getResponse().getContentAsString(),
                "$.summary.companyId");

        mvc.perform(patch("/api/jobs/" + jobId).contentType(MediaType.APPLICATION_JSON).content("{\"closed\": true}"))
                .andExpect(jsonPath("$.open").value(false));
        mvc.perform(post("/api/companies/" + companyId + "/sync")).andExpect(status().isBadRequest());
    }

    @Test
    void outreachTracksTheRoleSchedulesAFollowUpAndAReferralMovesItOn() throws Exception {
        long jobId = pasteRole("https://careers.microsoft.com/job/3", "Software Engineer", "Microsoft");
        long contactId = addContact("Microsoft", "Ada Lovelace", "ENGINEER");

        // A message sent six days ago is due for a follow-up today
        String sent = mvc.perform(post("/api/outreach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\": " + contactId + ", \"jobId\": " + jobId
                                + ", \"channel\": \"LINKEDIN\", \"message\": \"Hi Ada\", \"sentOn\": \"" + LocalDate.now().minusDays(6) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_REPLY"))
                .andExpect(jsonPath("$.followUpDue").value(true))
                .andReturn().getResponse().getContentAsString();
        long outreachId = JsonPath.<Integer>read(sent, "$.id");

        mvc.perform(get("/api/pursuits"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stage").value("REACHING_OUT"))
                .andExpect(jsonPath("$[0].people").value(1))
                .andExpect(jsonPath("$[0].followUpDue").value(true));
        mvc.perform(get("/api/outreach/due")).andExpect(jsonPath("$[0].person.name").value("Ada Lovelace"));

        event(outreachId, "FOLLOW_UP", null)
                .andExpect(jsonPath("$.followUps").value(1))
                .andExpect(jsonPath("$.followUpDue").value(false));
        mvc.perform(get("/api/outreach/due")).andExpect(jsonPath("$", hasSize(0)));

        event(outreachId, "REFERRED", "Submitted my referral").andExpect(jsonPath("$.status").value("REFERRED"));
        mvc.perform(get("/api/jobs/" + jobId + "/tracking"))
                .andExpect(jsonPath("$.pursuit.stage").value("REFERRED"))
                .andExpect(jsonPath("$.outreach[0].events", hasSize(3)))
                .andExpect(jsonPath("$.outreach[0].events[2].note").value("Submitted my referral"));
        mvc.perform(get("/api/contacts"))
                .andExpect(jsonPath("$[0].lastStatus").value("REFERRED"))
                .andExpect(jsonPath("$[0].conversations").value(1));
    }

    @Test
    void theOwnerMovesARoleAlongAndApplyingRecordsTheDate() throws Exception {
        long jobId = pasteRole("https://careers.microsoft.com/job/4", "Engineer", "Microsoft");
        String created = mvc.perform(post("/api/pursuits").contentType(MediaType.APPLICATION_JSON).content("{\"jobId\": " + jobId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("SAVED"))
                .andReturn().getResponse().getContentAsString();
        long pursuitId = JsonPath.<Integer>read(created, "$.id");

        mvc.perform(post("/api/pursuits").contentType(MediaType.APPLICATION_JSON).content("{\"jobId\": " + jobId + "}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/pursuits/" + pursuitId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"APPLIED\", \"resumeVersion\": \"backend-v2\"}"))
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andExpect(jsonPath("$.appliedOn").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.resumeVersion").value("backend-v2"));

        mvc.perform(delete("/api/pursuits/" + pursuitId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/jobs/" + jobId + "/tracking")).andExpect(jsonPath("$.pursuit").doesNotExist());
    }

    @Test
    void refusesMessagesToSomeoneAtAnotherCompanyOrInTheFuture() throws Exception {
        long jobId = pasteRole("https://careers.microsoft.com/job/5", "Engineer", "Microsoft");
        long googler = addContact("Google", "Grace Hopper", "RECRUITER");
        long msft = addContact("Microsoft", "Alan Turing", "RECRUITER");

        mvc.perform(post("/api/outreach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\": " + googler + ", \"jobId\": " + jobId + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/outreach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\": " + msft + ", \"sentOn\": \"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contactsCanBeEditedAndRemoved() throws Exception {
        long id = addContact("Microsoft", "Alan Turing", "OTHER");

        mvc.perform(patch("/api/contacts/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"HIRING_MANAGER\", \"title\": \"Engineering Manager\", \"linkedinUrl\": \"https://www.linkedin.com/in/alan\"}"))
                .andExpect(jsonPath("$.kind").value("HIRING_MANAGER"))
                .andExpect(jsonPath("$.title").value("Engineering Manager"))
                .andExpect(jsonPath("$.name").value("Alan Turing"));
        mvc.perform(delete("/api/contacts/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/contacts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void refusesProfileLinksThatAreNotWebLinks() throws Exception {
        for (String bad : new String[] {"javascript:alert(document.cookie)", "data:text/html,hi", "www.linkedin.com/in/x"}) {
            mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                            .content(json("companyName", "Microsoft", "name", "Eve", "linkedinUrl", bad)))
                    .andExpect(status().isBadRequest());
        }
        long id = addContact("Microsoft", "Alan Turing", "OTHER");
        mvc.perform(patch("/api/contacts/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("linkedinUrl", "JavaScript:alert(1)")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content(json("companyName", "Microsoft", "name", "Eve", "email", "not-an-email")))
                .andExpect(status().isBadRequest());
    }

    private long pasteRole(String url, String title, String company) throws Exception {
        String body = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(json("url", url, "title", title, "company", company, "location", "Redmond, WA")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(body, "$.job.id");
    }

    private long addContact(String company, String name, String kind) throws Exception {
        String body = mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content(json("companyName", company, "name", name, "kind", kind)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(body, "$.id");
    }

    private ResultActions event(long outreachId, String type, String note) throws Exception {
        String body = note == null ? json("type", type) : json("type", type, "note", note);
        return mvc.perform(post("/api/outreach/" + outreachId + "/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static String json(String... pairs) {
        StringBuilder s = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                s.append(',');
            }
            s.append('"').append(pairs[i]).append("\": \"").append(pairs[i + 1].replace("\"", "\\\"")).append('"');
        }
        return s.append('}').toString();
    }
}
