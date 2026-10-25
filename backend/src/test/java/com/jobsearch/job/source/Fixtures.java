package com.jobsearch.job.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

/** Trimmed copies of real responses from each ATS, recorded on 2026-10-25. */
final class Fixtures {

    private Fixtures() {}

    static String read(String name) {
        try {
            return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
