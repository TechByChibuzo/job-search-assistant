package com.jobsearch.answers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How alike two application questions are: the share of meaningful words they have in common (Jaccard).
 * "Why do you want to work at Nooks?" and "Why do you want to work here?" share why, want and work.
 */
final class QuestionMatcher {

    /** Alike enough to offer the saved answer; the owner still checks it on the form. */
    static final double THRESHOLD = 0.5;

    private static final Set<String> FILLER = Set.of(
            "a", "an", "the", "to", "of", "in", "for", "and", "or", "is", "are", "be", "you", "your", "yours", "us", "our",
            "we", "please", "at", "on", "with", "this", "that", "it", "do", "does", "did", "here", "there", "me", "my", "i",
            "can", "could", "would", "will", "if", "any", "as", "by", "from", "so", "into", "about", "briefly",
            // How the question is asked, not what it asks
            "tell", "describe", "share", "explain", "walk", "through", "give", "example", "provide",
            // What contractions leave behind: "you're" → "re"
            "re", "ve", "ll", "s", "t", "d", "m");

    private QuestionMatcher() {}

    static double similarity(String a, String b) {
        Set<String> wa = words(a);
        Set<String> wb = words(b);
        if (wa.isEmpty() || wb.isEmpty()) {
            return 0;
        }
        Set<String> both = new HashSet<>(wa);
        both.retainAll(wb);
        Set<String> either = new HashSet<>(wa);
        either.addAll(wb);
        return (double) both.size() / either.size();
    }

    static Set<String> words(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+"))
                .filter(w -> w.length() > 1 && !FILLER.contains(w))
                .map(QuestionMatcher::stem)
                .collect(Collectors.toSet());
    }

    /** "projects" → "project", "working" → "work": enough for questions, not a full stemmer. */
    private static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
