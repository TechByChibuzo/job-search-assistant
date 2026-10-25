package com.jobsearch.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.PlainTextSource;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jobsearch.config.AppProperties;

/**
 * Claude through the official Java SDK. Each request carries the system prompt and the resume first, with a
 * cache breakpoint on the resume, so repeated requests about different roles reuse that prefix; then the
 * role-specific request; and asks for JSON matching a schema (structured outputs).
 */
@Component
class AnthropicClaude implements Claude {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClaude.class);
    private static final long MAX_TOKENS = 8000L;

    private final AppProperties.Ai config;
    private final ObjectMapper json;
    private final AiBudget budget;
    /** Null for Anthropic's API; tests point it at a local stub. */
    private final String baseUrl;
    private volatile AnthropicClient client;

    @Autowired
    AnthropicClaude(AppProperties properties, ObjectMapper json, AiBudget budget) {
        this(properties, json, budget, null);
    }

    AnthropicClaude(AppProperties properties, ObjectMapper json, AiBudget budget, String baseUrl) {
        this.config = properties.ai();
        this.json = json;
        this.budget = budget;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean configured() {
        return config.configured();
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public JsonNode ask(String system, Resume resume, String request, Map<String, Object> schema, Effort effort) {
        if (!configured()) {
            throw new AiException("Claude isn't set up: add ANTHROPIC_API_KEY to .env and restart the app.");
        }
        budget.spend();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(system).build()))
                .addUserMessageOfContentBlockParams(List.of(resumeBlock(resume), ContentBlockParam.ofText(
                        TextBlockParam.builder().text(request).build())))
                .outputConfig(OutputConfig.builder()
                        .effort(effort(effort))
                        .format(JsonOutputFormat.builder().schema(schema(schema)).build())
                        .build())
                // If a safety classifier declines, retry server-side on the recommended fallback model
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        Message response;
        try {
            response = client().messages().create(params);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new AiException("Anthropic rejected the API key. Check ANTHROPIC_API_KEY in .env.", e);
        } catch (RateLimitException e) {
            throw new AiException("Anthropic is rate-limiting requests right now. Try again in a minute.", e);
        } catch (AnthropicServiceException e) {
            log.warn("Claude request failed: HTTP {}", e.statusCode());
            throw new AiException("Claude couldn't answer (HTTP " + e.statusCode() + "). Try again shortly.", e);
        } catch (AnthropicIoException e) {
            throw new AiException("Couldn't reach Anthropic. Check the internet connection.", e);
        }
        return parse(response);
    }

    private JsonNode parse(Message response) {
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            String category = response.stopDetails().flatMap(d -> d.category().map(Object::toString)).orElse("unspecified");
            throw new AiException("Claude declined this request (" + category + ").");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiException("Claude's answer was cut off. Try again.");
        }
        StringBuilder text = new StringBuilder();
        response.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
        try {
            return json.readTree(text.toString());
        } catch (JsonProcessingException e) {
            throw new AiException("Claude's answer wasn't in the expected format. Try again.", e);
        }
    }

    /** The resume, cached for an hour: the daily scoring run sends it once per role. */
    private static ContentBlockParam resumeBlock(Resume resume) {
        CacheControlEphemeral cache = CacheControlEphemeral.builder().ttl(CacheControlEphemeral.Ttl.TTL_1H).build();
        DocumentBlockParam.Builder doc = DocumentBlockParam.builder().title("Candidate resume").cacheControl(cache);
        if (resume.isPdf()) {
            doc.source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(resume.pdf())).build());
        } else {
            doc.source(PlainTextSource.builder().data(resume.text()).build());
        }
        return ContentBlockParam.ofDocument(doc.build());
    }

    private static OutputConfig.Effort effort(Effort effort) {
        return switch (effort) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    private static JsonOutputFormat.Schema schema(Map<String, Object> schema) {
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        schema.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    var builder = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).maxRetries(1);
                    if (baseUrl != null) {
                        builder.baseUrl(baseUrl);
                    }
                    client = builder.build();
                }
                c = client;
            }
        }
        return c;
    }

    /** A JSON schema for an object with these properties, all required and nothing else allowed. */
    static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", new ArrayList<>(properties.keySet()),
                "additionalProperties", false);
    }
}
