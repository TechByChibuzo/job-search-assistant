package com.jobsearch.contact;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @EntityGraph(attributePaths = "company")
    List<Contact> findAllByOrderByCompanyNameAscNameAsc();

    @EntityGraph(attributePaths = "company")
    List<Contact> findByCompanyIdOrderByNameAsc(Long companyId);

    @EntityGraph(attributePaths = "company")
    Optional<Contact> findWithCompanyById(Long id);
}
