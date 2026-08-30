package com.themainthread.carrierwebhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.themainthread.carrierwebhooks.transform.CarrierWebhookTransformer;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class TransformerBoundaryTest {

    @Inject
    ScriptInterfaceFactory<CarrierWebhookTransformer, Void> transformerFactory;

    @Test
    void aDynamicTransformerDoesNotReceiveFetchByDefault() {
        CarrierWebhookTransformer transformer = transformerFactory.create("""
                function normalize(payload) {
                    return typeof fetch;
                }
                export { normalize };
                """, null);

        assertEquals("undefined", transformer.normalize("{}"));
    }
}
