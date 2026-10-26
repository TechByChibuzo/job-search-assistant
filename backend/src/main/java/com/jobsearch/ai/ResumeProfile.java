package com.jobsearch.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

/** Reads the owner's contact details and education off the resume with Claude, to prefill the profile. */
@Service
public class ResumeProfile {

    public record Extracted(
            String firstName,
            String lastName,
            String email,
            String phone,
            String city,
            String state,
            String country,
            String linkedinUrl,
            String githubUrl,
            String websiteUrl,
            String currentCompany,
            String currentTitle,
            String school,
            String degree,
            String graduationYear,
            String previousSchool,
            String previousDegree) {}

    static final String PROMPT = """
            Read the candidate's resume and copy out their contact details and education, for filling job \
            application forms. Copy exactly what the resume says; leave a field as an empty string when the resume \
            doesn't show it. Never guess or invent.

            - currentCompany and currentTitle: the most recent role (the one marked present, or the latest).
            - school, degree, graduationYear: the highest or most recent degree, including one in progress.
            - previousSchool, previousDegree: the degree before that one, if the resume lists one.
            - Links: full URLs as written (add https:// if the resume omits it).
            """;

    static final Map<String, Object> SCHEMA;

    static {
        var string = Map.<String, Object>of("type", "string");
        var props = new LinkedHashMap<String, Object>();
        for (String f : new String[] {"firstName", "lastName", "email", "phone", "city", "state", "country", "linkedinUrl",
                "githubUrl", "websiteUrl", "currentCompany", "currentTitle", "school", "degree", "graduationYear", "previousSchool", "previousDegree"}) {
            props.put(f, string);
        }
        SCHEMA = AnthropicClaude.object(props);
    }

    private final Claude claude;
    private final ResumeStore resumes;

    ResumeProfile(Claude claude, ResumeStore resumes) {
        this.claude = claude;
        this.resumes = resumes;
    }

    public Extracted extract() {
        Resume resume = resumes.current().orElseThrow(() -> new AiException(resumes.describeFolder()));
        JsonNode a = claude.ask(PROMPT, resume, "Copy out the candidate's details.", SCHEMA, Claude.Effort.LOW);
        return new Extracted(t(a, "firstName"), t(a, "lastName"), t(a, "email"), t(a, "phone"), t(a, "city"), t(a, "state"),
                t(a, "country"), t(a, "linkedinUrl"), t(a, "githubUrl"), t(a, "websiteUrl"), t(a, "currentCompany"),
                t(a, "currentTitle"), t(a, "school"), t(a, "degree"), t(a, "graduationYear"),
                t(a, "previousSchool"), t(a, "previousDegree"));
    }

    private static String t(JsonNode a, String field) {
        String v = a.path(field).asText("").strip();
        return v.isEmpty() ? null : v;
    }
}
