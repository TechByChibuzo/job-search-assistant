package com.jobsearch.job.source;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/** Turns job-description HTML into readable plain text, keeping paragraph and list breaks. */
final class Html {

    private static final Document.OutputSettings NO_PRETTY_PRINT = new Document.OutputSettings().prettyPrint(false);

    private Html() {}

    static String toText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings(NO_PRETTY_PRINT);
        doc.select("br").after("\\n");
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr").before("\\n");
        doc.select("li").prepend("• ");
        String marked = doc.body().html().replace("\\n", "\n");
        String text = Parser.unescapeEntities(Jsoup.clean(marked, "", Safelist.none(), NO_PRETTY_PRINT), false);
        return text
                .replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /** Greenhouse escapes its HTML (&lt;p&gt;), so it is unescaped once before parsing. */
    static String escapedToText(String escapedHtml) {
        return escapedHtml == null ? null : toText(Parser.unescapeEntities(escapedHtml, false));
    }
}
