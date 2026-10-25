package com.jobsearch.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class LogoServiceTest {

    private static final byte[] PNG = new byte[512];
    private static final String GOOGLE = "https://www.google.com/s2/favicons?domain=%s&sz=128";
    private static final String DDG = "https://icons.duckduckgo.com/ip3/%s.ico";

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyLogoRepository logos = mock(CompanyLogoRepository.class);
    private final CompanyLinks links = mock(CompanyLinks.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private MockRestServiceServer server;
    private LogoService service;
    private Company acme;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new LogoService(companies, logos, links, tx, Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC), builder);
        doAnswer(i -> {
            ((Consumer<TransactionStatus>) i.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        acme = new Company("Airbnb", new DetectedBoard(AtsType.GREENHOUSE, "airbnb"), null);
        when(companies.findById(1L)).thenReturn(Optional.of(acme));
        when(logos.findById(1L)).thenReturn(Optional.empty());
    }

    @Test
    void findsTheSiteFromJobLinksFetchesTheLogoAndRemembersBoth() {
        when(links.sample(1L)).thenReturn(List.of("https://careers.airbnb.com/positions/1"));
        server.expect(requestTo(GOOGLE.formatted("airbnb.com"))).andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        var logo = service.logo(1L);

        assertThat(logo).isPresent();
        assertThat(logo.get().contentType()).isEqualTo("image/png");
        assertThat(acme.getDomain()).isEqualTo("airbnb.com");
        ArgumentCaptor<CompanyLogo> saved = ArgumentCaptor.forClass(CompanyLogo.class);
        org.mockito.Mockito.verify(logos).save(saved.capture());
        assertThat(saved.getValue().isFound()).isTrue();
    }

    @Test
    void fallsBackToDuckDuckGoThenRecordsThatNoneWasFound() {
        when(links.sample(1L)).thenReturn(List.of());
        server.expect(requestTo(GOOGLE.formatted("airbnb.com"))).andRespond(withResourceNotFound());
        server.expect(requestTo(DDG.formatted("airbnb.com"))).andRespond(withResourceNotFound());

        assertThat(service.logo(1L)).isEmpty();
        ArgumentCaptor<CompanyLogo> saved = ArgumentCaptor.forClass(CompanyLogo.class);
        org.mockito.Mockito.verify(logos).save(saved.capture());
        assertThat(saved.getValue().isFound()).isFalse();
    }

    @Test
    void usesTheStoredLogoWithoutFetchingAgain() {
        acme.setDomain("airbnb.com");
        when(logos.findById(1L)).thenReturn(Optional.of(
                new CompanyLogo(1L, "airbnb.com", true, "image/png", PNG, Instant.parse("2026-10-20T00:00:00Z"))));
        server.expect(never(), requestTo(GOOGLE.formatted("airbnb.com")));

        assertThat(service.logo(1L)).isPresent();
        server.verify();
    }

    @Test
    void anHtmlAnswerIsNotALogo() {
        acme.setDomain("airbnb.com");
        server.expect(requestTo(GOOGLE.formatted("airbnb.com"))).andRespond(withSuccess("<html>moved</html>", MediaType.TEXT_HTML));
        server.expect(requestTo(DDG.formatted("airbnb.com"))).andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

        assertThat(service.logo(1L)).isEmpty();
    }
}
