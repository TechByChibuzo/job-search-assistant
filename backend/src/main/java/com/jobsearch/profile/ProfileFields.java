package com.jobsearch.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.jobsearch.common.Links;

/** The fields of the applicant profile, as the API and the extension see them. */
public record ProfileFields(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email @Size(max = 200) String email,
        @Size(max = 50) String phone,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 300) String linkedinUrl,
        @Size(max = 300) String githubUrl,
        @Size(max = 300) String websiteUrl,
        @Size(max = 200) String currentCompany,
        @Size(max = 200) String currentTitle,
        @Size(max = 200) String school,
        @Size(max = 200) String degree,
        @Size(max = 10) String graduationYear) {

    /** Blank fields become null, and links must be web links: they end up as anchors and in forms. */
    ProfileFields cleaned() {
        return new ProfileFields(c(firstName), c(lastName), c(email), c(phone), c(city), c(state), c(country),
                link(linkedinUrl), link(githubUrl), link(websiteUrl), c(currentCompany), c(currentTitle), c(school), c(degree),
                c(graduationYear));
    }

    private static String c(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String link(String s) {
        String v = c(s);
        if (v == null) {
            return null;
        }
        String withScheme = v.matches("(?i)^https?://.*") ? v : "https://" + v;
        return Links.requireHttp(withScheme, "'" + v + "' isn't a web link");
    }
}
