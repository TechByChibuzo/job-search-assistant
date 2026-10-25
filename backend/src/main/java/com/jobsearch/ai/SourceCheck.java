package com.jobsearch.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks what Claude wrote against the resume, so "never invent experience" is enforced rather than hoped
 * for. A bullet must come with a quote from the resume that is really there, and every number it states
 * (a percentage, a count, a dollar amount) must appear in the resume too.
 */
public final class SourceCheck {

    /** Share of a quote's words that must appear in the resume, allowing for line breaks and PDF quirks. */
    private static final double QUOTE_COVERAGE = 0.9;
    private static final Pattern NUMBER = Pattern.compile("\\$?\\d[\\d,]*(\\.\\d+)?\\s?(%|k\\b|m\\b|x\\b|\\+)?",
            Pattern.CASE_INSENSITIVE);

    private final String resume;
    private final Set<String> resumeWords;
    private final Set<String> resumeNumbers;
    /** Emails and web addresses as the resume writes them, lightly normalised (see {@link #linkKey}). */
    private final Set<String> resumeLinks;

    /** An email address, or a web address such as github.com/name, as a whole contact item. */
    private static final Pattern LINK_ITEM = Pattern.compile(
            "(?i)^(?:[^\\s@]+@[^\\s@]+\\.[^\\s@]+|(?:https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?:[/?#]\\S*)?)$");
    private static final Pattern LINK_IN_TEXT = Pattern.compile(
            "(?i)[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+|(?:https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?:[/?#][\\w\\-./%?=&#~]*)?");

    public SourceCheck(String resumeText) {
        String raw = resumeText == null ? "" : resumeText;
        this.resume = normalize(raw);
        this.resumeWords = new HashSet<>(Arrays.asList(resume.split(" ")));
        this.resumeNumbers = numbers(raw);
        this.resumeLinks = new HashSet<>();
        Matcher links = LINK_IN_TEXT.matcher(raw);
        while (links.find()) {
            resumeLinks.add(linkKey(links.group()));
        }
    }

    /**
     * Whether a contact item may go on the page. An email or web address becomes a link, so it must match one
     * in the resume exactly (apart from case, the scheme, "www." and a trailing slash): word overlap isn't
     * enough, since "janedoe.github.com" has the same words as "github.com/janedoe". Without resume text to
     * compare, links are refused. Anything else (a phone number, a city) is shown as text and needs its words
     * in the resume.
     */
    public boolean contactAllowed(String item) {
        String text = item == null ? "" : item.strip();
        if (LINK_ITEM.matcher(text).matches()) {
            return available() && resumeLinks.contains(linkKey(text));
        }
        return mentions(text);
    }

    private static String linkKey(String link) {
        return link.toLowerCase(Locale.ROOT).strip()
                .replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "")
                .replaceFirst("[/.,;:)]+$", "");
    }

    /** Whether there is resume text to check against; a scanned PDF has none. */
    public boolean available() {
        return resume.length() > 50;
    }

    /** Problems with a bullet and its claimed source; empty when it checks out. */
    public List<String> checkBullet(String text, String source) {
        List<String> problems = new ArrayList<>();
        if (!available()) {
            return problems;
        }
        if (!quoteFound(source)) {
            problems.add("Its source wasn't found in your resume");
        }
        problems.addAll(checkNumbers(text));
        return problems;
    }

    /** Numbers in a sentence that the resume doesn't contain. */
    public List<String> checkNumbers(String text) {
        List<String> problems = new ArrayList<>();
        if (!available() || text == null) {
            return problems;
        }
        for (String n : numbers(text)) {
            if (!resumeNumbers.contains(n)) {
                problems.add("\"" + n + "\" isn't in your resume");
            }
        }
        return problems;
    }

    /** A skill counts when its words all appear in the resume. */
    public boolean mentions(String phrase) {
        if (!available()) {
            return true;
        }
        String p = normalize(phrase);
        return !p.isEmpty() && (resume.contains(p) || resumeWords.containsAll(Arrays.asList(p.split(" "))));
    }

    boolean quoteFound(String quote) {
        String q = normalize(quote == null ? "" : quote);
        if (q.length() < 8) {
            return false;
        }
        if (resume.contains(q)) {
            return true;
        }
        String[] words = q.split(" ");
        long present = Arrays.stream(words).filter(resumeWords::contains).count();
        return words.length >= 3 && (double) present / words.length >= QUOTE_COVERAGE;
    }

    /** Lower case, accents and punctuation gone, dashes and quotes unified, whitespace collapsed. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT)
                .replaceAll("[‐-―−]", "-")
                .replaceAll("[^a-z0-9%$.+#\\s-]", " ")
                .replaceAll("(?<![0-9])\\.|\\.(?![0-9])", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** "40%", "$1.2M", "3,000", "5+" → comparable forms: "40%", "$1.2m", "3000", "5+". */
    static Set<String> numbers(String text) {
        Set<String> found = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String n = m.group().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "");
            // Years and single digits ("2021", "3 years") are too common to be invented metrics worth flagging
            String digits = n.replaceAll("[^0-9.]", "");
            boolean year = digits.matches("(19|20)\\d{2}");
            if (!year && !(digits.length() == 1 && n.equals(digits))) {
                found.add(n);
            }
        }
        return found;
    }
}
