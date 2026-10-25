package com.jobsearch.tracker;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface PursuitRepository extends JpaRepository<Pursuit, Long> {

    @EntityGraph(attributePaths = {"job", "job.company"})
    Optional<Pursuit> findByJobId(Long jobId);

    @EntityGraph(attributePaths = {"job", "job.company"})
    List<Pursuit> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"job", "job.company"})
    Optional<Pursuit> findWithJobById(Long id);
}
