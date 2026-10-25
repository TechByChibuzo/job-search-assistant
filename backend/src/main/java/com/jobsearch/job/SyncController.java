package com.jobsearch.job;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SyncController {

    private final JobSyncService sync;

    SyncController(JobSyncService sync) {
        this.sync = sync;
    }

    @PostMapping("/api/sync")
    List<SyncResult> syncAll() {
        return sync.syncAll();
    }

    @PostMapping("/api/companies/{id}/sync")
    SyncResult syncCompany(@PathVariable long id) {
        return sync.sync(id);
    }
}
