package com.jobsearch.job.source;

import java.time.OffsetDateTime;
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
    public List<FetchedJob> fetch(String board) {
        Response response = http.get()
                .uri("/{board}", board)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new BoardNotFoundException(type(), board);
                })
                .body(Response.class);
        if (response == null || response.jobs() == null) {
            return List.of();
        }
        return response.jobs().stream()
                .filter(j -> !Boolean.FALSE.equals(j.isListed()))
                .map(AshbyJobSource::toFetched)
                .toList();
    }

    private static FetchedJob toFetched(Job j) {
        String description = j.descriptionPlain() != null ? j.descriptionPlain().strip() : Html.toText(j.descriptionHtml());
        return new FetchedJob(
                j.id(),
                j.title(),
                j.team() != null ? j.team() : j.department(),
                j.location(),
                workplace(j),
                j.publishedAt() == null ? null : j.publishedAt().toInstant(),
                j.jobUrl(),
                description);
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
            Boolean isListed,
            Boolean isRemote,
            String workplaceType,
            OffsetDateTime publishedAt,
            String jobUrl,
            String descriptionPlain,
            String descriptionHtml) {}
}
