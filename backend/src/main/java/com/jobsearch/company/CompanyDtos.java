package com.jobsearch.company;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class CompanyDtos {

    private CompanyDtos() {}

    /**
     * Either a careers link to detect from, or an explicit ATS and board. {@code search} is optional
     * comma-separated phrases; large employers like Amazon need them.
     */
    record CreateCompanyRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String careersUrl,
            AtsType ats,
            @Size(max = 200) String board,
            @Size(max = 500) String search) {}

    /** Fields left null are unchanged. A new careers link re-detects the ATS and board. */
    record UpdateCompanyRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String careersUrl,
            Boolean active,
            @Size(max = 500) String search) {}

    record DetectRequest(@NotBlank @Size(max = 2000) String careersUrl) {}

    /** What a careers link resolves to, and whether that source needs search phrases to stay manageable. */
    record DetectResponse(AtsType ats, String board, boolean searchBased) {

        static DetectResponse of(DetectedBoard board) {
            return new DetectResponse(board.ats(), board.board(), board.ats().isSearchBased());
        }
    }

    record CompanyResponse(
            Long id,
            String name,
            AtsType ats,
            String board,
            String careersUrl,
            String search,
            boolean searchBased,
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
                    c.getSearch(),
                    c.getAts().isSearchBased(),
                    c.isActive(),
                    openJobs,
                    c.getLastSyncedAt(),
                    c.getLastSyncStatus(),
                    c.getLastSyncError());
        }
    }
}
