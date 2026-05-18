package com.catalogapi;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ReflectionFreeProfile.class)
class JsonContractReflectionFreeTest {

    @Test
    void summariesMatchReflectionFreeContract() {
        JsonContractAssertions.assertSummariesContract();
    }

    @Test
    void pageMatchesReflectionFreeContract() {
        JsonContractAssertions.assertPageContract();
    }

    @Test
    void polymorphicPayloadMatchesReflectionFreeContract() {
        JsonContractAssertions.assertPolymorphicContract();
    }
}
