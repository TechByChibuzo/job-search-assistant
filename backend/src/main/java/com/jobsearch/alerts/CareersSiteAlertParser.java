package com.jobsearch.alerts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Alerts from a company's own careers site (Microsoft, Meta, Apple and others): any link that looks like one
 * job posting, with a role title as its text. The company comes from the sender, since these emails are
 * sent by the company itself. The fallback for every sender no other parser handles.
 */
@Component
@Order(100)
class CareersSiteAlertParser implements AlertParser {

    /** Sender domains whose company name isn't obvious from the display name. */
    private static final Map<String, String> COMPANIES = Map.of(
            "microsoft.com", "Microsoft",
            "metacareers.com", "Meta",
            "meta.com", "Meta",
            "fb.com", "Meta",
            "apple.com", "Apple");

    @Override
    public boolean handles(AlertEmail email) {
        return true;
    }

    @Override
    public List<AlertJob> parse(AlertEmail email) {
        if (email.html() == null) {
            return List.of();
        }
        String company = company(email);
        Map<String, AlertJob> jobs = new LinkedHashMap<>();
        for (Element a : Jsoup.parse(email.html()).select("a[href]")) {
            String href = a.attr("href");
            String title = a.text().strip();
            if (!AlertLinks.looksLikeJobPosting(href) || AlertLinks.isGenericText(title) || title.length() > 200) {
                continue;
            }
            String url = AlertLinks.canonical(href);
            jobs.putIfAbsent(url, new AlertJob(url, title, company, null));
        }
        return List.copyOf(jobs.values());
    }

    /** "careers@microsoft.com" → Microsoft; otherwise the display name without words like "Careers". */
    static String company(AlertEmail email) {
        String address = email.senderAddress();
        for (var entry : COMPANIES.entrySet()) {
            if (address.endsWith("@" + entry.getKey()) || address.endsWith("." + entry.getKey())) {
                return entry.getValue();
            }
        }
        String name = email.senderName()
                .replaceAll("(?i)\\b(careers?|jobs?|job alerts?|talent( acquisition)?|recruiting|recruitment|team|hr|no-?reply)\\b", "")
                .replaceAll("[|,:\\-–]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (!name.isEmpty()) {
            return name;
        }
        String domain = address.contains("@") ? address.substring(address.indexOf('@') + 1) : address;
        String[] parts = domain.split("\\.");
        String core = parts.length >= 2 ? parts[parts.length - 2] : domain;
        return core.isEmpty() ? "Unknown" : Character.toUpperCase(core.charAt(0)) + core.substring(1);
    }
}
