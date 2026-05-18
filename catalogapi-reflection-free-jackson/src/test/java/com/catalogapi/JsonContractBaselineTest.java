package com.catalogapi;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class JsonContractBaselineTest {

    @Test
    void summariesMatchBaselineContract() {
        JsonContractAssertions.assertSummariesContract();
    }

    @Test
    void pageMatchesBaselineContract() {
        JsonContractAssertions.assertPageContract();
    }

    @Test
    void polymorphicPayloadMatchesBaselineContract() {
        JsonContractAssertions.assertPolymorphicContract();
    }
}
