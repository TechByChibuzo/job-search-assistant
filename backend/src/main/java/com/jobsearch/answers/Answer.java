package com.jobsearch.answers;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** The owner's answer to an application question, reused across forms. */
@Entity
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String question;

    private String answer;

    /** How many times the extension has filled it in. */
    private int uses;

    private Instant createdAt;

    private Instant updatedAt;

    protected Answer() {}

    Answer(String question, String answer, Instant now) {
        this.question = question;
        this.answer = answer;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void edit(String question, String answer, Instant now) {
        if (question != null) {
            this.question = question;
        }
        if (answer != null) {
            this.answer = answer;
        }
        this.updatedAt = now;
    }

    void used() {
        uses++;
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public int getUses() {
        return uses;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
