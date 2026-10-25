package com.jobsearch.alerts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * LinkedIn job-alert emails. Each role is a card: a link to {@code linkedin.com/comm/jobs/view/{id}} with the
 * title as its text (the logo links there too), followed by a "Company · Location" line.
 */
@Component
@Order(1)
class LinkedInAlertParser implements AlertParser {

    @Override
    public boolean handles(AlertEmail email) {
        return email.senderAddress().endsWith("linkedin.com");
    }

    @Override
    public List<AlertJob> parse(AlertEmail email) {
        if (email.html() == null) {
            return List.of();
        }
        Map<String, AlertJob> jobs = new LinkedHashMap<>();
        for (Element a : Jsoup.parse(email.html()).select("a[href]")) {
            String id = AlertLinks.linkedInJobId(a.attr("href")).orElse(null);
            String title = a.text().strip();
            if (id == null || jobs.containsKey(id) || AlertLinks.isGenericText(title) || title.contains("·")) {
                continue;
            }
            String[] companyAndPlace = companyAndPlace(a, title);
            if (companyAndPlace == null) {
                continue;
            }
            jobs.put(id, new AlertJob(AlertLinks.linkedInJobUrl(id), title, companyAndPlace[0], companyAndPlace[1]));
        }
        return List.copyOf(jobs.values());
    }

    /**
     * The "Company · Location" line that follows the title in its card. Looks in the closest ancestor that
     * holds one, at most a few levels up, reading only from the title onwards and stopping at the next card.
     */
    private static String[] companyAndPlace(Element title, String titleText) {
        Element block = title;
        for (int depth = 0; depth < 6 && block.parent() != null; depth++) {
            block = block.parent();
            boolean afterTitle = false;
            for (Element e : block.select("p, span, div, td, a")) {
                if (e == title) {
                    afterTitle = true;
                    continue;
                }
                if (!afterTitle) {
                    continue;
                }
                if (isAnotherCard(e, title)) {
                    break;
                }
                String line = e.ownText().strip();
                if (line.contains("·") && !line.contains(titleText)) {
                    String[] parts = line.split("·");
                    String company = parts[0].strip();
                    String place = parts.length > 1 ? parts[1].strip() : null;
                    return company.isEmpty() ? null : new String[] {company, place == null || place.isEmpty() ? null : place};
                }
            }
        }
        return null;
    }

    /** A link to a different job: the start of the next card. */
    private static boolean isAnotherCard(Element e, Element title) {
        if (!e.tagName().equals("a")) {
            return false;
        }
        var id = AlertLinks.linkedInJobId(e.attr("href"));
        return id.isPresent() && !id.equals(AlertLinks.linkedInJobId(title.attr("href")));
    }
}
