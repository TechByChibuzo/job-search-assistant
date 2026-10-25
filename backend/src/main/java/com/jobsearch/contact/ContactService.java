package com.jobsearch.contact;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.NotFoundException;
import com.jobsearch.company.CompanyService;

@Service
public class ContactService {

    private final ContactRepository contacts;
    private final CompanyService companies;

    ContactService(ContactRepository contacts, CompanyService companies) {
        this.contacts = contacts;
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public List<Contact> list(Long companyId) {
        return companyId == null ? contacts.findAllByOrderByCompanyNameAscNameAsc() : contacts.findByCompanyIdOrderByNameAsc(companyId);
    }

    @Transactional(readOnly = true)
    public Contact get(long id) {
        return contacts.findWithCompanyById(id).orElseThrow(() -> new NotFoundException("No contact with id " + id));
    }

    /** At a tracked company by id, or by name; a new name creates a company added by hand. */
    @Transactional
    public Contact create(Long companyId, String companyName, String name, String title, ContactKind kind,
            String linkedinUrl, String email, String notes) {
        var company = companyId != null ? companies.get(companyId)
                : companyName != null && !companyName.isBlank() ? companies.findOrCreateManual(companyName)
                : null;
        if (company == null) {
            throw new BadRequestException("Say which company this person works at.");
        }
        Contact contact = new Contact(company, name.strip(), kind == null ? ContactKind.OTHER : kind);
        contact.update(null, clean(title), null, clean(linkedinUrl), clean(email), clean(notes));
        return contacts.save(contact);
    }

    @Transactional
    public Contact update(long id, String name, String title, ContactKind kind, String linkedinUrl, String email, String notes) {
        Contact contact = get(id);
        contact.update(name == null || name.isBlank() ? null : name.strip(), clean(title), kind, clean(linkedinUrl),
                clean(email), clean(notes));
        return contact;
    }

    @Transactional
    public void delete(long id) {
        contacts.delete(get(id));
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
