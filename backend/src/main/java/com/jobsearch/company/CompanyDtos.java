package com.jobsearch.company;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class CompanyDtos {

    private CompanyDtos() {}

    /** Either a careers link to detect from, or an explicit ATS and board. */
    record CreateCompanyRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String careersUrl,
            AtsType ats,
            @Size(max = 200) String board) {}

    /** Fields left null are unchanged. A new careers link re-detects the ATS and board. */
    record UpdateCompanyRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String careersUrl,
            Boolean active) {}

    record DetectRequest(@NotBlank @Size(max = 2000) String careersUrl) {}

    record CompanyResponse(
            Long id,
            String name,
            AtsType ats,
            String board,
            String careersUrl,
            boolean active,
            long openJobs,
            Instant lastSyncedAt,
            SyncStatus lastSyncStatus,
            String lastSyncError) {

        static CompanyResponse of(Company c, long openJobs) {
            return new CompanyResponse(
                    c.getId(),
                    c.getName(),
                    c.getAts(),
                    c.getBoard(),
                    c.getCareersUrl(),
                    c.isActive(),
                    openJobs,
                    c.getLastSyncedAt(),
                    c.getLastSyncStatus(),
                    c.getLastSyncError());
        }
    }
}
