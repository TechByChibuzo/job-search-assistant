package com.jobsearch.alerts;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.config.AppProperties;

/**
 * Google sign-in for Gmail, read-only. The owner approves once; the app keeps the refresh token and trades
 * it for an access token, valid for an hour, whenever it reads the mailbox.
 */
@Component
class GmailAuth {

    static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(GmailAuth.class);

    private final AppProperties.Gmail config;
    private final GmailAccountRepository accounts;
    private final RestClient google;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Sign-ins in progress, by their state value, so a callback can't be forged (CSRF). */
    private final Map<String, Instant> pendingStates = new ConcurrentHashMap<>();

    private volatile String accessToken;
    private volatile Instant accessTokenExpires = Instant.EPOCH;

    GmailAuth(AppProperties properties, GmailAccountRepository accounts, RestClient.Builder builder, Clock clock) {
        this.config = properties.gmail();
        this.accounts = accounts;
        this.google = builder.build();
        this.clock = clock;
    }

    boolean configured() {
        return config.configured();
    }

    Optional<GmailAccount> account() {
        return accounts.findById(GmailAccount.ID);
    }

    /** Where to send the owner to approve read-only access. */
    String authorizationUrl() {
        if (!configured()) {
            throw new BadRequestException("Gmail isn't set up: add a Google OAuth client id and secret (docs/gmail-setup.md).");
        }
        Instant now = clock.instant();
        pendingStates.values().removeIf(created -> created.plus(STATE_LIFETIME).isBefore(now));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingStates.put(state, now);
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    /** Finishes sign-in: checks the state, trades the code for tokens, and saves the account. */
    @Transactional
    GmailAccount complete(String code, String state, GmailApi api) {
        Instant created = state == null ? null : pendingStates.remove(state);
        if (created == null || created.plus(STATE_LIFETIME).isBefore(clock.instant())) {
            throw new BadRequestException("That sign-in link has expired. Start again from the app.");
        }
        Tokens tokens = token(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", config.redirectUri()));
        if (tokens.refreshToken() == null) {
            throw new BadRequestException("Google didn't return long-term access. Remove the app's access in your Google account and connect again.");
        }
        remember(tokens);
        String email = api.profileEmail(tokens.accessToken());
        accounts.deleteAll();
        return accounts.save(new GmailAccount(email, tokens.refreshToken(), clock.instant()));
    }

    /** A valid access token, refreshed when it is about to expire. */
    String accessToken() {
        if (accessToken != null && clock.instant().isBefore(accessTokenExpires.minusSeconds(60))) {
            return accessToken;
        }
        GmailAccount account = account().orElseThrow(() -> new IllegalStateException("Gmail isn't connected."));
        try {
            remember(token(Map.of("grant_type", "refresh_token", "refresh_token", account.getRefreshToken())));
        } catch (RestClientResponseException e) {
            if (e.getResponseBodyAsString().contains("invalid_grant")) {
                throw new IllegalStateException("Gmail access was revoked or has expired. Connect Gmail again.");
            }
            throw e;
        }
        return accessToken;
    }

    /** Revokes the app's access at Google (best effort) and forgets the account. */
    @Transactional
    void disconnect() {
        account().ifPresent(a -> {
            // The token goes in the form body, never the URL, so it can't end up in logs or error messages
            var form = new LinkedMultiValueMap<String, String>();
            form.add("token", a.getRefreshToken());
            try {
                google.post()
                        .uri("https://oauth2.googleapis.com/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                log.warn("Google refused to revoke the Gmail token (HTTP {}); forgetting it anyway", e.getStatusCode().value());
            } catch (RuntimeException e) {
                log.warn("Couldn't reach Google to revoke the Gmail token ({}); forgetting it anyway", e.getClass().getSimpleName());
            }
            accounts.deleteAll();
        });
        accessToken = null;
        accessTokenExpires = Instant.EPOCH;
    }

    private Tokens token(Map<String, String> grant) {
        var form = new LinkedMultiValueMap<String, String>();
        grant.forEach(form::add);
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        return google.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Tokens.class);
    }

    private void remember(Tokens tokens) {
        accessToken = tokens.accessToken();
        accessTokenExpires = clock.instant().plusSeconds(tokens.expiresIn() == null ? 3000 : tokens.expiresIn());
    }

    record Tokens(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn) {}
}
