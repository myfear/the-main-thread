package dev.mainthread.incidents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class IncidentVectorizerTest {

    private final IncidentVectorizer vectorizer = new IncidentVectorizer();

    @Test
    void similarCheckoutFailuresScoreHigherThanUnrelatedBillingFailures() {
        IncidentInput query = new IncidentInput(
                null,
                "checkout-service",
                "prod",
                "java.lang.NullPointerException",
                "Cannot invoke DiscountPolicy.percentage because policy is null",
                List.of(
                        "dev.mainthread.checkout.CartPriceCalculator.applyDiscount(CartPriceCalculator.java:91)",
                        "dev.mainthread.checkout.CheckoutService.priceCart(CheckoutService.java:47)"),
                null,
                null);

        IncidentInput checkout = IncidentFixtures.examples().get(0);
        IncidentInput billing = IncidentFixtures.examples().get(1);

        double checkoutScore = vectorizer.cosine(vectorizer.vectorForSearch(query), vectorizer.vectorForSearch(checkout));
        double billingScore = vectorizer.cosine(vectorizer.vectorForSearch(query), vectorizer.vectorForSearch(billing));

        assertTrue(checkoutScore > 0.75, "Expected close checkout match but got " + checkoutScore);
        assertTrue(checkoutScore > billingScore + 0.35,
                "Expected checkout score " + checkoutScore + " to beat billing score " + billingScore);
    }

    @Test
    void vectorsUseTheConfiguredCollectionDimension() {
        float[] vector = vectorizer.vectorForSearch(IncidentFixtures.examples().get(0));

        assertEquals(384, vector.length);
    }
}
