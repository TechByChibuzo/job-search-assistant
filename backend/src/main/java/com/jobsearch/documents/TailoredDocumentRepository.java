package com.jobsearch.documents;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface TailoredDocumentRepository extends JpaRepository<TailoredDocument, Long> {

    List<TailoredDocument> findByJobIdOrderByCreatedAtDesc(Long jobId);

    @EntityGraph(attributePaths = {"job", "job.company"})
    Optional<TailoredDocument> findWithJobById(Long id);
}
