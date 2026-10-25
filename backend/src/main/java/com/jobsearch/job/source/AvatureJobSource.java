package com.jobsearch.job.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobsearch.company.AtsType;

/**
 * Avature career sites (Bloomberg and others), read from their search-results pages. Avature's RSS feed
 * ignores paging and has no locations, so the HTML list is the only full source. It has no posting dates
 * either, so these roles are aged by when the app first saw them. The board is {@code subdomain/portal},
 * for example {@code bloomberg/careers}.
 */
@Component
class AvatureJobSource implements JobSource {

    static final int PAGE_SIZE = 12;
    private static final Pattern BOARD = Pattern.compile("([a-z0-9-]+)/([A-Za-z0-9_-]+)");
    private static final Pattern JOB_ID = Pattern.compile("/JobDetail/[^/]*/(\\d+)");

    private final RestClient.Builder builder;
    private final Pacer pacer;

    AvatureJobSource(RestClient.Builder builder, Pacer pacer) {
        this.builder = builder;
        this.pacer = pacer;
    }

    @Override
    public AtsType type() {
        return AtsType.AVATURE;
    }

    @Override
    public FetchResult fetch(String board, FetchQuery query) {
        Matcher m = BOARD.matcher(board);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not an Avature board: " + board);
        }
        RestClient http = builder.clone().baseUrl("https://" + m.group(1) + ".avature.net/" + m.group(2)).build();
        Map<String, FetchedJob> jobs = new LinkedHashMap<>();
        for (int page = 0; page < pacer.maxPages(); page++) {
            if (page > 0) {
                pacer.beforeNextPage();
            }
            int offset = page * PAGE_SIZE;
            String html = http.get()
                    .uri("/SearchJobs/?listFilterMode=1&jobRecordsPerPage={size}&jobOffset={offset}", PAGE_SIZE, offset)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new BoardNotFoundException(type(), board);
                    })
                    .body(String.class);
            List<FetchedJob> batch = parse(html);
            batch.forEach(j -> jobs.putIfAbsent(j.externalId(), j));
            if (batch.size() < PAGE_SIZE) {
                return FetchResult.complete(List.copyOf(jobs.values()));
            }
        }
        // Hit the page limit without reaching the end: some open roles were never seen
        return FetchResult.incomplete(List.copyOf(jobs.values()));
    }

    static List<FetchedJob> parse(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        Map<String, FetchedJob> jobs = new LinkedHashMap<>();
        for (Element article : doc.select("article.article--result")) {
            Element link = article.selectFirst("a[href*=/JobDetail/]");
            if (link == null) {
                continue;
            }
            Matcher id = JOB_ID.matcher(link.attr("href"));
            if (!id.find()) {
                continue;
            }
            List<String> places = article.select(".list-item-location").eachText();
            String location = places.isEmpty() ? null : String.join("; ", places);
            jobs.putIfAbsent(id.group(1), new FetchedJob(
                    id.group(1),
                    link.text(),
                    null,
                    location,
                    Workplace.UNKNOWN.orFromLocation(location),
                    Region.anyOf(places.stream().map(Region::fromLocation).toList()),
                    null,
                    link.absUrl("href").isEmpty() ? link.attr("href") : link.absUrl("href"),
                    null));
        }
        return List.copyOf(jobs.values());
    }
}
