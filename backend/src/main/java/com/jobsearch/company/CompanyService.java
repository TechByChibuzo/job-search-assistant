package com.jobsearch.company;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.common.ConflictException;
import com.jobsearch.common.NotFoundException;
import com.jobsearch.company.CompanyDtos.CreateCompanyRequest;
import com.jobsearch.company.CompanyDtos.UpdateCompanyRequest;

@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final AtsDetector detector;

    public CompanyService(CompanyRepository companies, AtsDetector detector) {
        this.companies = companies;
        this.detector = detector;
    }

    @Transactional(readOnly = true)
    public List<Company> list() {
        return companies.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Company> listActive() {
        return companies.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Company get(long id) {
        return companies.findById(id).orElseThrow(() -> new NotFoundException("No company with id " + id));
    }

    public DetectedBoard detect(String careersUrl) {
        return detector.detect(careersUrl).orElseThrow(() -> new BadRequestException(
                "Couldn't recognise a Greenhouse, Lever or Ashby board in that link. "
                        + "Pick the ATS and board by hand instead."));
    }

    @Transactional
    public Company create(CreateCompanyRequest request) {
        DetectedBoard board = request.ats() != null && notBlank(request.board())
                ? new DetectedBoard(request.ats(), request.board().strip().toLowerCase(Locale.ROOT))
                : detect(request.careersUrl());
        if (companies.existsByAtsAndBoard(board.ats(), board.board())) {
            throw new ConflictException("Already tracking the " + board.ats() + " board '" + board.board() + "'");
        }
        String name = notBlank(request.name()) ? request.name().strip() : nameFromBoard(board.board());
        return companies.save(new Company(name, board, blankToNull(request.careersUrl())));
    }

    @Transactional
    public Company update(long id, UpdateCompanyRequest request) {
        Company company = get(id);
        if (notBlank(request.name())) {
            company.rename(request.name().strip());
        }
        if (request.active() != null) {
            company.setActive(request.active());
        }
        if (notBlank(request.careersUrl())) {
            DetectedBoard board = detect(request.careersUrl());
            boolean moved = board.ats() != company.getAts() || !board.board().equals(company.getBoard());
            if (moved && companies.existsByAtsAndBoard(board.ats(), board.board())) {
                throw new ConflictException("Another company already uses the " + board.ats() + " board '"
                        + board.board() + "'");
            }
            company.moveTo(board, request.careersUrl().strip());
        }
        return company;
    }

    @Transactional
    public void delete(long id) {
        companies.delete(get(id));
    }

    /** "palantir" → "Palantir", "scale-ai" → "Scale Ai": a starting point the owner can rename. */
    static String nameFromBoard(String board) {
        StringBuilder name = new StringBuilder();
        for (String word : board.split("[-_.]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.strip() : null;
    }
}
