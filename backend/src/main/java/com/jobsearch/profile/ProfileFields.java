package com.jobsearch.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.jobsearch.common.Links;

/**
 * The fields of the applicant profile, as the API and the extension see them. The three {@code eeo} answers are
 * the owner's own voluntary self-identification, saved by their choice; empty leaves the question to them.
 */
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
        @Size(max = 10) String graduationYear,
        @Size(max = 200) String previousSchool,
        @Size(max = 200) String previousDegree,
        @Pattern(regexp = "Male|Female|Non-binary|Decline") String eeoGender,
        @Pattern(regexp = "Yes|No|Decline") String eeoHispanicLatino,
        @Pattern(regexp = "Not a protected veteran|Protected veteran|Decline") String eeoVeteran) {

    /** Blank fields become null, and links must be web links: they end up as anchors and in forms. */
    ProfileFields cleaned() {
        return new ProfileFields(c(firstName), c(lastName), c(email), c(phone), c(city), c(state), c(country),
                link(linkedinUrl), link(githubUrl), link(websiteUrl), c(currentCompany), c(currentTitle), c(school), c(degree),
                c(graduationYear), c(previousSchool), c(previousDegree), c(eeoGender), c(eeoHispanicLatino), c(eeoVeteran));
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
