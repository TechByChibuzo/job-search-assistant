package com.jobsearch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class VisaCheckTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Export Control Requirement: Due to applicable export control laws and regulations, candidates must be a U.S. citizen or national, U.S. permanent resident (i.e., current Green Card holder), or lawfully admitted into the U.S. as a refugee or granted asylum. | CITIZENS_ONLY
            This position requires that the candidate selected be a US Citizen and obtain and maintain an active TS/SCI security clearance with polygraph. | CITIZENS_ONLY
            This position requires U.S. person status as defined by the applicable government contract. | CITIZENS_ONLY
            U.S. citizenship is required for this role. | CITIZENS_ONLY
            The candidate selected must obtain and maintain a security clearance at the TS/SCI with polygraph level. | CLEARANCE
            Must possess an active Top Secret/SCI security clearance with current polygraph | CLEARANCE
            We are unable to sponsor visas for this role. | NO_SPONSORSHIP
            We do not offer visa sponsorship. | NO_SPONSORSHIP
            The company will not sponsor applicants for work visas. | NO_SPONSORSHIP
            Candidates must be authorized to work in the U.S. without the need for current or future visa sponsorship. | NO_SPONSORSHIP
            Visa sponsorship is not available for this position. | NO_SPONSORSHIP
            This role is not eligible for immigration sponsorship. | NO_SPONSORSHIP
            We cannot provide H-1B sponsorship. | NO_SPONSORSHIP
            To comply with U.S. export control laws and regulations, candidates for this role may need to meet certain legal status requirements as provided in those laws and regulations. | MAY_BE_RESTRICTED
            This position may require a deemed export control license for compliance with applicable laws and regulations. | MAY_BE_RESTRICTED
            An active Secret clearance is a plus. | MAY_BE_RESTRICTED
            """)
    void recognisesRestrictions(String text, VisaRestriction expected) {
        assertThat(VisaCheck.check("We build things. " + text + " Apply today.").restriction()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "The Sponsored Products and Brands team at Amazon Ads is re-imagining advertising.",
            "Grow senior and Staff engineers through sponsorship, not supervision.",
            "Our employee-led and company-sponsored affinity groups promote inclusion.",
            "Visa sponsorship is available for this role.",
            "You must be authorized to work in the United States.",
            "Partner globally with sponsors, users and engineering colleagues.",
            "Experience with export control tooling is a plus.",
    })
    void leavesOrdinaryPostingsAlone(String text) {
        assertThat(VisaCheck.check(text).restriction()).isNull();
    }

    @Test
    void keepsTheSentenceAsEvidenceWithoutCuttingAtUS() {
        var result = VisaCheck.check("Build ground software. Export Control Requirement: Due to applicable export control laws and "
                + "regulations, candidates must be a U.S. citizen or national, U.S. permanent resident (i.e., current Green Card "
                + "holder), or lawfully admitted into the U.S. as a refugee or granted asylum. Key job responsibilities follow.");
        assertThat(result.evidence())
                .startsWith("Export Control Requirement: Due to applicable")
                .endsWith("granted asylum.")
                .doesNotContain("Key job responsibilities");
    }

    @Test
    void theStrictestRestrictionWins() {
        var result = VisaCheck.check("We do not sponsor visas. Candidates must be a U.S. citizen.");
        assertThat(result.restriction()).isEqualTo(VisaRestriction.CITIZENS_ONLY);
        assertThat(result.evidence()).isEqualTo("Candidates must be a U.S. citizen.");
    }

    @Test
    void nothingToReadMeansNothingFound() {
        assertThat(VisaCheck.check(null).restriction()).isNull();
        assertThat(VisaCheck.check("  ").evidence()).isNull();
    }
}
