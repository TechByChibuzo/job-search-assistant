package com.jobsearch.contact;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contacts")
class ContactController {

    private final ContactService service;
    private final ContactActivity activity;

    ContactController(ContactService service, ContactActivity activity) {
        this.service = service;
        this.activity = activity;
    }

    /** The company is either a tracked company's id or a name; a new name is added as a company by hand. */
    record CreateContactRequest(
            Long companyId,
            @Size(max = 200) String companyName,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 300) String title,
            ContactKind kind,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 300) String email,
            @Size(max = 5000) String notes) {}

    /** Replaces the optional fields; a blank name or a missing kind leaves those unchanged. */
    record UpdateContactRequest(
            @Size(max = 200) String name,
            @Size(max = 300) String title,
            ContactKind kind,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 300) String email,
            @Size(max = 5000) String notes) {}

    record ContactResponse(
            Long id,
            Long companyId,
            String companyName,
            String name,
            String title,
            ContactKind kind,
            String linkedinUrl,
            String email,
            String notes,
            String lastStatus,
            Instant lastContactedAt,
            int conversations) {

        static ContactResponse of(Contact c, ContactActivity.Latest latest) {
            return new ContactResponse(c.getId(), c.getCompany().getId(), c.getCompany().getName(), c.getName(),
                    c.getTitle(), c.getKind(), c.getLinkedinUrl(), c.getEmail(), c.getNotes(),
                    latest == null ? null : latest.status(),
                    latest == null ? null : latest.lastContactedAt(),
                    latest == null ? 0 : latest.conversations());
        }
    }

    @GetMapping
    List<ContactResponse> list(@RequestParam(required = false) Long companyId) {
        Map<Long, ContactActivity.Latest> latest = activity.byContactId();
        return service.list(companyId).stream().map(c -> ContactResponse.of(c, latest.get(c.getId()))).toList();
    }

    @GetMapping("/{id}")
    ContactResponse get(@PathVariable long id) {
        return ContactResponse.of(service.get(id), activity.byContactId().get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContactResponse create(@Valid @RequestBody CreateContactRequest r) {
        Contact c = service.create(r.companyId(), r.companyName(), r.name(), r.title(), r.kind(), r.linkedinUrl(),
                r.email(), r.notes());
        return ContactResponse.of(service.get(c.getId()), null);
    }

    @PatchMapping("/{id}")
    ContactResponse update(@PathVariable long id, @Valid @RequestBody UpdateContactRequest r) {
        service.update(id, r.name(), r.title(), r.kind(), r.linkedinUrl(), r.email(), r.notes());
        return get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
