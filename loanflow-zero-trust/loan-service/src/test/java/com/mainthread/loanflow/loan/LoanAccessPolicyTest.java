package com.mainthread.loanflow.loan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mainthread.loanflow.loan.model.LoanApplication;
import com.mainthread.loanflow.loan.model.LoanStatus;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;

class LoanAccessPolicyTest {

    private LoanApplication berlinDraft;
    private LoanApplication hamburgDraft;
    private LoanApplication berlinSubmitted;

    @BeforeEach
    void setUp() {
        berlinDraft = new LoanApplication("LN-100", "berlin", LoanStatus.DRAFT, "APP-100");
        hamburgDraft = new LoanApplication("LN-200", "hamburg", LoanStatus.DRAFT, "APP-200");
        berlinSubmitted = new LoanApplication("LN-300", "berlin", LoanStatus.SUBMITTED, "APP-300");
    }

    @Test
    void loanAdminCanReadAnyBranch() {
        var policy = policyFor(identity("loan_admin", "hq"));

        assertDoesNotThrow(() -> policy.checkCanRead(berlinDraft));
        assertDoesNotThrow(() -> policy.checkCanRead(hamburgDraft));
    }

    @Test
    void loanOfficerCanReadOwnBranch() {
        var policy = policyFor(identity("loan_officer", "berlin"));

        assertDoesNotThrow(() -> policy.checkCanRead(berlinDraft));
    }

    @Test
    void loanOfficerCannotReadOtherBranch() {
        var policy = policyFor(identity("loan_officer", "hamburg"));

        assertThrows(ForbiddenException.class, () -> policy.checkCanRead(berlinDraft));
    }

    @Test
    void loanAdminCanSubmitDraftLoan() {
        var policy = policyFor(identity("loan_admin", "hq"));

        assertDoesNotThrow(() -> policy.checkCanSubmit(berlinDraft));
    }

    @Test
    void loanOfficerCanSubmitOwnBranchDraftLoan() {
        var policy = policyFor(identity("loan_officer", "berlin"));

        assertDoesNotThrow(() -> policy.checkCanSubmit(berlinDraft));
    }

    @Test
    void loanOfficerCannotSubmitOtherBranchLoan() {
        var policy = policyFor(identity("loan_officer", "hamburg"));

        assertThrows(ForbiddenException.class, () -> policy.checkCanSubmit(berlinDraft));
    }

    @Test
    void cannotSubmitAlreadySubmittedLoan() {
        var policy = policyFor(identity("loan_officer", "berlin"));

        WebApplicationException exception =
                assertThrows(WebApplicationException.class, () -> policy.checkCanSubmit(berlinSubmitted));
        assertEquals(409, exception.getResponse().getStatus());
    }

    @Test
    void readsBranchFromJwtWhenIdentityAttributeMissing() {
        JsonWebToken token = mock(JsonWebToken.class);
        when(token.getClaim("branch")).thenReturn("berlin");
        CallerContext callerContext = new CallerContext(identity("loan_officer", null), resolvable(token));
        var policy = new LoanAccessPolicy(callerContext);

        assertDoesNotThrow(() -> policy.checkCanRead(berlinDraft));
    }

    private LoanAccessPolicy policyFor(SecurityIdentity identity) {
        return new LoanAccessPolicy(new CallerContext(identity, emptyTokens()));
    }

    @SuppressWarnings("unchecked")
    private Instance<JsonWebToken> emptyTokens() {
        Instance<JsonWebToken> tokens = mock(Instance.class);
        when(tokens.isResolvable()).thenReturn(false);
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private Instance<JsonWebToken> resolvable(JsonWebToken token) {
        Instance<JsonWebToken> tokens = mock(Instance.class);
        when(tokens.isResolvable()).thenReturn(true);
        when(tokens.get()).thenReturn(token);
        return tokens;
    }

    private SecurityIdentity identity(String role, String branch) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new Principal() {
                    @Override
                    public String getName() {
                        return "test-user";
                    }
                })
                .addRole(role);
        if (branch != null) {
            builder.addAttribute("branch", branch);
        }
        return builder.build();
    }
}
