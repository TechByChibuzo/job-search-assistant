package com.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jobsearch.job.source.FeedFormatException;

class GmailApiTest {

    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String LABELS = """
            {"labels": [{"id": "INBOX", "name": "INBOX"}, {"id": "Label_7", "name": "Job alerts"}]}""";

    private MockRestServiceServer server;
    private GmailApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new GmailApi(builder);
    }

    @Test
    void readsNewMessagesUnderTheLabelAndDecodesTheirBodies() {
        server.expect(requestTo(BASE + "/labels")).andExpect(header("Authorization", "Bearer t"))
                .andRespond(withSuccess(LABELS, MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(BASE + "/messages?")))
                .andExpect(queryParam("labelIds", "Label_7"))
                .andExpect(queryParam("q", "newer_than:30d"))
                .andRespond(withSuccess("{\"messages\": [{\"id\": \"a\"}, {\"id\": \"seen\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/messages/a?format=full"))
                .andRespond(withSuccess(message(), MediaType.APPLICATION_JSON));

        List<AlertEmail> emails = api.alertEmails("t", "job alerts", 30, Set.of("seen")::contains);

        server.verify();
        assertThat(emails).hasSize(1);
        AlertEmail email = emails.getFirst();
        assertThat(email.from()).isEqualTo("LinkedIn <jobalerts-noreply@linkedin.com>");
        assertThat(email.subject()).isEqualTo("New jobs");
        assertThat(email.receivedAt()).isEqualTo(Instant.ofEpochMilli(1792944000000L));
        assertThat(email.html()).isEqualTo("<p>Héllo</p>");
        assertThat(email.text()).isEqualTo("Hello");
    }

    @Test
    void explainsAMissingLabel() {
        server.expect(requestTo(BASE + "/labels")).andRespond(withSuccess("{\"labels\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.alertEmails("t", "Job alerts", 30, id -> false))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("no Gmail label called \"Job alerts\"");
    }

    private static String message() {
        return """
                {"id": "a", "internalDate": "1792944000000",
                 "payload": {"mimeType": "multipart/alternative",
                   "headers": [{"name": "From", "value": "LinkedIn <jobalerts-noreply@linkedin.com>"},
                               {"name": "Subject", "value": "New jobs"}],
                   "parts": [
                     {"mimeType": "text/plain", "body": {"data": "%s"}},
                     {"mimeType": "text/html", "body": {"data": "%s"}}]}}"""
                .formatted(b64("Hello"), b64("<p>Héllo</p>"));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
