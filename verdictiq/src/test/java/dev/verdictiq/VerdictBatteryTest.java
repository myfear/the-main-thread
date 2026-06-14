package dev.verdictiq;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.verdictiq.model.PanelVerdict;
import dev.verdictiq.model.SubmissionAccepted;
import dev.verdictiq.model.SubmitVerdictRequest;
import dev.verdictiq.model.VerdictStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@EnabledIfSystemProperty(named = "verdictiq.live", matches = "true")
class VerdictBatteryTest {

    private static final Logger LOG = Logger.getLogger(VerdictBatteryTest.class);

    @Inject
    ObjectMapper objectMapper;

    @Test
    void runsTheAmbiguousBattery() throws Exception {
        List<AmbiguousText> samples = loadSamples();
        List<PanelVerdict> results = new ArrayList<>();

        for (AmbiguousText sample : samples) {
            SubmissionAccepted accepted = given()
                    .contentType(ContentType.JSON)
                    .body(new SubmitVerdictRequest(sample.text()))
                    .when()
                    .post("/verdict")
                    .then()
                    .statusCode(202)
                    .extract()
                    .as(SubmissionAccepted.class);

            PanelVerdict verdict = waitForVerdict(accepted.id());
            results.add(verdict);

            assertEquals(VerdictStatus.COMPLETE, verdict.status(),
                    () -> "Verdict did not complete for: " + sample.text() + " (" + verdict.finalReason() + ")");
            assertNotNull(verdict.finalVerdict(), () -> "Final label missing for: " + sample.text());
        }

        logSummary(samples, results);
    }

    private List<AmbiguousText> loadSamples() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ambiguous-texts.json")) {
            if (stream == null) {
                throw new IllegalStateException("ambiguous-texts.json was not found.");
            }
            return objectMapper.readValue(stream, new TypeReference<List<AmbiguousText>>() {
            });
        }
    }

    private PanelVerdict waitForVerdict(String id) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));

        while (Instant.now().isBefore(deadline)) {
            PanelVerdict verdict = given()
                    .when()
                    .get("/verdict/{id}", id)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(PanelVerdict.class);

            if (verdict.status() != VerdictStatus.PENDING) {
                return verdict;
            }

            Thread.sleep(250);
        }

        throw new IllegalStateException("Timed out waiting for verdict " + id);
    }

    private void logSummary(List<AmbiguousText> samples, List<PanelVerdict> results) {
        long disagreements = results.stream().filter(result -> !result.agreement()).count();
        long abstentions = results.stream().filter(PanelVerdict::abstained).count();

        StringBuilder table = new StringBuilder();
        table.append(System.lineSeparator());
        table.append(String.format("%-18s %-6s %-11s %-11s %-11s %-10s%n",
                "category", "agree", "granite", "mistral", "final", "abstained"));

        for (int i = 0; i < samples.size(); i++) {
            AmbiguousText sample = samples.get(i);
            PanelVerdict verdict = results.get(i);

            table.append(String.format("%-18s %-6s %-11s %-11s %-11s %-10s%n",
                    sample.category(),
                    verdict.agreement(),
                    verdict.graniteLabel(),
                    verdict.mistralLabel(),
                    verdict.finalVerdict(),
                    verdict.abstained()));
        }

        table.append(System.lineSeparator());
        table.append("Disagreements: ").append(disagreements).append(System.lineSeparator());
        table.append("Abstentions: ").append(abstentions);

        LOG.info(table.toString());
    }
}
