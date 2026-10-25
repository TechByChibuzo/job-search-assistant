package com.jobsearch.job.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/** https://github.com/lever/postings-api */
@Component
class LeverJobSource implements JobSource {

    private final RestClient http;

    LeverJobSource(RestClient.Builder builder) {
        this.http = builder.baseUrl("https://api.lever.co/v0/postings").build();
    }

    @Override
    public AtsType type() {
        return AtsType.LEVER;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        Posting[] postings = http.get()
                .uri("/{board}?mode=json", board)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new BoardNotFoundException(type(), board);
                })
                .body(Posting[].class);
        if (postings == null) {
            return FetchResult.complete(List.of());
        }
        return FetchResult.complete(Arrays.stream(postings).map(LeverJobSource::toFetched).toList());
    }

    private static FetchedJob toFetched(Posting p) {
        Categories c = p.categories() == null ? new Categories(null, null, null) : p.categories();
        String team = c.team() != null ? c.team() : c.department();
        return new FetchedJob(
                p.id(),
                p.text(),
                team,
                c.location(),
                Workplace.fromLabel(p.workplaceType()).orFromLocation(c.location()),
                p.country() != null ? Region.fromCountry(p.country()) : Region.fromLocation(c.location()),
                p.createdAt() == null ? null : Instant.ofEpochMilli(p.createdAt()),
                p.hostedUrl(),
                description(p));
    }

    /** Lever splits a posting into an intro, titled lists ("What you'll do") and a closing section. */
    private static String description(Posting p) {
        List<String> parts = new ArrayList<>();
        if (p.descriptionPlain() != null && !p.descriptionPlain().isBlank()) {
            parts.add(p.descriptionPlain().strip());
        }
        if (p.lists() != null) {
            for (Section section : p.lists()) {
                String body = Html.toText("<ul>" + (section.content() == null ? "" : section.content()) + "</ul>");
                parts.add((section.text() == null ? "" : section.text() + "\n") + (body == null ? "" : body));
            }
        }
        if (p.additionalPlain() != null && !p.additionalPlain().isBlank()) {
            parts.add(p.additionalPlain().strip());
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts).strip();
    }

    record Posting(
            String id,
            String text,
            String hostedUrl,
            Categories categories,
            String workplaceType,
            String country,
            Long createdAt,
            String descriptionPlain,
            List<Section> lists,
            String additionalPlain) {}

    record Categories(String team, String department, String location) {}

    record Section(String text, String content) {}
}
