package com.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.config.AppProperties;

class GmailAuthTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final GmailAccountRepository accounts = mock(GmailAccountRepository.class);
    private final GmailApi api = mock(GmailApi.class);
    private MockRestServiceServer google;
    private GmailAuth auth;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
        var gmail = new AppProperties.Gmail("client-1", "secret-1", "http://localhost:8081/api/gmail/callback", "Job alerts");
        var properties = new AppProperties(null, null, null, null, null, gmail);
        auth = new GmailAuth(properties, accounts, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void asksForReadOnlyOfflineAccessWithAState() {
        var params = UriComponentsBuilder.fromUriString(auth.authorizationUrl()).build().getQueryParams();

        assertThat(params.getFirst("scope")).isEqualTo(GmailAuth.SCOPE);
        assertThat(params.getFirst("access_type")).isEqualTo("offline");
        assertThat(params.getFirst("client_id")).isEqualTo("client-1");
        assertThat(params.getFirst("state")).hasSizeGreaterThan(20);
    }

    @Test
    void refusesACallbackWithAStateItDidNotIssue() {
        assertThatThrownBy(() -> auth.complete("code", "forged", api)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void tradesTheCodeForTokensAndSavesTheAccount() {
        String state = UriComponentsBuilder.fromUriString(auth.authorizationUrl()).build().getQueryParams().getFirst("state");
        google.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("grant_type=authorization_code"),
                        Matchers.containsString("code=the-code"),
                        Matchers.containsString("client_secret=secret-1"))))
                .andRespond(withSuccess("{\"access_token\": \"at\", \"refresh_token\": \"rt\", \"expires_in\": 3599}",
                        MediaType.APPLICATION_JSON));
        when(api.profileEmail("at")).thenReturn("me@gmail.com");
        when(accounts.save(ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        GmailAccount account = auth.complete("the-code", state, api);

        assertThat(account.getEmail()).isEqualTo("me@gmail.com");
        assertThat(account.getRefreshToken()).isEqualTo("rt");
        assertThat(auth.accessToken()).isEqualTo("at");
        // A state works once
        assertThatThrownBy(() -> auth.complete("the-code", state, api)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refreshesTheAccessTokenWithTheStoredRefreshToken() {
        when(accounts.findById(GmailAccount.ID)).thenReturn(Optional.of(new GmailAccount("me@gmail.com", "rt", NOW.minus(Duration.ofDays(1)))));
        google.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(content().string(Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\": \"fresh\", \"expires_in\": 3599}", MediaType.APPLICATION_JSON));

        assertThat(auth.accessToken()).isEqualTo("fresh");
        // Cached until it nears expiry
        assertThat(auth.accessToken()).isEqualTo("fresh");
        google.verify();
    }
}
