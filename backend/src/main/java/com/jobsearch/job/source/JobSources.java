package com.jobsearch.job.source;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jobsearch.company.AtsType;

/** Finds the client for an ATS. Adding an ATS means adding one JobSource bean. */
@Component
public class JobSources {

    private final Map<AtsType, JobSource> byType = new EnumMap<>(AtsType.class);

    public JobSources(List<JobSource> sources) {
        sources.forEach(s -> byType.put(s.type(), s));
    }

    public JobSource forType(AtsType type) {
        JobSource source = byType.get(type);
        if (source == null) {
            throw new IllegalStateException("No job source for " + type);
        }
        return source;
    }
}
