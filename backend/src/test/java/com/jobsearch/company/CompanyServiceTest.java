package com.jobsearch.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompanyServiceTest {

    @ParameterizedTest
    @CsvSource({"palantir, Palantir", "scale-ai, Scale Ai", "the_trade_desk, The Trade Desk", "x.ai, X Ai"})
    void suggestsANameFromTheBoard(String board, String name) {
        assertThat(CompanyService.nameFromBoard(board)).isEqualTo(name);
    }
}
