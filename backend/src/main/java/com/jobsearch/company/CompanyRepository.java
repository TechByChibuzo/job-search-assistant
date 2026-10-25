package com.jobsearch.company;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByAtsAndBoard(AtsType ats, String board);

    List<Company> findAllByOrderByNameAsc();

    List<Company> findByActiveTrueOrderByNameAsc();
}
