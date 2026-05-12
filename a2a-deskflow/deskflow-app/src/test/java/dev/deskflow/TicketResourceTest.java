package dev.deskflow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TicketResourceTest {

    @InjectMock
    TriageAgent triageAgent;

    @InjectMock
    KnowledgeBaseClient knowledgeBaseClient;

    @Test
    void postTicketReturnsTriageAndRemediation() {
        when(triageAgent.classify(anyString(), anyString()))
                .thenReturn("{\"severity\":\"HIGH\",\"category\":\"VPN\",\"escalationRequired\":true}");
        when(knowledgeBaseClient.findRemediation(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("- Verify GlobalProtect version.\n- Reconnect to vpn.corp.example.com.\n");

        given().contentType("application/json")
                .body(
                        """
                        {
                          "summary": "VPN will not connect",
                          "details": "After OS update",
                          "reportedBy": "test@example.com"
                        }
                        """)
                .when()
                .post("/tickets")
                .then()
                .statusCode(200)
                .body("severity", equalTo("HIGH"))
                .body("category", equalTo("VPN"))
                .body("escalationRequired", equalTo(true));
    }
}