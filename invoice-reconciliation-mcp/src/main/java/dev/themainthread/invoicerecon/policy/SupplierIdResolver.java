package dev.themainthread.invoicerecon.policy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.themainthread.invoicerecon.domain.Invoice;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SupplierIdResolver {

    private static final Map<String, String> ALIASES = Map.of(
            "acme", "ACME",
            "acmesupplies", "ACME");

    public String resolve(String supplierId) {
        if (supplierId == null || supplierId.isBlank()) {
            return supplierId;
        }

        String trimmed = supplierId.trim();
        String alias = ALIASES.get(normalize(trimmed));
        if (alias != null) {
            return alias;
        }

        for (String knownSupplierId : knownSupplierIds()) {
            if (knownSupplierId.equalsIgnoreCase(trimmed)) {
                return knownSupplierId;
            }
        }

        return trimmed;
    }

    public String knownSupplierIdsMessage() {
        List<String> supplierIds = knownSupplierIds();
        if (supplierIds.isEmpty()) {
            return "No supplier IDs are seeded.";
        }
        return "Known supplier IDs: " + String.join(", ", supplierIds);
    }

    private List<String> knownSupplierIds() {
        return Invoice.find("select distinct supplierId from Invoice order by supplierId")
                .project(String.class)
                .list();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
