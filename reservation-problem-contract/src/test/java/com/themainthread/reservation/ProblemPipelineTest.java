package com.themainthread.reservation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.PostProcessorsRegistry;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@QuarkusTest
class ProblemPipelineTest {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    @Inject
    PostProcessorsRegistry registry;

    @Test
    void processorsRunHighestPriorityFirst() {
        List<Integer> priorities = registry.getProcessors().stream()
                .map(ProblemPostProcessor::priority)
                .toList();
        List<String> names = registry.getProcessors().stream()
                .map(processor -> processor.getClass().getSimpleName())
                .toList();

        assertThat(priorities, contains(100, 99, 50, 0));
        assertThat(names, hasItem(containsString("SupportIdPostProcessor")));
    }

    @Test
    void serverErrorGetsSupportIdThroughThePipeline() {
        HttpProblem processed = registry.applyPostProcessing(
                HttpProblem.valueOf(Response.Status.INTERNAL_SERVER_ERROR),
                ProblemContext.of(new IllegalStateException("Inventory ledger is unreachable"), "/dev-ui/test"));

        assertThat(processed.getInstance().toString(), equalTo("/dev-ui/test"));
        assertThat((String) processed.getParameters().get("supportId"), matchesPattern(UUID_PATTERN));
        assertThat(processed.getDetail(), nullValue());
        assertThat(processed.getMessage(), not(containsString("Inventory ledger is unreachable")));
    }

    @Test
    void clientErrorDoesNotGetSupportId() {
        HttpProblem processed = registry.applyPostProcessing(
                HttpProblem.valueOf(Response.Status.BAD_REQUEST),
                ProblemContext.of(new RuntimeException("ignored"), "/reservations"));

        assertThat(processed.getParameters().get("supportId"), nullValue());
    }
}
