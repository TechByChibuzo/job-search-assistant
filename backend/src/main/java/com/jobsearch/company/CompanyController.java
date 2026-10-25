package com.jobsearch.company;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.company.CompanyDtos.CompanyResponse;
import com.jobsearch.company.CompanyDtos.CreateCompanyRequest;
import com.jobsearch.company.CompanyDtos.DetectRequest;
import com.jobsearch.company.CompanyDtos.DetectResponse;
import com.jobsearch.company.CompanyDtos.UpdateCompanyRequest;

@RestController
@RequestMapping("/api/companies")
class CompanyController {

    private final CompanyService service;
    private final OpenJobCounts openJobCounts;
    private final LogoService logos;

    CompanyController(CompanyService service, OpenJobCounts openJobCounts, LogoService logos) {
        this.service = service;
        this.openJobCounts = openJobCounts;
        this.logos = logos;
    }

    /** The company's logo, served by the app; 404 when none was found, and the interface shows initials. */
    @GetMapping("/{id}/logo")
    ResponseEntity<byte[]> logo(@PathVariable long id) {
        return logos.logo(id)
                .map(l -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(l.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                        .body(l.image()))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).build());
    }

    @GetMapping
    List<CompanyResponse> list() {
        Map<Long, Long> counts = openJobCounts.byCompanyId();
        return service.list().stream()
                .map(c -> CompanyResponse.of(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{id}")
    CompanyResponse get(@PathVariable long id) {
        return CompanyResponse.of(service.get(id), openJobCounts.byCompanyId().getOrDefault(id, 0L));
    }

    /** Previews what a careers link resolves to, before the owner saves it. */
    @PostMapping("/detect")
    DetectResponse detect(@Valid @RequestBody DetectRequest request) {
        return DetectResponse.of(service.detect(request.careersUrl()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CompanyResponse create(@Valid @RequestBody CreateCompanyRequest request) {
        return CompanyResponse.of(service.create(request), 0);
    }

    @PatchMapping("/{id}")
    CompanyResponse update(@PathVariable long id, @Valid @RequestBody UpdateCompanyRequest request) {
        Company company = service.update(id, request);
        if (request.domain() != null) {
            logos.forget(id);
        }
        return CompanyResponse.of(company, openJobCounts.byCompanyId().getOrDefault(id, 0L));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
