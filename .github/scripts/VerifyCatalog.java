//JAVA 21+
//DEPS org.junit.jupiter:junit-jupiter:5.13.4
//DEPS org.junit.platform:junit-platform-launcher:1.13.4
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES ProjectCatalog.java ProjectCatalogTest.java

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.io.PrintWriter;

public class VerifyCatalog {
    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ProjectCatalogTest.class)).build(), listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.err, true));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0 || summary.getContainersFailedCount() > 0)
            System.exit(1);
    }
}
