package com.jobsearch.alerts;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.config.AppProperties;
import com.jobsearch.job.SyncRunner;
import com.jobsearch.job.SyncRunner.SyncProgress;

/** Connecting Gmail, and reading job-alert emails on demand. */
@RestController
@RequestMapping("/api/gmail")
class GmailController {

    private final GmailAuth auth;
    private final GmailApi api;
    private final SyncRunner runner;
    private final AppProperties properties;

    GmailController(GmailAuth auth, GmailApi api, SyncRunner runner, AppProperties properties) {
        this.auth = auth;
        this.api = api;
        this.runner = runner;
        this.properties = properties;
    }

    record GmailStatus(
            boolean configured,
            boolean connected,
            String email,
            String label,
            Instant connectedAt,
            Instant lastImportAt,
            String lastImportError,
            int lastEmailsRead,
            int lastRolesAdded,
            int lastUnreadable) {}

    @GetMapping
    GmailStatus status() {
        var account = auth.account();
        String label = properties.gmail().label();
        return account
                .map(a -> new GmailStatus(auth.configured(), true, a.getEmail(), label, a.getConnectedAt(), a.getLastImportAt(),
                        a.getLastImportError(), a.getLastEmailsRead(), a.getLastRolesAdded(), a.getLastUnreadable()))
                .orElseGet(() -> new GmailStatus(auth.configured(), false, null, label, null, null, null, 0, 0, 0));
    }

    /** Sends the browser to Google to approve read-only access. */
    @GetMapping("/connect")
    ResponseEntity<Void> connect() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(auth.authorizationUrl())).build();
    }

    /** Google sends the browser back here; the app then returns it to the Companies page with the outcome. */
    @GetMapping("/callback")
    ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String outcome;
        String reason = null;
        if (error != null) {
            outcome = "declined";
        } else {
            try {
                auth.complete(code, state, api);
                outcome = "connected";
            } catch (BadRequestException e) {
                outcome = "error";
                reason = e.getMessage();
            } catch (RuntimeException e) {
                outcome = "error";
                reason = "Google sign-in failed. Check the client id, secret and redirect URI.";
            }
        }
        var back = UriComponentsBuilder.fromPath("/companies").queryParam("gmail", outcome);
        if (reason != null) {
            back.queryParam("reason", reason);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(back.encode().build().toUri()).build();
    }

    /** Reads new alert emails now, in the background like a sync. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SyncProgress importNow() {
        if (!auth.account().isPresent()) {
            throw new BadRequestException("Connect Gmail first.");
        }
        return runner.startFeed(AlertImporter.NAME);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect() {
        auth.disconnect();
    }
}
