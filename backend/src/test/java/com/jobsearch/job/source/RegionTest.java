package com.jobsearch.job.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegionTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "New York, NY",
        "US, CA, Santa Clara",
        "US-TX-Austin",
        "Remote - US",
        "Remote, US",
        "United States",
        "New York, New York, United States of America",
        "Bellevue, Washington, USA",
        "San Francisco",
        "Mountain View, CA, USA; Kirkland, WA, USA",
        "Remote (U.S.)",
        "New London, CT",
    })
    void recognisesUsLocations(String location) {
        assertThat(Region.fromLocation(location)).isEqualTo(Region.US);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Tokyo, Japan",
        "London, United Kingdom",
        "Toronto",
        "Pune, India",
        "Paris",
        "Remote - EMEA",
        "São Paulo, Brazil",
        "Latin America",
    })
    void recognisesPlacesElsewhere(String location) {
        assertThat(Region.fromLocation(location)).isEqualTo(Region.NON_US);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Remote", "Hybrid", "6 Locations", "Milwaukee Office", ""})
    void staysUnsureRatherThanGuess(String location) {
        // Milwaukee mustn't match "uk"; vague text mustn't drop a role that may be in the US
        assertThat(Region.fromLocation(location)).isEqualTo(Region.UNKNOWN);
    }

    @Test
    void readsCountryFields() {
        assertThat(Region.fromCountry("US")).isEqualTo(Region.US);
        assertThat(Region.fromCountry("USA")).isEqualTo(Region.US);
        assertThat(Region.fromCountry("United States")).isEqualTo(Region.US);
        assertThat(Region.fromCountry("FR")).isEqualTo(Region.NON_US);
        assertThat(Region.fromCountry(null)).isEqualTo(Region.UNKNOWN);
    }

    @Test
    void aRoleInSeveralPlacesIsUsWhenAnyIs() {
        assertThat(Region.anyOf(List.of(Region.NON_US, Region.US))).isEqualTo(Region.US);
        assertThat(Region.anyOf(List.of(Region.NON_US, Region.NON_US))).isEqualTo(Region.NON_US);
        assertThat(Region.anyOf(List.of(Region.NON_US, Region.UNKNOWN))).isEqualTo(Region.UNKNOWN);
        assertThat(Region.anyOf(List.of())).isEqualTo(Region.UNKNOWN);
    }
}
