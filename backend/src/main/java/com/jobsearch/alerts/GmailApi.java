package com.jobsearch.alerts;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.jobsearch.job.source.FeedFormatException;

/**
 * The few Gmail API calls the app makes, all read-only: the account's address, the label list, the messages
 * under one label, and one message at a time.
 */
@Component
class GmailApi {

    private static final int MAX_MESSAGES = 300;

    private final RestClient http;

    GmailApi(RestClient.Builder builder) {
        this.http = builder.baseUrl("https://gmail.googleapis.com/gmail/v1/users/me").build();
    }

    String profileEmail(String token) {
        return get(token, "/profile").path("emailAddress").asText();
    }

    /**
     * Alert emails under {@code labelName} received in the last {@code days} days, skipping messages
     * {@code alreadyRead} says were processed before, so their bodies aren't fetched again.
     */
    List<AlertEmail> alertEmails(String token, String labelName, int days, Predicate<String> alreadyRead) {
        String labelId = labelId(token, labelName);
        List<AlertEmail> emails = new ArrayList<>();
        String pageToken = null;
        int seen = 0;
        do {
            String page = pageToken;
            JsonNode list = http.get()
                    .uri(b -> {
                        UriBuilder uri = b.path("/messages")
                                .queryParam("labelIds", labelId)
                                .queryParam("q", "newer_than:" + days + "d")
                                .queryParam("maxResults", 100);
                        if (page != null) {
                            uri.queryParam("pageToken", page);
                        }
                        return uri.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            for (JsonNode m : list.path("messages")) {
                seen++;
                String id = m.path("id").asText();
                if (!alreadyRead.test(id)) {
                    emails.add(message(token, id));
                }
            }
            pageToken = list.path("nextPageToken").asText(null);
        } while (pageToken != null && seen < MAX_MESSAGES);
        return emails;
    }

    private String labelId(String token, String name) {
        for (JsonNode label : get(token, "/labels").path("labels")) {
            if (label.path("name").asText().equalsIgnoreCase(name)) {
                return label.path("id").asText();
            }
        }
        throw new FeedFormatException("There's no Gmail label called \"" + name + "\". Create it with a filter for your "
                + "job-alert emails (docs/gmail-setup.md).");
    }

    private AlertEmail message(String token, String id) {
        JsonNode m = get(token, "/messages/" + id + "?format=full");
        JsonNode payload = m.path("payload");
        String from = header(payload, "From");
        String subject = header(payload, "Subject");
        Instant received = Instant.ofEpochMilli(m.path("internalDate").asLong(0));
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        collectBodies(payload, html, text);
        return new AlertEmail(id, from, subject, received, html.isEmpty() ? null : html.toString(),
                text.isEmpty() ? null : text.toString());
    }

    /** Walks a MIME tree, decoding the HTML and plain-text parts. */
    static void collectBodies(JsonNode part, StringBuilder html, StringBuilder text) {
        String type = part.path("mimeType").asText("");
        String data = part.path("body").path("data").asText(null);
        if (data != null && type.equals("text/html")) {
            html.append(decode(data));
        } else if (data != null && type.equals("text/plain")) {
            text.append(decode(data));
        }
        for (JsonNode child : part.path("parts")) {
            collectBodies(child, html, text);
        }
    }

    /** Gmail bodies are base64url, sometimes without padding. */
    static String decode(String data) {
        return new String(Base64.getUrlDecoder().decode(data.replace("=", "")), StandardCharsets.UTF_8);
    }

    private static String header(JsonNode payload, String name) {
        for (JsonNode h : payload.path("headers")) {
            if (h.path("name").asText().equalsIgnoreCase(name)) {
                return h.path("value").asText();
            }
        }
        return null;
    }

    private JsonNode get(String token, String path) {
        return http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(JsonNode.class);
    }
}
