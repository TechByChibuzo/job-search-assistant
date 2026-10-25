package com.jobsearch.company;

import java.util.List;

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

    /** Companies the daily sync reads: active, and not added by hand. */
    @Transactional(readOnly = true)
    public List<Company> listActive() {
        return companies.findByActiveTrueAndAtsNotOrderByNameAsc(AtsType.MANUAL);
    }

    /**
     * The company a hand-added role belongs to: an existing one with that name, whatever its source, or a
     * new company marked as added by hand.
     */
    @Transactional
    public Company findOrCreateManual(String name) {
        String clean = name.strip();
        return companies.findFirstByNameIgnoreCase(clean).orElseGet(() -> {
            DetectedBoard board = detector.normalize(AtsType.MANUAL, clean)
                    .orElseThrow(() -> new BadRequestException("'" + clean + "' isn't a usable company name"));
            return companies.save(new Company(clean, board, null));
        });
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
        DetectedBoard board = request.ats() != null
                ? detector.normalize(request.ats(), request.board()).orElseThrow(() -> new BadRequestException(
                        "'" + request.board() + "' isn't a valid " + request.ats() + " board"))
                : detect(request.careersUrl());
        if (companies.existsByAtsAndBoard(board.ats(), board.board())) {
            throw new ConflictException("Already tracking the " + board.ats() + " board '" + board.board() + "'");
        }
        String name = notBlank(request.name()) ? request.name().strip()
                : board.ats().company() != null ? board.ats().company()
                : nameFromBoard(board.board());
        Company company = new Company(name, board, blankToNull(request.careersUrl()));
        company.setSearch(request.search());
        return companies.save(company);
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
        if (request.search() != null) {
            company.setSearch(request.search());
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

    /**
     * "palantir" → "Palantir", "scale-ai" → "Scale Ai", "nvidia.wd5/NVIDIAExternalCareerSite" → "Nvidia":
     * a starting point the owner can rename.
     */
    static String nameFromBoard(String board) {
        String company = board.contains("/") ? board.substring(0, board.indexOf('/')).replaceFirst("\\.wd\\d+$", "") : board;
        StringBuilder name = new StringBuilder();
        for (String word : company.split("[-_.]+")) {
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
