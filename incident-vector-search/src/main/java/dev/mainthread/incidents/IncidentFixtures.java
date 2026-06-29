package dev.mainthread.incidents;

import java.util.List;

final class IncidentFixtures {

    private IncidentFixtures() {
    }

    static List<IncidentInput> examples() {
        return List.of(
                new IncidentInput(
                        "INC-1001",
                        "checkout-service",
                        "prod",
                        "java.lang.NullPointerException",
                        "Cannot invoke DiscountPolicy.percentage because policy is null while applying coupon",
                        List.of(
                                "dev.mainthread.checkout.CartPriceCalculator.applyDiscount(CartPriceCalculator.java:84)",
                                "dev.mainthread.checkout.CheckoutService.priceCart(CheckoutService.java:47)",
                                "dev.mainthread.checkout.CheckoutResource.pay(CheckoutResource.java:31)"),
                        "Guard missing coupon policy before discount calculation",
                        "https://runbooks.example.com/incidents/INC-1001"),
                new IncidentInput(
                        "INC-1002",
                        "billing-service",
                        "prod",
                        "java.sql.SQLTransientConnectionException",
                        "Timed out waiting for database connection from the invoice pool",
                        List.of(
                                "dev.mainthread.billing.InvoiceRepository.findOpenInvoices(InvoiceRepository.java:118)",
                                "dev.mainthread.billing.InvoiceBatch.closeCurrentPeriod(InvoiceBatch.java:55)",
                                "io.agroal.pool.ConnectionPool.handlerFromSharedCache(ConnectionPool.java:321)"),
                        "Increase pool timeout and split invoice close job into smaller batches",
                        "https://runbooks.example.com/incidents/INC-1002"),
                new IncidentInput(
                        "INC-1003",
                        "checkout-service",
                        "prod",
                        "java.util.concurrent.TimeoutException",
                        "Tax service call exceeded the 800 ms client timeout during payment authorization",
                        List.of(
                                "dev.mainthread.checkout.TaxClient.calculate(TaxClient.java:62)",
                                "dev.mainthread.checkout.CheckoutService.priceCart(CheckoutService.java:52)",
                                "dev.mainthread.checkout.CheckoutResource.pay(CheckoutResource.java:31)"),
                        "Cache tax jurisdiction lookups and fail payment before authorization",
                        "https://runbooks.example.com/incidents/INC-1003"),
                new IncidentInput(
                        "INC-1004",
                        "inventory-service",
                        "staging",
                        "jakarta.persistence.OptimisticLockException",
                        "Concurrent stock reservation updated the same SKU version",
                        List.of(
                                "dev.mainthread.inventory.StockRepository.reserve(StockRepository.java:77)",
                                "dev.mainthread.inventory.ReservationService.reserveCart(ReservationService.java:41)",
                                "dev.mainthread.inventory.InventoryResource.reserve(InventoryResource.java:29)"),
                        "Retry stock reservation once and return conflict after the second collision",
                        "https://runbooks.example.com/incidents/INC-1004"),
                new IncidentInput(
                        "INC-1005",
                        "search-service",
                        "prod",
                        "com.fasterxml.jackson.databind.JsonMappingException",
                        "Cannot deserialize shipment filter because status contains an unknown enum value",
                        List.of(
                                "dev.mainthread.search.ShipmentSearchResource.search(ShipmentSearchResource.java:37)",
                                "com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3895)",
                                "io.quarkus.resteasy.reactive.jackson.runtime.serialisers.ServerJacksonMessageBodyReader.readFrom(ServerJacksonMessageBodyReader.java:92)"),
                        "Reject unknown status values at the API boundary",
                        "https://runbooks.example.com/incidents/INC-1005"));
    }
}
