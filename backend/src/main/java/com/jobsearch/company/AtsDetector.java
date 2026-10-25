package com.jobsearch.company;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Works out the ATS and board name from a careers link, such as
 * {@code https://jobs.lever.co/palantir} → Lever, {@code palantir}.
 */
@Component
public class AtsDetector {

    private static final Pattern BOARD = Pattern.compile("[A-Za-z0-9._-]+");

    public Optional<DetectedBoard> detect(String careersUrl) {
        if (careersUrl == null || careersUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri = parse(careersUrl.strip());
        if (uri == null || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        List<String> path = Arrays.stream(uri.getPath() == null ? new String[0] : uri.getPath().split("/"))
                .filter(s -> !s.isBlank())
                .toList();

        return switch (host) {
            case "boards.greenhouse.io", "job-boards.greenhouse.io" -> greenhouse(uri, path);
            case "boards-api.greenhouse.io" -> segmentAfter(path, "boards").flatMap(b -> board(AtsType.GREENHOUSE, b));
            case "jobs.lever.co" -> first(path).flatMap(b -> board(AtsType.LEVER, b));
            case "api.lever.co" -> segmentAfter(path, "postings").flatMap(b -> board(AtsType.LEVER, b));
            case "jobs.ashbyhq.com" -> first(path).flatMap(b -> board(AtsType.ASHBY, b));
            case "api.ashbyhq.com" -> segmentAfter(path, "job-board").flatMap(b -> board(AtsType.ASHBY, b));
            default -> Optional.empty();
        };
    }

    /** Greenhouse also serves embedded boards as {@code /embed/job_board?for=airbnb}. */
    private Optional<DetectedBoard> greenhouse(URI uri, List<String> path) {
        if (!path.isEmpty() && path.getFirst().equals("embed")) {
            String board = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("for");
            return board(AtsType.GREENHOUSE, board);
        }
        return first(path).flatMap(b -> board(AtsType.GREENHOUSE, b));
    }

    private static Optional<DetectedBoard> board(AtsType ats, String board) {
        if (board == null || !BOARD.matcher(board).matches()) {
            return Optional.empty();
        }
        return Optional.of(new DetectedBoard(ats, board.toLowerCase(Locale.ROOT)));
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
