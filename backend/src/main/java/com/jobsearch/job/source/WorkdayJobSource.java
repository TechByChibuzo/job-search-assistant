package com.jobsearch.job.source;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/**
 * Workday career sites, through the JSON API their own pages call
 * ({@code POST /wday/cxs/{tenant}/{site}/jobs}). Many large companies use Workday: Nvidia, Salesforce, Adobe
 * and others. The board is stored as {@code tenant.wdN/site}, for example {@code nvidia.wd5/NVIDIAExternalCareerSite}.
 */
@Component
class WorkdayJobSource implements JobSource {

    static final int PAGE_SIZE = 20;
    private static final Pattern BOARD = Pattern.compile("([a-z0-9-]+)\\.(wd\\d+)/([A-Za-z0-9_-]+)");
    /** A role's path on its board, as listings give it: "/job/US-CA-Santa-Clara/Engineer_JR123". */
    private static final Pattern EXTERNAL_PATH = Pattern.compile("/job/[A-Za-z0-9_.,~()%-]+(/[A-Za-z0-9_.,~()%-]+)*");
    private static final Pattern DAYS_AGO = Pattern.compile("(\\d+)\\+? Days? Ago", Pattern.CASE_INSENSITIVE);

    private final RestClient.Builder builder;
    private final Pacer pacer;

    WorkdayJobSource(RestClient.Builder builder, Pacer pacer) {
        this.builder = builder;
        this.pacer = pacer;
    }

    @Override
    public AtsType type() {
        return AtsType.WORKDAY;
    }

    /** One role's details, from the same API the careers page loads for it: {@code GET …/{site}{externalPath}}. */
    @Override
    public Optional<String> describe(String board, String externalId, String url) {
        if (externalId == null || !EXTERNAL_PATH.matcher(externalId).matches()) {
            return Optional.empty();
        }
        Site site = Site.parse(board);
        JsonNode details = builder.clone().build().get()
                .uri(URI.create(site.host() + "/wday/cxs/" + site.tenant() + "/" + site.site() + externalId))
                .retrieve()
                .body(JsonNode.class);
        return Optional.ofNullable(details)
                .map(d -> Html.toText(d.path("jobPostingInfo").path("jobDescription").asText(null)))
                .filter(text -> !text.isBlank());
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        Site site = Site.parse(board);
        RestClient http = builder.clone().baseUrl(site.apiBase()).build();
        Optional<Facet> us = query.usOnly() ? lookUpUnitedStates(http, site) : Optional.empty();
        Map<String, List<String>> facets = us.map(f -> Map.of(f.parameter(), List.of(f.id()))).orElse(Map.of());
        return Paging.newestFirst(query, pacer, (search, page) -> {
            Page response = http.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appliedFacets", facets, "limit", PAGE_SIZE, "offset", page * PAGE_SIZE,
                            "searchText", search == null ? "" : search))
                    .retrieve()
                    .onStatus(s -> s.value() == 404 || s.value() == 422, (req, res) -> {
                        throw new BoardNotFoundException(type(), board);
                    })
                    .body(Page.class);
            if (response == null || response.jobPostings() == null) {
                return List.of();
            }
            return response.jobPostings().stream()
                    .map(p -> toFetched(p, site, us.isPresent(), query.now()))
                    .toList();
        });
    }

    /** Each tenant has its own id for "United States" in its location facets, so it is looked up by name. */
    private Optional<Facet> lookUpUnitedStates(RestClient http, Site site) {
        Page first = http.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("appliedFacets", Map.of(), "limit", 1, "offset", 0, "searchText", ""))
                .retrieve()
                .onStatus(s -> s.value() == 404 || s.value() == 422, (req, res) -> {
                    throw new BoardNotFoundException(type(), site.board());
                })
                .body(Page.class);
        return first == null || first.facets() == null ? Optional.empty() : findUnitedStates(first.facets());
    }

    /**
     * Facets nest: a group such as "locationMainGroup" holds facets such as "locationHierarchy1", whose values
     * are countries. Returns the first location facet with a value naming the United States.
     */
    static Optional<Facet> findUnitedStates(JsonNode facets) {
        for (JsonNode facet : facets) {
            Optional<Facet> found = findIn(facet);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Facet> findIn(JsonNode facet) {
        String parameter = facet.path("facetParameter").asText("");
        for (JsonNode value : facet.path("values")) {
            if (value.has("facetParameter")) {
                Optional<Facet> nested = findIn(value);
                if (nested.isPresent()) {
                    return nested;
                }
            } else if (parameter.toLowerCase(Locale.ROOT).contains("location")
                    && Region.fromCountry(value.path("descriptor").asText(null)) == Region.US) {
                return Optional.of(new Facet(parameter, value.path("id").asText()));
            }
        }
        return Optional.empty();
    }

    private static FetchedJob toFetched(Posting p, Site site, boolean filteredToUs, Instant now) {
        String location = p.locationsText();
        // "6 Locations" says nothing about where
        boolean vague = location != null && location.matches("\\d+ Locations?");
        return new FetchedJob(
                p.externalPath(),
                p.title(),
                null,
                vague ? null : location,
                Workplace.UNKNOWN.orFromLocation(location),
                filteredToUs ? Region.US : Region.UNKNOWN,
                posted(p.postedOn(), now),
                site.jobUrl(p.externalPath()),
                null);
    }

    /**
     * Workday only gives relative dates: "Posted Today", "Posted Yesterday", "Posted 3 Days Ago", and
     * "Posted 30+ Days Ago" for anything older, which is read as 31 days.
     */
    static Instant posted(String postedOn, Instant now) {
        if (postedOn == null) {
            return null;
        }
        String text = postedOn.toLowerCase(Locale.ROOT);
        if (text.contains("today")) {
            return now;
        }
        if (text.contains("yesterday")) {
            return now.minus(Duration.ofDays(1));
        }
        Matcher m = DAYS_AGO.matcher(postedOn);
        if (m.find()) {
            int days = Integer.parseInt(m.group(1));
            return now.minus(Duration.ofDays(postedOn.contains("+") ? days + 1 : days));
        }
        return null;
    }

    record Site(String tenant, String instance, String site) {

        static Site parse(String board) {
            Matcher m = BOARD.matcher(board);
            if (!m.matches()) {
                throw new IllegalArgumentException("Not a Workday board: " + board);
            }
            return new Site(m.group(1), m.group(2), m.group(3));
        }

        String board() {
            return tenant + "." + instance + "/" + site;
        }

        String host() {
            return "https://" + tenant + "." + instance + ".myworkdayjobs.com";
        }

        String apiBase() {
            return host() + "/wday/cxs/" + tenant + "/" + site + "/jobs";
        }

        String jobUrl(String externalPath) {
            return externalPath == null ? null : host() + "/" + site + externalPath;
        }
    }

    record Facet(String parameter, String id) {}

    record Page(Integer total, List<Posting> jobPostings, JsonNode facets) {}

    record Posting(String title, String externalPath, String locationsText, String postedOn) {}
}
