package com.jobsearch.config;

import java.time.Clock;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class HttpConfig {

    /** Every outgoing request names the app. Timeouts come from spring.http.client.* in application.yml. */
    @Bean
    RestClientCustomizer userAgent(AppProperties properties) {
        return builder -> builder.defaultHeader(HttpHeaders.USER_AGENT, properties.http().userAgent());
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
