package com.themainthread.decisions;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class GuardedOrderTools {
    private final DemoTools tools;

    GuardedOrderTools(DemoTools tools) {
        this.tools = tools;
    }

    @Tool("Read the order details for orderId")
    @ToolInputGuardrails(DecisionToolGuardrail.class)
    @Blocking
    public Object getOrder(String orderId) {
        return tools.execute(tools.validate(new ReviewRequest("Read after assessment", "getOrder",
                Map.of("orderId", orderId))));
    }

    @Tool("Simulate a refund of exactly amountCents euro cents for orderId")
    @ToolInputGuardrails(DecisionToolGuardrail.class)
    @Blocking
    public Object issueRefund(String orderId, long amountCents) {
        return tools.execute(tools.validate(new ReviewRequest("Refund after assessment", "issueRefund",
                Map.of("orderId", orderId, "amountCents", amountCents))));
    }
}
