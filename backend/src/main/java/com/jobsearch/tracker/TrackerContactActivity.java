package com.jobsearch.tracker;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobsearch.contact.ContactActivity;

@Component
class TrackerContactActivity implements ContactActivity {

    private final OutreachRepository outreach;

    TrackerContactActivity(OutreachRepository outreach) {
        this.outreach = outreach;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Latest> byContactId() {
        Map<Long, Latest> latest = new HashMap<>();
        // Newest first, so the first seen per contact is their latest conversation
        for (Outreach o : outreach.findAllByOrderByLastContactedAtDesc()) {
            latest.merge(o.getContact().getId(),
                    new Latest(o.getStatus().name(), o.getLastContactedAt(), 1),
                    (kept, more) -> new Latest(kept.status(), kept.lastContactedAt(), kept.conversations() + 1));
        }
        return latest;
    }
}
