package com.jobsearch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jobsearch.config.CsrfHeaderFilter;

/** A real Postgres for tests, the same version as docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16.4-alpine");
    }

    /** Test requests come from "the app", like the web client; CsrfHeaderIT checks requests that don't. */
    @Bean
    MockMvcBuilderCustomizer appHeader() {
        return builder -> builder.defaultRequest(get("/").header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE));
    }
}
