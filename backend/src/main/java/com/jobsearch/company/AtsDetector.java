package com.jobsearch.company;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Works out where a company's roles come from, given a careers link: for example
 * {@code https://jobs.lever.co/palantir} → Lever, {@code palantir}, or
 * {@code https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite} → Workday,
 * {@code nvidia.wd5/NVIDIAExternalCareerSite}.
 */
@Component
public class AtsDetector {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern WORKDAY_HOST = Pattern.compile("([a-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com");
    private static final Pattern WORKDAY_BOARD = Pattern.compile("([a-z0-9-]+)\\.(wd\\d+)/([A-Za-z0-9_-]+)");
    private static final Pattern AVATURE_HOST = Pattern.compile("([a-z0-9-]+)\\.avature\\.net");
    private static final Pattern AVATURE_BOARD = Pattern.compile("([a-z0-9-]+)/([A-Za-z0-9_-]+)");
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}(-[A-Za-z]{2})?");

    public Optional<DetectedBoard> detect(String careersUrl) {
        if (careersUrl == null || careersUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri = parse(careersUrl.strip());
        if (uri == null || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        List<String> path = Arrays.stream(uri.getPath() == null ? new String[0] : uri.getPath().split("/"))
                .filter(s -> !s.isBlank())
                .toList();

        Matcher workday = WORKDAY_HOST.matcher(host);
        if (workday.matches()) {
            return workdaySite(path).flatMap(site ->
                    normalize(AtsType.WORKDAY, workday.group(1) + "." + workday.group(2) + "/" + site));
        }
        Matcher avature = AVATURE_HOST.matcher(host);
        if (avature.matches()) {
            return first(path).flatMap(portal -> normalize(AtsType.AVATURE, avature.group(1) + "/" + portal));
        }

        return switch (host) {
            case "boards.greenhouse.io", "job-boards.greenhouse.io" -> greenhouse(uri, path);
            case "boards-api.greenhouse.io" -> segmentAfter(path, "boards").flatMap(b -> normalize(AtsType.GREENHOUSE, b));
            case "jobs.lever.co" -> first(path).flatMap(b -> normalize(AtsType.LEVER, b));
            case "api.lever.co" -> segmentAfter(path, "postings").flatMap(b -> normalize(AtsType.LEVER, b));
            case "jobs.ashbyhq.com" -> first(path).flatMap(b -> normalize(AtsType.ASHBY, b));
            case "api.ashbyhq.com" -> segmentAfter(path, "job-board").flatMap(b -> normalize(AtsType.ASHBY, b));
            case "amazon.jobs" -> Optional.of(new DetectedBoard(AtsType.AMAZON, "amazon"));
            case "careers.google.com" -> Optional.of(new DetectedBoard(AtsType.GOOGLE, "google"));
            case "google.com" -> path.size() >= 2 && path.get(0).equals("about") && path.get(1).equals("careers")
                    ? Optional.of(new DetectedBoard(AtsType.GOOGLE, "google"))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Checks a board typed by hand and puts it in the stored form. Most boards are case-insensitive and
     * stored in lower case; Workday and Avature site names keep their case because their URLs use it.
     */
    public Optional<DetectedBoard> normalize(AtsType ats, String board) {
        if (board == null || board.isBlank()) {
            return Optional.empty();
        }
        String b = board.strip();
        return switch (ats) {
            case GREENHOUSE, LEVER, ASHBY -> NAME.matcher(b).matches()
                    ? Optional.of(new DetectedBoard(ats, b.toLowerCase(Locale.ROOT)))
                    : Optional.empty();
            case WORKDAY -> matchKeepingSite(ats, WORKDAY_BOARD, b);
            case AVATURE -> matchKeepingSite(ats, AVATURE_BOARD, b);
            case AMAZON -> Optional.of(new DetectedBoard(ats, "amazon"));
            case GOOGLE -> Optional.of(new DetectedBoard(ats, "google"));
        };
    }

    private static Optional<DetectedBoard> matchKeepingSite(AtsType ats, Pattern pattern, String board) {
        int slash = board.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String normalised = board.substring(0, slash).toLowerCase(Locale.ROOT) + board.substring(slash);
        return pattern.matcher(normalised).matches() ? Optional.of(new DetectedBoard(ats, normalised)) : Optional.empty();
    }

    /** Workday links look like /en-US/{site}/job/..., /{site}, or the API's /wday/cxs/{tenant}/{site}/jobs. */
    private static Optional<String> workdaySite(List<String> path) {
        if (path.size() >= 4 && path.get(0).equals("wday") && path.get(1).equals("cxs")) {
            return Optional.of(path.get(3));
        }
        int i = !path.isEmpty() && LOCALE.matcher(path.getFirst()).matches() ? 1 : 0;
        return i < path.size() ? Optional.of(path.get(i)) : Optional.empty();
    }

    /** Greenhouse also serves embedded boards as {@code /embed/job_board?for=airbnb}. */
    private Optional<DetectedBoard> greenhouse(URI uri, List<String> path) {
        if (!path.isEmpty() && path.getFirst().equals("embed")) {
            String board = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("for");
            return normalize(AtsType.GREENHOUSE, board);
        }
        return first(path).flatMap(b -> normalize(AtsType.GREENHOUSE, b));
    }

    private static Optional<String> first(List<String> path) {
        return path.stream().findFirst();
    }

    private static Optional<String> segmentAfter(List<String> path, String marker) {
        int i = path.indexOf(marker);
        return i >= 0 && i + 1 < path.size() ? Optional.of(path.get(i + 1)) : Optional.empty();
    }

    private static URI parse(String url) {
        String withScheme = url.contains("://") ? url : "https://" + url;
        try {
            return new URI(withScheme);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
