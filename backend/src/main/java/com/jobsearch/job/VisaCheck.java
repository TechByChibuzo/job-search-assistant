package com.jobsearch.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a posting for what it says about visas and legal status: whether the role is for U.S. citizens only,
 * needs a security clearance, or comes without sponsorship. Phrases are matched within one sentence, never on a
 * lone word ("sponsor" also means Amazon's Sponsored Products), and the sentence is kept as evidence so the
 * owner can judge it.
 */
final class VisaCheck {

    record Result(VisaRestriction restriction, String evidence) {
        static final Result NONE = new Result(null, null);
    }

    private static final int MAX_EVIDENCE = 320;

    private static final String US = "u\\.?\\s?s\\.?(?:a\\.?)?";

    private static final List<Pattern> CITIZENS_ONLY = patterns(
            "(?:must|require[sd]?|required to)[^.]{0,60}?\\bbe\\s+an?\\s+" + US + "\\s+citizen",
            US + "\\s+citizenship\\s+(?:is\\s+)?required",
            "requires?\\s+" + US + "\\s+citizenship",
            US + "\\s+person\\s+status",
            "\\bonly\\s+(?:open\\s+to\\s+)?" + US + "\\s+citizens");

    private static final Pattern CLEARANCE_TERM = Pattern.compile("security clearance|ts/sci|top secret|secret clearance");
    private static final Pattern CLEARANCE_NEED = Pattern.compile("\\b(?:obtain|possess|maintain|hold|active|current|eligib|require|must)");
    /** Wording that makes a clearance a bonus rather than a condition. */
    private static final Pattern SOFTENED = Pattern.compile("\\b(?:a plus|preferred|nice to have|desired|bonus|advantage)");

    private static final String NEGATION =
            "(?:unable to|not able to|cannot|can't|can not|will not|won't|do not|does not|don't|doesn't|are not|is not)";

    private static final List<Pattern> NO_SPONSORSHIP = patterns(
            NEGATION + "\\s+(?:[\\w-]+\\s+){0,3}?sponsor",
            "without\\s+[^.]{0,50}?sponsorship",
            "sponsorship\\s+(?:is|are|will)\\s+not\\s+(?:be\\s+)?(?:available|offered|provided|possible)",
            "not\\s+eligible\\s+for\\s+[^.]{0,30}?sponsorship",
            "\\bno\\s+(?:visa\\s+|immigration\\s+|employment\\s+|h-1b\\s+)?sponsorship");

    private static final List<Pattern> MAY_BE_RESTRICTED = patterns(
            "legal status requirements",
            "export control licen[cs]e");

    /** Words whose final period isn't the end of a sentence. */
    private static final Set<String> ABBREVIATIONS = Set.of("u.s", "u.s.a", "i.e", "e.g", "etc", "inc", "vs", "jr", "sr", "no", "st");

    private VisaCheck() {}

    static Result check(String text) {
        if (text == null || text.isBlank()) {
            return Result.NONE;
        }
        Result best = Result.NONE;
        for (String sentence : sentences(text)) {
            VisaRestriction found = classify(sentence.toLowerCase(Locale.ROOT));
            if (found != null && (best.restriction() == null || found.ordinal() < best.restriction().ordinal())) {
                best = new Result(found, evidence(sentence));
            }
        }
        return best;
    }

    private static VisaRestriction classify(String s) {
        if (anyFind(CITIZENS_ONLY, s)) {
            return VisaRestriction.CITIZENS_ONLY;
        }
        if (CLEARANCE_TERM.matcher(s).find() && CLEARANCE_NEED.matcher(s).find()) {
            return SOFTENED.matcher(s).find() ? VisaRestriction.MAY_BE_RESTRICTED : VisaRestriction.CLEARANCE;
        }
        if (anyFind(NO_SPONSORSHIP, s)) {
            return VisaRestriction.NO_SPONSORSHIP;
        }
        return anyFind(MAY_BE_RESTRICTED, s) ? VisaRestriction.MAY_BE_RESTRICTED : null;
    }

    /** Lines and bullets, then sentences, without breaking at "U.S." or "i.e.". */
    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("[\\n\\r•]+")) {
            String s = line.replaceAll("\\s+", " ").strip();
            Matcher end = Pattern.compile("[.!?](?=\\s|$)").matcher(s);
            int start = 0;
            while (end.find()) {
                if (abbreviation(s, end.start())) {
                    continue;
                }
                add(out, s.substring(start, end.end()));
                start = end.end();
            }
            add(out, s.substring(start));
        }
        return out;
    }

    private static boolean abbreviation(String s, int period) {
        int from = s.lastIndexOf(' ', period - 1) + 1;
        String word = s.substring(from, period).replaceAll("^[(\"']+", "").toLowerCase(Locale.ROOT);
        return ABBREVIATIONS.contains(word);
    }

    private static void add(List<String> out, String sentence) {
        String s = sentence.strip();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }

    private static String evidence(String sentence) {
        return sentence.length() <= MAX_EVIDENCE ? sentence : sentence.substring(0, MAX_EVIDENCE - 1).strip() + "…";
    }

    private static boolean anyFind(List<Pattern> patterns, String s) {
        return patterns.stream().anyMatch(p -> p.matcher(s).find());
    }

    private static List<Pattern> patterns(String... regexes) {
        return List.of(regexes).stream().map(Pattern::compile).toList();
    }
}
