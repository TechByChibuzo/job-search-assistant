package com.jobsearch.job;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads how senior a role is: from its title (Principal, Staff, Senior, Lead, Manager…) and from the
 * years of experience its posting asks for. Years count only when the sentence ties them to experience ("8 years
 * of experience in…", "Experience: 10+ years…"), so "for more than 25 years" or "over the next 2 years" don't.
 * When a posting offers several paths ("8 years with a bachelor's, or 3 with a PhD"), the fewest years count,
 * so a role the owner could qualify for isn't hidden. A level number (III, IV) means different things at
 * different companies (Google's Software Engineer III asks for about 2 years, Amazon's SDE III about 5), so it
 * counts as senior only when the posting states no years.
 */
final class ExperienceCheck {

    record Result(Integer minYears, String evidence, boolean seniorTitle) {}

    private static final int MAX_EVIDENCE = 320;

    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("fifteen", 15));

    private static final String NUMBER = "(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen)";
    private static final String YEARS = "\\s*(?:\\+|plus)?\\s*(?:or more\\s+)?(?:(?:-|–|to)\\s*\\d{1,2}\\s*)?(?:years?|yrs?)\\b";

    /** "5+ years of … experience", with "experience" later in the sentence. */
    private static final Pattern YEARS_THEN_EXPERIENCE = Pattern.compile("(?<![\\d.])" + NUMBER + YEARS + "(?=[^.;]{0,90}?experien)");
    /** "Experience: 10+ years …". */
    private static final Pattern EXPERIENCE_THEN_YEARS = Pattern.compile("experien[^.;]{0,30}?(?<![\\d.])" + NUMBER + YEARS);

    private static final Pattern SENIOR = Pattern.compile(
            "\\b(principal|staff|senior|sr|lead|manager|director|head of|distinguished|architect|fellow|vp|vice president)\\b");
    private static final Pattern LEVEL = Pattern.compile("\\b(iii|iv)\\b");

    private ExperienceCheck() {}

    static Result check(String title, String description) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        Integer fewest = null;
        String evidence = null;
        if (description != null && !description.isBlank()) {
            for (String sentence : VisaCheck.sentences(description)) {
                String s = sentence.toLowerCase(Locale.ROOT);
                for (Pattern p : new Pattern[] {YEARS_THEN_EXPERIENCE, EXPERIENCE_THEN_YEARS}) {
                    Matcher m = p.matcher(s);
                    while (m.find()) {
                        int years = WORDS.getOrDefault(m.group(1), m.group(1).chars().allMatch(Character::isDigit) ? Integer.parseInt(m.group(1)) : 0);
                        if (years > 0 && years <= 30 && (fewest == null || years < fewest)) {
                            fewest = years;
                            evidence = sentence.length() <= MAX_EVIDENCE ? sentence : sentence.substring(0, MAX_EVIDENCE - 1).strip() + "…";
                        }
                    }
                }
            }
        }
        boolean senior = SENIOR.matcher(t).find() || (fewest == null && LEVEL.matcher(t).find());
        return new Result(fewest, evidence, senior);
    }
}
