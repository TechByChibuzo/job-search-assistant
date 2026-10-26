package com.jobsearch.answers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.ai.AiException;
import com.jobsearch.ai.AnswerDrafter;
import com.jobsearch.common.NotFoundException;

/** The answer bank: saved answers, matching a form's questions to them, and drafting new ones. */
@RestController
@RequestMapping("/api/answers")
class AnswerController {

    private final AnswerRepository answers;
    private final AnswerDrafter drafter;
    private final Clock clock;

    AnswerController(AnswerRepository answers, AnswerDrafter drafter, Clock clock) {
        this.answers = answers;
        this.drafter = drafter;
        this.clock = clock;
    }

    record AnswerView(Long id, String question, String answer, int uses, Instant updatedAt) {

        static AnswerView of(Answer a) {
            return new AnswerView(a.getId(), a.getQuestion(), a.getAnswer(), a.getUses(), a.getUpdatedAt());
        }
    }

    record SaveRequest(@NotBlank @Size(max = 1000) String question, @NotBlank @Size(max = 10000) String answer) {}

    record EditRequest(@Size(max = 1000) String question, @Size(max = 10000) String answer) {}

    /** {@code pageUrl} and {@code pageTitle} come from the extension, on the application form being filled. */
    record DraftRequest(@NotBlank @Size(max = 1000) String question, Long jobId, @Size(max = 2000) String pageUrl,
            @Size(max = 300) String pageTitle) {}

    record Draft(String answer) {}

    record MatchRequest(@Size(max = 50) List<@Size(max = 1000) String> questions) {}

    /** For each question on a form, the saved answer that fits it best, if any is close enough. */
    record Match(String question, Long answerId, String savedQuestion, String answer, double score) {}

    @GetMapping
    @Transactional(readOnly = true)
    List<AnswerView> list(@RequestParam(required = false) String q) {
        String needle = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return answers.findAllByOrderByUsesDescUpdatedAtDesc().stream()
                .filter(a -> needle.isEmpty() || (a.getQuestion() + " " + a.getAnswer()).toLowerCase(Locale.ROOT).contains(needle))
                .map(AnswerView::of)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AnswerView save(@Valid @RequestBody SaveRequest r) {
        return AnswerView.of(answers.save(new Answer(r.question().strip(), r.answer().strip(), clock.instant())));
    }

    @PatchMapping("/{id}")
    @Transactional
    AnswerView edit(@PathVariable long id, @Valid @RequestBody EditRequest r) {
        Answer a = get(id);
        a.edit(blankToNull(r.question()), blankToNull(r.answer()), clock.instant());
        return AnswerView.of(a);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable long id) {
        answers.delete(get(id));
    }

    /** The extension filled this answer into a form. */
    @PostMapping("/{id}/used")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void used(@PathVariable long id) {
        get(id).used();
    }

    @PostMapping("/match")
    @Transactional(readOnly = true)
    List<Match> match(@Valid @RequestBody MatchRequest r) {
        List<Answer> saved = answers.findAll();
        List<Match> matches = new ArrayList<>();
        for (String question : r.questions() == null ? List.<String>of() : r.questions()) {
            saved.stream()
                    .map(a -> new Match(question, a.getId(), a.getQuestion(), a.getAnswer(),
                            QuestionMatcher.similarity(question, a.getQuestion())))
                    .filter(m -> m.score() >= QuestionMatcher.THRESHOLD)
                    .max(Comparator.comparingDouble(Match::score))
                    .ifPresent(matches::add);
        }
        return matches;
    }

    /** Claude's draft, for the owner to edit and save. Nothing is stored here. */
    @PostMapping("/draft")
    @Transactional(readOnly = true)
    Draft draft(@Valid @RequestBody DraftRequest r) {
        List<String> examples = answers.findAllByOrderByUsesDescUpdatedAtDesc().stream().map(Answer::getAnswer).limit(3).toList();
        return new Draft(drafter.draft(r.question(), r.jobId(), r.pageUrl(), r.pageTitle(), examples));
    }

    @ExceptionHandler(AiException.class)
    ProblemDetail aiFailed(AiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private Answer get(long id) {
        return answers.findById(id).orElseThrow(() -> new NotFoundException("No answer with id " + id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
