package com.jobsearch.profile;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * The owner's details for application forms. One row. Work authorization, sponsorship and EEO answers are
 * not stored: the owner answers those on every form themselves.
 */
@Entity
public class ApplicantProfile {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String city;
    private String state;
    private String country;
    private String linkedinUrl;
    private String githubUrl;
    private String websiteUrl;
    private String currentCompany;
    private String currentTitle;
    private String school;
    private String degree;
    private String graduationYear;
    /** An earlier degree, such as the bachelor's before a master's. */
    private String previousSchool;
    private String previousDegree;
    private Instant updatedAt;

    protected ApplicantProfile() {}

    ApplicantProfile(Instant now) {
        this.updatedAt = now;
    }

    void update(ProfileFields f, Instant now) {
        firstName = f.firstName();
        lastName = f.lastName();
        email = f.email();
        phone = f.phone();
        city = f.city();
        state = f.state();
        country = f.country();
        linkedinUrl = f.linkedinUrl();
        githubUrl = f.githubUrl();
        websiteUrl = f.websiteUrl();
        currentCompany = f.currentCompany();
        currentTitle = f.currentTitle();
        school = f.school();
        degree = f.degree();
        graduationYear = f.graduationYear();
        previousSchool = f.previousSchool();
        previousDegree = f.previousDegree();
        updatedAt = now;
    }

    ProfileFields fields() {
        return new ProfileFields(firstName, lastName, email, phone, city, state, country, linkedinUrl, githubUrl, websiteUrl,
                currentCompany, currentTitle, school, degree, graduationYear, previousSchool, previousDegree);
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
