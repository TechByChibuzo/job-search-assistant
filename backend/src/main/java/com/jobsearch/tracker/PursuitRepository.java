package com.jobsearch.tracker;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PursuitRepository extends JpaRepository<Pursuit, Long> {

    @EntityGraph(attributePaths = {"job", "job.company"})
    Optional<Pursuit> findByJobId(Long jobId);

    @EntityGraph(attributePaths = {"job", "job.company"})
    List<Pursuit> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"job", "job.company"})
    Optional<Pursuit> findWithJobById(Long id);

    @Query("select p.job.id from Pursuit p where p.stage in :stages")
    List<Long> findJobIdsByStageIn(Collection<PursuitStage> stages);

    @Query("select p.job.id as jobId, p.stage as stage from Pursuit p where p.job.id in :jobIds")
    List<JobStage> findStagesByJobIdIn(Collection<Long> jobIds);

    interface JobStage {
        Long getJobId();

        PursuitStage getStage();
    }
}
