package com.jobsearch.job.source;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/** https://developers.ashbyhq.com/docs/public-job-posting-api */
@Component
class AshbyJobSource implements JobSource {

    private final RestClient http;

    AshbyJobSource(RestClient.Builder builder) {
        this.http = builder.baseUrl("https://api.ashbyhq.com/posting-api/job-board").build();
    }

    @Override
    public AtsType type() {
        return AtsType.ASHBY;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        Response response = http.get()
                .uri("/{board}", board)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new BoardNotFoundException(type(), board);
                })
                .body(Response.class);
        if (response == null || response.jobs() == null) {
            return FetchResult.complete(List.of());
        }
        return FetchResult.complete(response.jobs().stream()
                .filter(j -> !Boolean.FALSE.equals(j.isListed()))
                .map(AshbyJobSource::toFetched)
                .toList());
    }

    private static FetchedJob toFetched(Job j) {
        String description = j.descriptionPlain() != null ? j.descriptionPlain().strip() : Html.toText(j.descriptionHtml());
        return new FetchedJob(
                j.id(),
                j.title(),
                j.team() != null ? j.team() : j.department(),
                j.location(),
                workplace(j),
                region(j),
                j.publishedAt() == null ? null : j.publishedAt().toInstant(),
                j.jobUrl(),
                description);
    }

    /** The primary address and any secondary locations; US when any of them is. */
    private static Region region(Job j) {
        List<Region> regions = new ArrayList<>();
        if (j.address() != null && j.address().postalAddress() != null) {
            regions.add(Region.fromCountry(j.address().postalAddress().addressCountry()));
        }
        if (j.secondaryLocations() != null) {
            for (SecondaryLocation s : j.secondaryLocations()) {
                if (s.address() != null && s.address().postalAddress() != null) {
                    regions.add(Region.fromCountry(s.address().postalAddress().addressCountry()));
                }
            }
        }
        regions.removeIf(r -> r == Region.UNKNOWN);
        return regions.isEmpty() ? Region.fromLocation(j.location()) : Region.anyOf(regions);
    }

    /** Ashby sets isRemote on hybrid roles too, so workplaceType wins when it is present. */
    private static Workplace workplace(Job j) {
        Workplace fromType = Workplace.fromLabel(j.workplaceType());
        if (fromType != Workplace.UNKNOWN) {
            return fromType;
        }
        if (Boolean.TRUE.equals(j.isRemote())) {
            return Workplace.REMOTE;
        }
        return Workplace.UNKNOWN.orFromLocation(j.location());
    }

    record Response(List<Job> jobs) {}

    record Job(
            String id,
            String title,
            String department,
            String team,
            String location,
            Address address,
            List<SecondaryLocation> secondaryLocations,
            Boolean isListed,
            Boolean isRemote,
            String workplaceType,
            OffsetDateTime publishedAt,
            String jobUrl,
            String descriptionPlain,
            String descriptionHtml) {}

    record Address(PostalAddress postalAddress) {}

    record PostalAddress(String addressCountry) {}

    record SecondaryLocation(String location, Address address) {}
}
