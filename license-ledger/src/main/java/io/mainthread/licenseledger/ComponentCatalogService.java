package io.mainthread.licenseledger;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ComponentCatalogService {

    private static final Set<String> APPROVED_LICENSES = Set.of(
            "Apache-2.0",
            "BSD-2-Clause",
            "BSD-3-Clause",
            "MIT");

    private static final Set<String> REVIEW_LICENSES = Set.of(
            "EPL-2.0",
            "LGPL-2.1-only",
            "LGPL-2.1-or-later",
            "MPL-2.0");

    public List<ComponentReport> sampleComponents() {
        return List.of(
                review(new ComponentRequest(
                        "Quarkus",
                        "io.quarkus",
                        "quarkus-rest-jackson",
                        "3.27.2",
                        "Apache-2.0")),
                review(new ComponentRequest(
                        "Package URL",
                        "com.github.package-url",
                        "packageurl-java",
                        "1.5.0",
                        "MIT")),
                review(new ComponentRequest(
                        "Example Vendor",
                        "org.example",
                        "legacy-reports",
                        "2.4.1",
                        "GPL-2.0-only")));
    }

    public ComponentReport review(ComponentRequest request) {
        String coordinate = request.groupId() + ":" + request.artifactId() + ":" + request.version();
        String licenseExpression = normalize(request.licenseExpression());
        String decision = decisionFor(licenseExpression);
        String note = noteFor(licenseExpression, decision);

        return new ComponentReport(
                normalize(request.supplier()),
                coordinate,
                buildPurl(request),
                licenseExpression,
                decision,
                note);
    }

    private String buildPurl(ComponentRequest request) {
        try {
            return new PackageURL(
                    "maven",
                    request.groupId(),
                    request.artifactId(),
                    request.version(),
                    new TreeMap<>(java.util.Map.of("type", "jar")),
                    null).canonicalize();
        } catch (MalformedPackageURLException e) {
            throw new IllegalArgumentException("Invalid Maven coordinates for SPDX demo", e);
        }
    }

    private String decisionFor(String licenseExpression) {
        if (licenseExpression.contains(" OR ") || licenseExpression.contains(" WITH ")) {
            return "manual-review";
        }
        if (APPROVED_LICENSES.contains(licenseExpression)) {
            return "approved";
        }
        if (REVIEW_LICENSES.contains(licenseExpression)) {
            return "manual-review";
        }
        if (licenseExpression.contains("GPL") || licenseExpression.contains("AGPL")) {
            return "blocked";
        }
        return "manual-review";
    }

    private String noteFor(String licenseExpression, String decision) {
        if ("approved".equals(decision)) {
            return "Known SPDX identifier with a policy rule that can pass automatically.";
        }
        if ("blocked".equals(decision)) {
            return "Copyleft licenses are not rejected by SPDX, but this demo policy sends them to a hard stop.";
        }
        if (licenseExpression.contains(" OR ") || licenseExpression.contains(" WITH ")) {
            return "The SBOM can keep this SPDX expression exactly. A human still needs to decide which branch is acceptable.";
        }
        return "The SBOM stays useful, but this license still needs a reviewer before it reaches production.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
