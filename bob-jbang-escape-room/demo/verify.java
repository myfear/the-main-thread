///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.junit.jupiter:junit-jupiter:5.11.4
//DEPS org.junit.platform:junit-platform-launcher:1.11.4
//SOURCES src/Airlock.java tests/AirlockTest.java

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

class Verify {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(AirlockTest.class))
                .build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        // Test results are this CLI's output, not application logging.
        System.out.printf("AIRLOCK TESTS: %d/%d passed; %d failed%n",
                summary.getTestsSucceededCount(), summary.getTestsFoundCount(), summary.getTestsFailedCount());
        for (var failure : summary.getFailures()) {
            System.out.printf("FAIL %s: %s%n", failure.getTestIdentifier().getDisplayName(),
                    failure.getException().getMessage());
        }
        System.exit(summary.getTestsSucceededCount() == 7 && summary.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
