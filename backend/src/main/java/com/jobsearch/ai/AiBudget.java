package com.jobsearch.ai;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.jobsearch.config.AppProperties;

/**
 * A hard ceiling on Claude requests per day, whatever asks for them: the daily scoring step, "Check my fit"
 * and drafts alike. Kept in memory, so a restart resets it; that is enough to stop a runaway loop or abuse
 * from running up the bill.
 */
@Component
class AiBudget {

    private final int perDay;
    private final Clock clock;
    private LocalDate day;
    private int used;

    AiBudget(AppProperties properties, Clock clock) {
        this.perDay = properties.ai().requestCap();
        this.clock = clock;
    }

    /** Counts one request, or refuses it when today's allowance is used up. */
    synchronized void spend() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= perDay) {
            throw new AiException("Today's limit of " + perDay + " Claude requests is used up. It resets at midnight; "
                    + "raise AI_REQUEST_CAP in .env if you need more.");
        }
        used++;
    }

    synchronized int usedToday() {
        return LocalDate.now(clock).equals(day) ? used : 0;
    }

    int perDay() {
        return perDay;
    }
}
