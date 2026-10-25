package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jobsearch.config.AppProperties;

/** The Claude client against a local stub of the Messages API: what it sends, and how it reads answers. */
class AnthropicClaudeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> sentBody = new AtomicReference<>();
    private final AtomicReference<String> sentBeta = new AtomicReference<>();
    private HttpServer server;
    private int status;
    private String reply;
    private AnthropicClaude claude;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sentBeta.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var ai = new AppProperties.Ai("test-key", "claude-opus-5-5", 30, ".", 150);
        var properties = new AppProperties(null, null, null, null, null, null, ai);
        claude = new AnthropicClaude(properties, json, new AiBudget(properties, java.time.Clock.systemUTC()),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheResumeCachedAStructuredFormatAndFallbacksThenReadsTheJson() throws Exception {
        status = 200;
        reply = message("end_turn", "{\"score\": 82, \"summary\": \"Good fit\", \"strengths\": [], \"gaps\": []}");
        Resume resume = new Resume("resume.md", Instant.now(), null, "Jane Doe. Java, Spring Boot.");

        JsonNode answer = claude.ask("system prompt", resume, "Assess this role", Prompts.MATCH_SCHEMA, Claude.Effort.LOW);

        assertThat(answer.path("score").asInt()).isEqualTo(82);
        JsonNode body = json.readTree(sentBody.get());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(sentBeta.get()).contains("server-side-fallback-2026-07-01");
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("output_config").path("format").path("schema").path("required").toString()).contains("score", "gaps");
        JsonNode document = body.path("messages").get(0).path("content").get(0);
        assertThat(document.path("type").asText()).isEqualTo("document");
        assertThat(document.path("source").path("data").asText()).isEqualTo("Jane Doe. Java, Spring Boot.");
        assertThat(document.path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(body.path("messages").get(0).path("content").get(1).path("text").asText()).isEqualTo("Assess this role");
    }

    @Test
    void sendsAPdfResumeAsABase64Document() throws Exception {
        status = 200;
        reply = message("end_turn", "{}");
        Resume pdf = new Resume("resume.pdf", Instant.now(), "%PDF-1.4".getBytes(StandardCharsets.US_ASCII), null);

        claude.ask("s", pdf, "r", Map.of("type", "object"), Claude.Effort.MEDIUM);

        JsonNode source = json.readTree(sentBody.get()).path("messages").get(0).path("content").get(0).path("source");
        assertThat(source.path("type").asText()).isEqualTo("base64");
        assertThat(source.path("media_type").asText()).isEqualTo("application/pdf");
    }

    @Test
    void explainsARefusal() {
        status = 200;
        reply = """
                {"id": "m", "type": "message", "role": "assistant", "model": "claude-opus-5-5", "content": [],
                 "stop_reason": "refusal", "stop_details": {"type": "refusal", "category": "cyber", "explanation": "x"},
                 "usage": {"input_tokens": 1, "output_tokens": 0}}""";

        assertThatThrownBy(() -> claude.ask("s", textResume(), "r", Map.of("type", "object"), Claude.Effort.LOW))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void explainsABadApiKey() {
        status = 401;
        reply = "{\"type\": \"error\", \"error\": {\"type\": \"authentication_error\", \"message\": \"invalid x-api-key\"}}";

        assertThatThrownBy(() -> claude.ask("s", textResume(), "r", Map.of("type", "object"), Claude.Effort.LOW))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void refusesToRunWithoutAKey() {
        var properties = new AppProperties(null, null, null, null, null, null, new AppProperties.Ai("", "m", 30, ".", 150));
        var unset = new AnthropicClaude(properties, json, new AiBudget(properties, java.time.Clock.systemUTC()), "http://127.0.0.1:1");

        assertThat(unset.configured()).isFalse();
        assertThatThrownBy(() -> unset.ask("s", textResume(), "r", Map.of(), Claude.Effort.LOW))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("isn't set up");
    }

    private static Resume textResume() {
        return new Resume("r.txt", Instant.now(), null, "text");
    }

    private static String message(String stopReason, String text) {
        return """
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": [{"type": "text", "text": %s}],
                 "stop_reason": "%s", "usage": {"input_tokens": 10, "output_tokens": 5}}"""
                .formatted(new ObjectMapper().valueToTree(text).toString(), stopReason);
    }
}
