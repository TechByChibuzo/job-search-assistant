package com.jobsearch.job.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.jobsearch.company.AtsType;

/**
 * Google Careers. Google has no public jobs API; its results page embeds the listings as a JSON array in an
 * {@code AF_initDataCallback} script, read here by position. If Google changes that page, this throws
 * FeedFormatException and the company shows "Last sync failed" instead of silently going empty.
 */
@Component
class GoogleJobSource implements JobSource {

    private static final Pattern DATA = Pattern.compile("AF_initDataCallback\\(\\{key: 'ds:1'.*?data:(.*?), sideChannel", Pattern.DOTALL);
    private static final String JOB_URL = "https://www.google.com/about/careers/applications/jobs/results/";

    private final RestClient http;
    private final Pacer pacer;
    private final ObjectMapper json;

    GoogleJobSource(RestClient.Builder builder, Pacer pacer, ObjectMapper json) {
        this.http = builder.baseUrl("https://www.google.com/about/careers/applications/jobs/results").build();
        this.pacer = pacer;
        this.json = json;
    }

    @Override
    public AtsType type() {
        return AtsType.GOOGLE;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        return Paging.newestFirst(query, pacer, (search, page) -> page(search, page, query.usOnly()));
    }

    private List<FetchedJob> page(String search, int page, boolean usOnly) {
        String html = http.get()
                .uri(b -> {
                    UriBuilder uri = b.queryParam("sort_by", "date").queryParam("page", page + 1);
                    if (usOnly) {
                        uri.queryParam("location", "United States");
                    }
                    if (search != null) {
                        uri.queryParam("q", search);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(String.class);
        return parse(html);
    }

    List<FetchedJob> parse(String html) {
        Matcher m = DATA.matcher(html == null ? "" : html);
        if (!m.find()) {
            throw new FeedFormatException("Google's careers page no longer has the job data this app reads");
        }
        JsonNode jobs;
        try {
            jobs = json.readTree(m.group(1)).path(0);
        } catch (JsonProcessingException e) {
            throw new FeedFormatException("Google's careers page has job data this app can't read: " + e.getOriginalMessage());
        }
        if (jobs.isNull() || jobs.isMissingNode()) {
            return List.of();
        }
        List<FetchedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            result.add(toFetched(job));
        }
        return result;
    }

    private static FetchedJob toFetched(JsonNode job) {
        String id = job.path(0).asText(null);
        if (id == null || job.path(1).asText(null) == null) {
            throw new FeedFormatException("Google's job data has changed shape");
        }
        List<String> places = new ArrayList<>();
        List<Region> regions = new ArrayList<>();
        for (JsonNode place : job.path(9)) {
            places.add(place.path(0).asText());
            regions.add(Region.fromCountry(place.path(5).asText(null)));
        }
        String location = places.isEmpty() ? null : String.join("; ", places);
        long seconds = job.path(12).path(0).asLong(0);
        return new FetchedJob(
                id,
                job.path(1).asText(),
                null,
                location,
                Workplace.UNKNOWN.orFromLocation(location),
                Region.anyOf(regions),
                seconds == 0 ? null : Instant.ofEpochSecond(seconds),
                JOB_URL + id,
                description(job));
    }

    private static String description(JsonNode job) {
        List<String> parts = new ArrayList<>();
        add(parts, null, job.path(10).path(1).asText(null));
        add(parts, "Responsibilities", job.path(3).path(1).asText(null));
        add(parts, "Qualifications", job.path(4).path(1).asText(null));
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private static void add(List<String> parts, String heading, String html) {
        String text = Html.toText(html);
        if (text != null) {
            parts.add(heading == null ? text : heading + "\n" + text);
        }
    }
}
