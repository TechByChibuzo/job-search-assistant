package com.jobsearch.company;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Works out a company's own website, for its logo, from what the app knows: a few well-known names, the
 * host its job links point at (careers.airbnb.com → airbnb.com), or failing that a guess from its name.
 */
public final class CompanyDomains {

    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    /** Hosts that belong to hiring systems and job boards, not to the company itself. */
    private static final List<String> NOT_THE_COMPANY = List.of(
            "greenhouse.io", "lever.co", "ashbyhq.com", "myworkdayjobs.com", "myworkdaysite.com", "avature.net",
            "linkedin.com", "amazon.jobs", "icims.com", "smartrecruiters.com", "workable.com", "jobvite.com",
            "taleo.net", "successfactors.com", "eightfold.ai", "indeed.com", "glassdoor.com", "example.com");

    private static final Map<String, String> KNOWN = Map.of(
            "amazon", "amazon.com",
            "google", "google.com",
            "goldman sachs", "goldmansachs.com",
            "meta", "meta.com",
            "microsoft", "microsoft.com",
            "apple", "apple.com",
            "lockheed martin", "lockheedmartin.com");

    /** Second-level parts that sit under a country code: "co" in example.co.uk. */
    private static final Set<String> SECOND_LEVEL = Set.of("co", "com", "org", "net", "ac", "gov");

    private CompanyDomains() {}

    public static Optional<String> known(String companyName) {
        return Optional.ofNullable(KNOWN.get(companyName == null ? "" : companyName.strip().toLowerCase(Locale.ROOT)));
    }

    /** The company's site from one of its job links, unless the link is on a hiring system or job board. */
    public static Optional<String> fromLink(String link) {
        try {
            String host = new URI(link.strip()).getHost();
            if (host == null) {
                return Optional.empty();
            }
            String domain = registrable(host.toLowerCase(Locale.ROOT));
            boolean jobBoard = NOT_THE_COMPANY.stream().anyMatch(b -> domain.equals(b) || domain.endsWith("." + b));
            return jobBoard ? Optional.empty() : Optional.of(domain);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** "Lockheed Martin" → "lockheedmartin.com": only a guess, checked against a real logo before use. */
    public static Optional<String> guess(String companyName) {
        String slug = companyName == null ? "" : companyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return slug.isEmpty() ? Optional.empty() : Optional.of(slug + ".com");
    }

    /** A domain typed by the owner, tidied ("https://www.Airbnb.com/careers" → "airbnb.com"), or empty if not one. */
    public static Optional<String> normalize(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String s = input.strip().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "").replaceFirst("[/?#].*$", "")
                .replaceFirst("^www\\.", "");
        return DOMAIN.matcher(s).matches() ? Optional.of(s) : Optional.empty();
    }

    /** careers.airbnb.com → airbnb.com; jobs.bbc.co.uk → bbc.co.uk. */
    static String registrable(String host) {
        String[] parts = host.replaceFirst("^www\\.", "").split("\\.");
        if (parts.length <= 2) {
            return String.join(".", parts);
        }
        int keep = parts[parts.length - 1].length() == 2 && SECOND_LEVEL.contains(parts[parts.length - 2]) ? 3 : 2;
        return String.join(".", Arrays.copyOfRange(parts, parts.length - keep, parts.length));
    }
}
