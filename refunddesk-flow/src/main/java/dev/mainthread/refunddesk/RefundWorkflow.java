package dev.mainthread.refunddesk;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consumed;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class RefundWorkflow extends Flow {

    private final RefundPolicy policy;
    private final DecisionStore decisions;

    public RefundWorkflow(RefundPolicy policy, DecisionStore decisions) {
        this.policy = policy;
        this.decisions = decisions;
    }

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("refund-review", "refunddesk", "1.0.0")
                .tasks(
                        function("evaluatePolicy", policy::evaluate, RefundRequest.class),
                        switchWhenOrElse(this::needsManualReview, "requestManualReview", "finishAutomatic",
                                RefundCase.class),
                        emitJson("requestManualReview", "refund.review.required", RefundCase.class),
                        listen("waitForReview",
                                toOne(consumed("refund.review.completed").extensionByInstanceId("flowinstanceid"))),
                        consume("finishManualReview", decisions::recordManualReview, ReviewDecision.class)
                                .then(FlowDirectiveEnum.END),
                        consume("finishAutomatic", decisions::recordAutomatic, RefundCase.class)
                                .then(FlowDirectiveEnum.END))
                .build();
    }

    private boolean needsManualReview(RefundCase refundCase) {
        return DecisionOutcome.MANUAL_REVIEW.equals(refundCase.decision().outcome());
    }
}
