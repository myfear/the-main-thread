package dev.verdictiq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.verdictiq.model.PanelVerdict;
import dev.verdictiq.model.Sentiment;
import dev.verdictiq.model.SubmissionAccepted;
import dev.verdictiq.model.SubmitVerdictRequest;
import dev.verdictiq.model.VerdictStatus;
import dev.verdictiq.testsupport.StubAiProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import org.awaitility.Awaitility;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(StubAiProfile.class)
class VerdictResourceTest {

    @Test
    void rejectsBlankText() {
        given()
                .contentType(ContentType.JSON)
                .body(new SubmitVerdictRequest(" "))
                .when()
                .post("/verdict")
                .then()
                .statusCode(400)
                .body("error", equalTo("text is required"));
    }

    @Test
    void returnsNotFoundForUnknownVerdict() {
        given()
                .when()
                .get("/verdict/does-not-exist")
                .then()
                .statusCode(404);
    }

    @Test
    void completesConsensusWithoutJudge() {
        PanelVerdict verdict = awaitVerdict(submit("This is the best Java framework I have ever used."));

        assertThat(verdict.status()).isEqualTo(VerdictStatus.COMPLETE);
        assertThat(verdict.agreement()).isTrue();
        assertThat(verdict.finalVerdict()).isEqualTo(Sentiment.POSITIVE);
        assertThat(verdict.finalReason()).isEqualTo("Panel consensus");
        assertThat(verdict.abstained()).isFalse();
    }

    @Test
    void adjudicatesDisagreementToUncertain() {
        PanelVerdict verdict = awaitVerdict(
                submit("The hotel was conveniently located near the airport, which meant we could hear every plane."));

        assertThat(verdict.status()).isEqualTo(VerdictStatus.COMPLETE);
        assertThat(verdict.agreement()).isFalse();
        assertThat(verdict.graniteLabel()).isEqualTo(Sentiment.NEUTRAL);
        assertThat(verdict.mistralLabel()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(verdict.finalVerdict()).isEqualTo(Sentiment.UNCERTAIN);
        assertThat(verdict.abstained()).isTrue();
    }

    @Test
    void marksVerdictFailedWhenPanelThrows() {
        PanelVerdict verdict = awaitVerdict(submit("panel should fail"));

        assertThat(verdict.status()).isEqualTo(VerdictStatus.FAILED);
        assertThat(verdict.finalReason()).contains("Stub panel failed on purpose.");
    }

    @Test
    void marksVerdictFailedWhenJudgeThrows() {
        PanelVerdict verdict = awaitVerdict(submit("judge should fail"));

        assertThat(verdict.status()).isEqualTo(VerdictStatus.FAILED);
        assertThat(verdict.agreement()).isFalse();
        assertThat(verdict.finalReason()).contains("Judge failed");
    }

    private String submit(String text) {
        SubmissionAccepted accepted = given()
                .contentType(ContentType.JSON)
                .body(new SubmitVerdictRequest(text))
                .when()
                .post("/verdict")
                .then()
                .statusCode(202)
                .extract()
                .as(SubmissionAccepted.class);

        return accepted.id();
    }

    private PanelVerdict awaitVerdict(String id) {
        AtomicReference<PanelVerdict> verdictRef = new AtomicReference<>();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    PanelVerdict verdict = given()
                            .when()
                            .get("/verdict/{id}", id)
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(PanelVerdict.class);

                    verdictRef.set(verdict);
                    assertThat(verdict.status()).isNotEqualTo(VerdictStatus.PENDING);
                });

        return verdictRef.get();
    }
}
