package com.jobsearch.job.source;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/**
 * Goldman Sachs careers (higher.gs.com), through the GraphQL API its own search page calls. Answers without
 * a login; searched newest first with a United States location filter.
 */
@Component
class GoldmanJobSource implements JobSource {

    static final int PAGE_SIZE = 20;
    private static final String ROLE_URL = "https://higher.gs.com/roles/";

    /** The fields the careers page itself asks for, plus the creation date and description. */
    private static final String QUERY = """
            query GetRoles($searchQueryInput: RoleSearchQueryInput!) {
              roleSearch(searchQueryInput: $searchQueryInput) {
                totalCount
                items {
                  roleId
                  jobTitle
                  division
                  createdDate
                  descriptionHtml
                  locations { primary state country city }
                  externalSource { sourceId }
                }
              }
            }""";

    private final RestClient http;
    private final Pacer pacer;

    GoldmanJobSource(RestClient.Builder builder, Pacer pacer) {
        this.http = builder.baseUrl("https://api-higher.gs.com/gateway/api/v1/graphql").build();
        this.pacer = pacer;
    }

    @Override
    public AtsType type() {
        return AtsType.GOLDMAN;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        return Paging.newestFirst(query, pacer, (search, page) -> page(search, page, query.usOnly()));
    }

    private List<FetchedJob> page(String search, int page, boolean usOnly) {
        List<Map<String, Object>> filters = usOnly
                ? List.of(Map.of("filterCategoryType", "LOCATION",
                        "filters", List.of(Map.of("filter", "United States", "subFilters", List.of()))))
                : List.of();
        Map<String, Object> input = Map.of(
                "page", Map.of("pageSize", PAGE_SIZE, "pageNumber", page),
                "sort", Map.of("sortStrategy", "POSTED_DATE", "sortOrder", "DESC"),
                "filters", filters,
                "experiences", List.of("EARLY_CAREER", "PROFESSIONAL"),
                "searchTerm", search == null ? "" : search);
        Response response = http.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("operationName", "GetRoles", "query", QUERY, "variables", Map.of("searchQueryInput", input)))
                .retrieve()
                .body(Response.class);
        if (response == null || response.data() == null || response.data().roleSearch() == null) {
            String why = response != null && response.errors() != null && !response.errors().isEmpty()
                    ? response.errors().getFirst().message()
                    : "no role list";
            throw new FeedFormatException("Goldman Sachs careers answered with " + why);
        }
        List<Role> items = response.data().roleSearch().items();
        return items == null ? List.of() : items.stream().map(GoldmanJobSource::toFetched).toList();
    }

    private static FetchedJob toFetched(Role role) {
        List<String> places = new ArrayList<>();
        List<Region> regions = new ArrayList<>();
        if (role.locations() != null) {
            for (Location l : role.locations()) {
                places.add(l.city() == null ? l.state() : l.state() == null ? l.city() : l.city() + ", " + l.state());
                regions.add(Region.fromCountry(l.country()));
            }
        }
        places.removeIf(p -> p == null);
        String location = places.isEmpty() ? null : String.join("; ", places);
        String sourceId = role.externalSource() != null ? role.externalSource().sourceId() : null;
        return new FetchedJob(
                role.roleId(),
                role.jobTitle(),
                role.division(),
                location,
                Workplace.UNKNOWN.orFromLocation(location),
                Region.anyOf(regions),
                role.createdDate() == null ? null : role.createdDate().toInstant(),
                ROLE_URL + (sourceId != null ? sourceId : role.roleId()),
                Html.toText(role.descriptionHtml()));
    }

    record Response(Data data, List<Error> errors) {}

    record Data(RoleSearch roleSearch) {}

    record RoleSearch(Integer totalCount, List<Role> items) {}

    record Role(
            String roleId,
            String jobTitle,
            String division,
            OffsetDateTime createdDate,
            String descriptionHtml,
            List<Location> locations,
            ExternalSource externalSource) {}

    record Location(Boolean primary, String state, String country, String city) {}

    record ExternalSource(String sourceId) {}

    record Error(String message) {}
}
