package com.jobsearch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jobsearch.config.CsrfHeaderFilter;

/** A page on another site can make the browser send a plain POST; without the app's header it is refused. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CsrfHeaderIT {

    @Autowired
    WebApplicationContext context;

    @Autowired
    CsrfHeaderFilter filter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Built by hand, so requests don't get the app header the other tests add by default
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    void refusesAChangeWithoutTheAppHeader() throws Exception {
        mvc.perform(post("/api/sync"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("X-Requested-With")));
        mvc.perform(post("/api/jobs/1/match").header(CsrfHeaderFilter.HEADER, "something-else"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsTheAppAndPlainReads() throws Exception {
        mvc.perform(post("/api/sync").header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE)).andExpect(status().isAccepted());
        mvc.perform(get("/api/companies")).andExpect(status().isOk());
    }

    @Test
    void onlyTheAppsOwnOriginMayAddTheHeader() throws Exception {
        mvc.perform(options("/api/sync")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", CsrfHeaderFilter.HEADER))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/sync")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", CsrfHeaderFilter.HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
