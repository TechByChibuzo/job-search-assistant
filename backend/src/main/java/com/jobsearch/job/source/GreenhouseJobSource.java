package com.jobsearch.job.source;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/** https://developers.greenhouse.io/job-board.html */
@Component
class GreenhouseJobSource implements JobSource {

    private final RestClient http;

    GreenhouseJobSource(RestClient.Builder builder) {
        this.http = builder.baseUrl("https://boards-api.greenhouse.io/v1/boards").build();
    }

    @Override
    public AtsType type() {
        return AtsType.GREENHOUSE;
    }

    @Override
    public List<FetchedJob> fetch(String board) {
        Response response = http.get()
                .uri("/{board}/jobs?content=true", board)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new BoardNotFoundException(type(), board);
                })
                .body(Response.class);
        if (response == null || response.jobs() == null) {
            return List.of();
        }
        return response.jobs().stream().map(GreenhouseJobSource::toFetched).toList();
    }

    private static FetchedJob toFetched(Job job) {
        String location = job.location() == null ? null : job.location().name();
        String team = job.departments() == null || job.departments().isEmpty() ? null : job.departments().getFirst().name();
        OffsetDateTime posted = job.firstPublished() != null ? job.firstPublished() : job.updatedAt();
        return new FetchedJob(
                String.valueOf(job.id()),
                job.title(),
                team,
                location,
                Workplace.fromLabel(workplaceType(job)).orFromLocation(location),
                posted == null ? null : posted.toInstant(),
                job.absoluteUrl(),
                Html.escapedToText(job.content()));
    }

    /** Greenhouse has no workplace field; many companies add a "Workplace Type" custom field instead. */
    private static String workplaceType(Job job) {
        if (job.metadata() == null) {
            return null;
        }
        return job.metadata().stream()
                .filter(m -> m.name() != null && m.name().equalsIgnoreCase("Workplace Type"))
                .map(Metadata::value)
                .filter(v -> v != null && v.isTextual())
                .map(JsonNode::asText)
                .findFirst()
                .orElse(null);
    }

    record Response(List<Job> jobs) {}

    record Job(
            long id,
            String title,
            @JsonProperty("absolute_url") String absoluteUrl,
            Named location,
            List<Named> departments,
            List<Metadata> metadata,
            @JsonProperty("first_published") OffsetDateTime firstPublished,
            @JsonProperty("updated_at") OffsetDateTime updatedAt,
            String content) {}

    record Named(String name) {}

    /** Custom fields; the value can be text, a boolean, a list or null. */
    record Metadata(String name, JsonNode value) {}
}
