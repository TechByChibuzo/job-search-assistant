package com.jobsearch.ai;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** One structured request to Claude about the owner's resume. An interface so tests never call the API. */
public interface Claude {

    enum Effort {
        LOW,
        MEDIUM,
        HIGH
    }

    boolean configured();

    String model();

    /**
     * Sends the system prompt, the resume, then {@code request}, and returns Claude's answer as JSON matching
     * {@code schema}.
     *
     * @throws AiException when Claude declines, the API fails, or the answer isn't valid JSON
     */
    JsonNode ask(String system, Resume resume, String request, Map<String, Object> schema, Effort effort);
}
