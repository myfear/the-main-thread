package io.mainthread.licenseledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComponentCatalogServiceTest {

    private final ComponentCatalogService service = new ComponentCatalogService();

    @Test
    void shouldApproveKnownLicense() {
        ComponentReport report = service.review(new ComponentRequest(
                "Package URL",
                "com.github.package-url",
                "packageurl-java",
                "1.5.0",
                "MIT"));

        assertEquals("approved", report.decision());
        assertEquals("pkg:maven/com.github.package-url/packageurl-java@1.5.0?type=jar", report.purl());
    }

    @Test
    void shouldEscalateCompositeLicenseExpression() {
        ComponentReport report = service.review(new ComponentRequest(
                "Example Vendor",
                "org.example",
                "dual-licensed-lib",
                "2.0.0",
                "Apache-2.0 OR GPL-2.0-only"));

        assertEquals("manual-review", report.decision());
        assertTrue(report.note().contains("human"));
    }

    @Test
    void shouldBlockCopyleftLicense() {
        ComponentReport report = service.review(new ComponentRequest(
                "Example Vendor",
                "org.example",
                "legacy-reports",
                "2.4.1",
                "GPL-2.0-only"));

        assertEquals("blocked", report.decision());
        assertTrue(report.note().contains("hard stop"));
    }
}
