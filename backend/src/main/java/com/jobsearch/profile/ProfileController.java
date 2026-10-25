package com.jobsearch.profile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.ai.AiException;
import com.jobsearch.ai.Resume;
import com.jobsearch.ai.ResumeProfile;
import com.jobsearch.ai.ResumeStore;
import com.jobsearch.common.NotFoundException;

/** The applicant profile the extension fills forms from, and the resume it attaches. */
@RestController
@RequestMapping("/api/profile")
class ProfileController {

    private final ApplicantProfileRepository profiles;
    private final ResumeProfile fromResume;
    private final ResumeStore resumes;
    private final Clock clock;

    ProfileController(ApplicantProfileRepository profiles, ResumeProfile fromResume, ResumeStore resumes, Clock clock) {
        this.profiles = profiles;
        this.fromResume = fromResume;
        this.resumes = resumes;
        this.clock = clock;
    }

    /** @param resumeFileName the resume the extension will attach, so the popup can say which */
    record ProfileView(ProfileFields fields, Instant updatedAt, String resumeFileName) {}

    @GetMapping
    @Transactional(readOnly = true)
    ProfileView get() {
        var resume = resumes.current().map(Resume::fileName).orElse(null);
        return profiles.findById(ApplicantProfile.ID)
                .map(p -> new ProfileView(p.fields(), p.getUpdatedAt(), resume))
                .orElseGet(() -> new ProfileView(empty(), null, resume));
    }

    @PutMapping
    @Transactional
    ProfileView save(@Valid @RequestBody ProfileFields fields) {
        ApplicantProfile profile = profiles.findById(ApplicantProfile.ID).orElseGet(() -> new ApplicantProfile(clock.instant()));
        profile.update(fields.cleaned(), clock.instant());
        profiles.save(profile);
        return get();
    }

    /** Claude's reading of the resume, for the owner to review and save. Nothing is stored here. */
    @PostMapping("/from-resume")
    ProfileFields suggestFromResume() {
        var e = fromResume.extract();
        return new ProfileFields(e.firstName(), e.lastName(), e.email(), e.phone(), e.city(), e.state(), e.country(),
                e.linkedinUrl(), e.githubUrl(), e.websiteUrl(), e.currentCompany(), e.currentTitle(), e.school(), e.degree(),
                e.graduationYear());
    }

    /** The resume file, for the extension to attach to an application form. */
    @GetMapping("/resume")
    ResponseEntity<byte[]> resume() {
        Resume r = resumes.current().orElseThrow(() -> new NotFoundException("No resume in the private folder."));
        byte[] body = r.pdf() != null ? r.pdf() : r.text().getBytes(StandardCharsets.UTF_8);
        MediaType type = r.pdf() != null ? MediaType.APPLICATION_PDF : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(r.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @ExceptionHandler(AiException.class)
    ProblemDetail aiFailed(AiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private static ProfileFields empty() {
        return new ProfileFields(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
