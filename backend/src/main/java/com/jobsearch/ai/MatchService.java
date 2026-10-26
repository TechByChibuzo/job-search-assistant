package com.jobsearch.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import com.jobsearch.job.Job;
import com.jobsearch.job.JobDescriptions;
import com.jobsearch.job.JobLookup;
import com.jobsearch.job.JobMatches;

/** Scores a role against the resume with Claude, and stores the result on the role. */
@Service
public class MatchService {

    /** Postings longer than this are cut; the requirements come first in nearly every posting. */
    static final int MAX_DESCRIPTION = 15_000;

    private final Claude claude;
    private final ResumeStore resumes;
    private final JobLookup jobs;
    private final JobDescriptions descriptions;
    private final JobMatches matches;

    MatchService(Claude claude, ResumeStore resumes, JobLookup jobs, JobMatches matches, JobDescriptions descriptions) {
        this.claude = claude;
        this.resumes = resumes;
        this.jobs = jobs;
        this.matches = matches;
        this.descriptions = descriptions;
    }

    public Job score(long jobId) {
        descriptions.ensure(jobId);
        Job job = jobs.get(jobId);
        Resume resume = resumes.current().orElseThrow(() -> new AiException(resumes.describeFolder()));
        JsonNode answer = claude.ask(Prompts.MATCH, resume, posting(job), Prompts.MATCH_SCHEMA, Claude.Effort.LOW);
        return matches.record(jobId, answer.path("score").asInt(0), answer.path("summary").asText(""),
                strings(answer.path("strengths"), 3), strings(answer.path("gaps"), 3), claude.model());
    }

    static String posting(Job job) {
        StringBuilder s = new StringBuilder("<job_posting>\n");
        s.append("Title: ").append(job.getTitle()).append('\n');
        s.append("Company: ").append(job.getCompany().getName()).append('\n');
        if (job.getTeam() != null) {
            s.append("Team: ").append(job.getTeam()).append('\n');
        }
        if (job.getLocation() != null) {
            s.append("Location: ").append(job.getLocation()).append('\n');
        }
        String description = job.getDescription();
        if (description == null || description.isBlank()) {
            s.append("\n(The job board gave no description for this role.)\n");
        } else if (description.length() > MAX_DESCRIPTION) {
            s.append('\n').append(description, 0, MAX_DESCRIPTION).append("\n(Description shortened.)\n");
        } else {
            s.append('\n').append(description).append('\n');
        }
        return s.append("</job_posting>\n\nAssess the candidate's fit for this role.").toString();
    }

    private static List<String> strings(JsonNode array, int max) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : array) {
            String text = item.asText("").strip();
            if (!text.isEmpty() && out.size() < max) {
                out.add(text);
            }
        }
        return out;
    }
}
