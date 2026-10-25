package com.jobsearch.job.source;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whether a role is in the United States. Feeds describe locations in free text ("US, CA, Santa Clara",
 * "New York, New York, United States of America", "Remote - US", "Toronto"), so text is read with a
 * conservative set of rules: UNKNOWN when unsure, so a US role is never dropped by mistake.
 */
public enum Region {
    US,
    NON_US,
    UNKNOWN;

    // "America" alone is left out: "Latin America" is not the US
    private static final Pattern US_MARKERS = Pattern.compile("\\b(united states|usa|us)\\b|(^|[^a-z])u\\.s\\.");

    private static final Set<String> STATES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
            "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC",
            "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY");

    private static final Pattern STATE_NAMES = words(
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado", "connecticut", "delaware",
            "florida", "georgia", "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas", "kentucky",
            "louisiana", "maine", "maryland", "massachusetts", "michigan", "minnesota", "mississippi", "missouri",
            "montana", "nebraska", "nevada", "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "ohio", "oklahoma", "oregon", "pennsylvania", "rhode island",
            "south carolina", "south dakota", "tennessee", "texas", "utah", "vermont", "virginia", "washington",
            "west virginia", "wisconsin", "wyoming");

    /** Big US tech hubs that feeds often list without a state. */
    private static final Pattern US_CITIES = words(
            "san francisco", "bay area", "seattle", "nyc", "boston", "austin", "chicago", "los angeles",
            "mountain view", "sunnyvale", "palo alto", "menlo park", "redmond", "bellevue", "denver", "atlanta",
            "san jose", "santa clara", "cupertino", "pittsburgh", "washington dc", "washington, d.c.");

    private static final Pattern ELSEWHERE = words(
            "canada", "toronto", "vancouver", "montreal", "ontario", "mexico", "brazil", "são paulo", "sao paulo",
            "argentina", "colombia", "chile", "united kingdom", "uk", "england", "london", "scotland", "ireland",
            "dublin", "france", "paris", "germany", "berlin", "munich", "netherlands", "amsterdam", "spain",
            "madrid", "barcelona", "portugal", "lisbon", "italy", "milan", "switzerland", "zurich", "sweden",
            "stockholm", "denmark", "copenhagen", "norway", "finland", "poland", "warsaw", "romania", "czech",
            "prague", "austria", "belgium", "israel", "tel aviv", "uae", "dubai", "india", "bangalore",
            "bengaluru", "hyderabad", "pune", "mumbai", "delhi", "gurgaon", "chennai", "singapore", "japan",
            "tokyo", "china", "beijing", "shanghai", "shenzhen", "hong kong", "taiwan", "taipei", "korea",
            "seoul", "australia", "sydney", "melbourne", "new zealand", "philippines", "indonesia", "vietnam",
            "malaysia", "thailand", "south africa", "nigeria", "kenya", "egypt", "emea", "apac", "latam", "latin america",
            "south america", "europe", "asia");

    public static Region fromLocation(String location) {
        if (location == null || location.isBlank()) {
            return UNKNOWN;
        }
        String text = location.toLowerCase(Locale.ROOT);
        if (US_MARKERS.matcher(text).find() || hasStateCode(location) || STATE_NAMES.matcher(text).find()
                || US_CITIES.matcher(text).find()) {
            return US;
        }
        return ELSEWHERE.matcher(text).find() ? NON_US : UNKNOWN;
    }

    /** "Austin, TX" or "US-TX": a two-letter state code after a separator, in capitals as feeds write them. */
    private static boolean hasStateCode(String location) {
        for (String part : location.split("[,;/()|\\-–]")) {
            if (STATES.contains(part.strip())) {
                return true;
            }
        }
        return false;
    }

    /** One pattern matching any of the words as a whole word, so "uk" doesn't match "milwaukee". */
    private static Pattern words(String... words) {
        String any = String.join("|", Arrays.stream(words).map(Pattern::quote).toList());
        return Pattern.compile("(^|[^\\p{L}])(" + any + ")($|[^\\p{L}])");
    }

    /** A role listed in several places counts as US when any of them is. */
    public static Region anyOf(List<Region> regions) {
        if (regions.contains(US)) {
            return US;
        }
        return !regions.isEmpty() && regions.stream().allMatch(r -> r == NON_US) ? NON_US : UNKNOWN;
    }

    /** Reads a country name or code from a structured field ("US", "USA", "United States", "IN"). */
    public static Region fromCountry(String country) {
        if (country == null || country.isBlank()) {
            return UNKNOWN;
        }
        String c = country.strip().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "us", "usa", "united states", "united states of america" -> US;
            default -> NON_US;
        };
    }
}
