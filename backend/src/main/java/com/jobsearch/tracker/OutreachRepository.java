package com.jobsearch.tracker;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OutreachRepository extends JpaRepository<Outreach, Long> {

    @EntityGraph(attributePaths = {"contact", "contact.company", "job", "job.company"})
    Optional<Outreach> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"contact", "contact.company", "job"})
    List<Outreach> findByJobIdOrderByFirstSentAtAsc(Long jobId);

    @EntityGraph(attributePaths = {"contact", "job"})
    List<Outreach> findByJobIdIn(Collection<Long> jobIds);

    @EntityGraph(attributePaths = {"contact", "contact.company", "job", "job.company"})
    List<Outreach> findByContactIdOrderByFirstSentAtDesc(Long contactId);

    /** Conversations waiting on a reply whose follow-up date has come. */
    @EntityGraph(attributePaths = {"contact", "contact.company", "job", "job.company"})
    @Query("select o from Outreach o where o.status = com.jobsearch.tracker.OutreachStatus.AWAITING_REPLY "
            + "and o.followUpOn <= :today order by o.followUpOn, o.id")
    List<Outreach> findDue(LocalDate today);

    @EntityGraph(attributePaths = "contact")
    List<Outreach> findAllByOrderByLastContactedAtDesc();
}
