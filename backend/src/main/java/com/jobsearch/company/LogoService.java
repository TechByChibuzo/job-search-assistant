package com.jobsearch.company;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * Company logos, fetched once by the app and stored, then served from the app itself: the browser never asks
 * a third party, so no one learns which companies the owner is looking at. The icon comes from Google's
 * favicon service, or DuckDuckGo's as a fallback; both are asked about a domain, never sent to it, so an
 * edited domain can't point the app at an internal address.
 */
@Service
public class LogoService {

    private static final Duration KEEP_FOUND = Duration.ofDays(30);
    private static final Duration RETRY_MISSING = Duration.ofDays(1);
    private static final int MAX_BYTES = 300_000;
    private static final Logger log = LoggerFactory.getLogger(LogoService.class);

    private final CompanyRepository companies;
    private final CompanyLogoRepository logos;
    private final CompanyLinks links;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RestClient http;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    LogoService(CompanyRepository companies, CompanyLogoRepository logos, CompanyLinks links, TransactionTemplate tx, Clock clock,
            RestClient.Builder builder) {
        this.companies = companies;
        this.logos = logos;
        this.links = links;
        this.tx = tx;
        this.clock = clock;
        this.http = builder.build();
    }

    public record Logo(String contentType, byte[] image) {}

    /** The company's logo, fetching it the first time; empty when none can be found. */
    public Optional<Logo> logo(long companyId) {
        // One fetch per company at a time: the inbox asks for the same logo on many rows at once
        synchronized (locks.computeIfAbsent(companyId, id -> new Object())) {
            Company company = companies.findById(companyId).orElse(null);
            if (company == null) {
                return Optional.empty();
            }
            Optional<CompanyLogo> cached = logos.findById(companyId).filter(l -> fresh(l, company));
            if (cached.isPresent()) {
                return cached.filter(CompanyLogo::isFound).map(l -> new Logo(l.getContentType(), l.getImage()));
            }
            return fetchAndStore(companyId, company);
        }
    }

    /** Forgets the stored logo, after the owner corrects the company's website. */
    public void forget(long companyId) {
        tx.executeWithoutResult(s -> logos.deleteById(companyId));
    }

    private boolean fresh(CompanyLogo logo, Company company) {
        if (company.getDomain() != null && !company.getDomain().equals(logo.getDomain())) {
            return false;
        }
        Duration age = Duration.between(logo.getFetchedAt(), clock.instant());
        return age.compareTo(logo.isFound() ? KEEP_FOUND : RETRY_MISSING) < 0;
    }

    private Optional<Logo> fetchAndStore(long companyId, Company company) {
        Instant now = clock.instant();
        Set<String> candidates = candidates(companyId, company);
        for (String domain : candidates) {
            Optional<Logo> logo = fetch(domain);
            if (logo.isPresent()) {
                tx.executeWithoutResult(s -> {
                    companies.findById(companyId).ifPresent(c -> {
                        if (c.getDomain() == null) {
                            c.setDomain(domain);
                        }
                    });
                    logos.save(new CompanyLogo(companyId, domain, true, logo.get().contentType(), logo.get().image(), now));
                });
                return logo;
            }
        }
        String tried = candidates.stream().findFirst().orElse("");
        tx.executeWithoutResult(s -> logos.save(new CompanyLogo(companyId, tried, false, null, null, now)));
        return Optional.empty();
    }

    /** The owner's domain if set; otherwise well-known, then from job links, then a guess from the name. */
    private Set<String> candidates(long companyId, Company company) {
        Set<String> domains = new LinkedHashSet<>();
        if (company.getDomain() != null) {
            domains.add(company.getDomain());
            return domains;
        }
        CompanyDomains.known(company.getName()).ifPresent(domains::add);
        links.sample(companyId).forEach(link -> CompanyDomains.fromLink(link).ifPresent(domains::add));
        CompanyDomains.guess(company.getName()).ifPresent(domains::add);
        return domains;
    }

    private Optional<Logo> fetch(String domain) {
        return get("https://www.google.com/s2/favicons?domain={d}&sz=128", domain)
                .or(() -> get("https://icons.duckduckgo.com/ip3/{d}.ico", domain));
    }

    private Optional<Logo> get(String url, String domain) {
        try {
            ResponseEntity<byte[]> response = http.get().uri(url, domain).retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> { })
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            boolean image = type != null && type.getType().equals("image");
            if (!response.getStatusCode().is2xxSuccessful() || !image || body == null || body.length < 100 || body.length > MAX_BYTES) {
                return Optional.empty();
            }
            return Optional.of(new Logo(type.toString(), body));
        } catch (RuntimeException e) {
            log.debug("No logo for {} from {}: {}", domain, url, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
