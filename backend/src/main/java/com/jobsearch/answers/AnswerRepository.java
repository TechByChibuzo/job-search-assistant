package com.jobsearch.answers;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findAllByOrderByUsesDescUpdatedAtDesc();
}
