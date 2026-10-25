package com.jobsearch.documents;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.ai.AiException;
import com.jobsearch.ai.Claude;
import com.jobsearch.ai.TailorService;
import com.jobsearch.ai.TailorService.Kind;
import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.NotFoundException;
import com.jobsearch.job.Job;
import com.jobsearch.job.JobLookup;

/** Tailored resumes and cover letters, per role. */
@RestController
class DocumentController {

    private final TailoredDocumentRepository documents;
    private final TailorService tailor;
    private final JobLookup jobs;
    private final Claude claude;
    private final ObjectMapper json;
    private final Clock clock;

    DocumentController(TailoredDocumentRepository documents, TailorService tailor, JobLookup jobs, Claude claude, ObjectMapper json,
            Clock clock) {
        this.documents = documents;
        this.tailor = tailor;
        this.jobs = jobs;
        this.claude = claude;
        this.json = json;
        this.clock = clock;
    }

    record DocumentView(Long id, Long jobId, String jobTitle, String companyName, Kind kind, JsonNode content, String model,
            Instant createdAt) {}

    record GenerateRequest(@NotNull Kind kind) {}

    /** The owner's edits: the whole document back, as the interface shows it. */
    record EditRequest(@NotNull JsonNode content) {}

    @GetMapping("/api/jobs/{jobId}/documents")
    @Transactional(readOnly = true)
    List<DocumentView> forJob(@PathVariable long jobId) {
        Job job = jobs.get(jobId);
        return documents.findByJobIdOrderByCreatedAtDesc(jobId).stream().map(d -> view(d, job)).toList();
    }

    /**
     * Generates a new one with Claude (one request, often 20 to 40 seconds) and keeps it. Not one
     * transaction: no database connection is held while Claude writes.
     */
    @PostMapping("/api/jobs/{jobId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentView generate(@PathVariable long jobId, @Valid @RequestBody GenerateRequest r) {
        Job job = jobs.get(jobId);
        JsonNode content = tailor.generate(jobId, r.kind());
        TailoredDocument doc = documents.save(new TailoredDocument(job, r.kind(), write(content), claude.model(), clock.instant()));
        return view(doc, job);
    }

    @GetMapping("/api/documents/{id}")
    @Transactional(readOnly = true)
    DocumentView get(@PathVariable long id) {
        TailoredDocument doc = find(id);
        return view(doc, doc.getJob());
    }

    @PatchMapping("/api/documents/{id}")
    @Transactional
    DocumentView edit(@PathVariable long id, @Valid @RequestBody EditRequest r) {
        if (!r.content().isObject()) {
            throw new BadRequestException("A document is a JSON object.");
        }
        TailoredDocument doc = find(id);
        doc.edit(write(r.content()));
        return view(doc, doc.getJob());
    }

    @DeleteMapping("/api/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable long id) {
        documents.delete(find(id));
    }

    @ExceptionHandler(AiException.class)
    ProblemDetail aiFailed(AiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private TailoredDocument find(long id) {
        return documents.findWithJobById(id).orElseThrow(() -> new NotFoundException("No document with id " + id));
    }

    private DocumentView view(TailoredDocument d, Job job) {
        try {
            return new DocumentView(d.getId(), job.getId(), job.getTitle(), job.getCompany().getName(), d.getKind(),
                    json.readTree(d.getContent()), d.getModel(), d.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored document " + d.getId() + " isn't valid JSON", e);
        }
    }

    private String write(JsonNode content) {
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
