package com.jobsearch.job.source;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.jobsearch.company.AtsType;

/**
 * amazon.jobs, through the JSON its own search page loads. Amazon posts hundreds of US roles a day, so this
 * source searches newest first and stops at the age cutoff (see Paging).
 */
@Component
class AmazonJobSource implements JobSource {

    static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter POSTED = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);

    private final RestClient http;
    private final Pacer pacer;

    AmazonJobSource(RestClient.Builder builder, Pacer pacer) {
        this.http = builder.baseUrl("https://www.amazon.jobs").build();
        this.pacer = pacer;
    }

    @Override
    public AtsType type() {
        return AtsType.AMAZON;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        return Paging.newestFirst(query, pacer, (search, page) -> page(search, page, query.usOnly()));
    }

    private List<FetchedJob> page(String search, int page, boolean usOnly) {
        Response response = http.get()
                .uri(b -> {
                    UriBuilder uri = b.path("/en/search.json")
                            .queryParam("sort", "recent")
                            .queryParam("result_limit", PAGE_SIZE)
                            .queryParam("offset", page * PAGE_SIZE);
                    if (usOnly) {
                        uri.queryParam("normalized_country_code[]", "USA");
                    }
                    if (search != null) {
                        uri.queryParam("base_query", search);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(Response.class);
        if (response == null || response.jobs() == null) {
            throw new FeedFormatException("amazon.jobs answered without a job list");
        }
        return response.jobs().stream().map(AmazonJobSource::toFetched).toList();
    }

    private static FetchedJob toFetched(Job j) {
        String location = j.normalizedLocation() != null ? j.normalizedLocation() : j.location();
        return new FetchedJob(
                j.id(),
                j.title(),
                j.jobFamily(),
                location,
                Workplace.UNKNOWN.orFromLocation(location),
                Region.fromCountry(j.countryCode()),
                posted(j.postedDate()),
                j.jobPath() == null ? null : "https://www.amazon.jobs" + j.jobPath(),
                description(j));
    }

    private static String description(Job j) {
        List<String> parts = new ArrayList<>();
        add(parts, null, j.description());
        add(parts, "Basic qualifications", j.basicQualifications());
        add(parts, "Preferred qualifications", j.preferredQualifications());
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private static void add(List<String> parts, String heading, String html) {
        String text = Html.toText(html);
        if (text != null) {
            parts.add(heading == null ? text : heading + "\n" + text);
        }
    }

    /** "October 23, 2026"; a date without a time is taken as the start of that day, UTC. */
    static Instant posted(String date) {
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip(), POSTED).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    record Response(Integer hits, List<Job> jobs) {}

    record Job(
            @JsonProperty("id_icims") String id,
            String title,
            @JsonProperty("job_family") String jobFamily,
            String location,
            @JsonProperty("normalized_location") String normalizedLocation,
            @JsonProperty("country_code") String countryCode,
            @JsonProperty("posted_date") String postedDate,
            @JsonProperty("job_path") String jobPath,
            String description,
            @JsonProperty("basic_qualifications") String basicQualifications,
            @JsonProperty("preferred_qualifications") String preferredQualifications) {}
}
