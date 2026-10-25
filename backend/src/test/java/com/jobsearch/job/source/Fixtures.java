package com.jobsearch.job.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/** Trimmed copies of real responses from each ATS, recorded on 2026-10-25. */
final class Fixtures {

    /** The fixtures were recorded on this day. */
    static final Instant NOW = Instant.parse("2026-10-25T18:00:00Z");

    /** Everything: no age limit, no search, any country. */
    static final FetchQuery ALL = new FetchQuery(NOW, Instant.EPOCH, List.of(), false);

    private Fixtures() {}

    static String read(String name) {
        try {
            return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
