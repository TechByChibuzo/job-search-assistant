package com.jobsearch.job;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobsearch.common.BadRequestException;
import com.jobsearch.company.CompanyService;
import com.jobsearch.job.SyncRunner.SyncProgress;

/** Starts syncs in the background (202 Accepted) and reports their progress. */
@RestController
class SyncController {

    private final SyncRunner runner;
    private final CompanyService companies;

    SyncController(SyncRunner runner, CompanyService companies) {
        this.runner = runner;
        this.companies = companies;
    }

    @GetMapping("/api/sync")
    SyncProgress progress() {
        return runner.current();
    }

    @PostMapping("/api/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SyncProgress syncAll() {
        return runner.start(null);
    }

    @PostMapping("/api/companies/{id}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SyncProgress syncCompany(@PathVariable long id) {
        // 404 or 400 now, rather than a failed run later
        if (!companies.get(id).getAts().isSyncable()) {
            throw new BadRequestException("This company was added by hand; there is no feed to sync.");
        }
        return runner.start(id);
    }
}
