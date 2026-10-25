package com.jobsearch.alerts;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriComponentsBuilder;

/** Recognises links to job postings in alert emails, and strips them down to a stable form. */
final class AlertLinks {

    private static final Pattern LINKEDIN_JOB = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d+)");

    /** Tracking parameters email links carry; removing them lets the same role from two emails match. */
    private static final Set<String> TRACKING = Set.of(
            "trackingid", "refid", "lipi", "midtoken", "midsig", "trk", "trkemail", "eid", "otptoken", "src", "source",
            "ref", "ccuid", "cmpid", "mcid", "email", "alertid", "jobalertid", "savedsearchid");

    /** A job page on a careers site: /job/123, /jobs/123, /details/123, /position/123 and the like. */
    private static final Pattern JOB_PATH = Pattern.compile(
            "/(?:job|jobs|details|position|positions|posting|req|requisition)/(?:[^/?#]*/)*?[^/?#]*\\d{3,}[^/?#]*",
            Pattern.CASE_INSENSITIVE);

    private AlertLinks() {}

    static Optional<String> linkedInJobId(String href) {
        Matcher m = LINKEDIN_JOB.matcher(href == null ? "" : href);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    static String linkedInJobUrl(String id) {
        return "https://www.linkedin.com/jobs/view/" + id + "/";
    }

    /** Whether the link looks like one job posting rather than a search, a profile or a settings page. */
    static boolean looksLikeJobPosting(String href) {
        URI uri = parse(href);
        return uri != null && uri.getHost() != null && uri.getPath() != null && JOB_PATH.matcher(uri.getPath()).find();
    }

    /** The link without tracking parameters or fragment, so the same role matches across emails. */
    static String canonical(String href) {
        URI uri = parse(href);
        if (uri == null) {
            return href;
        }
        var builder = UriComponentsBuilder.fromUri(uri).fragment(null).replaceQuery(null);
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        params.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!TRACKING.contains(lower) && !lower.startsWith("utm_")) {
                builder.queryParam(name, values.toArray());
            }
        });
        return builder.build().toUriString();
    }

    static String host(String href) {
        URI uri = parse(href);
        return uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    /** Only absolute http(s) links; anything else (mailto:, javascript:, httpx:) is not a posting. */
    private static URI parse(String href) {
        if (href == null) {
            return null;
        }
        try {
            URI uri = new URI(href.strip().replace(" ", "%20"));
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Anchor text that names an action rather than a role. */
    static boolean isGenericText(String text) {
        String t = text.strip().toLowerCase(Locale.ROOT);
        return t.length() < 4 || GENERIC.contains(t) || t.startsWith("view ") || t.startsWith("see ") || t.startsWith("apply")
                || t.startsWith("http");
    }

    private static final Set<String> GENERIC = Set.of("view job", "view jobs", "apply now", "learn more", "see all jobs",
            "see more jobs", "view all jobs", "job details", "details", "unsubscribe", "manage alerts");
}
