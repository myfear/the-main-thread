package dev.kbagent;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Bridges the A2A task lifecycle to the {@link KnowledgeBaseAgent} AI service.
 * <p>
 * The {@code deskflow-app} client sends parameters as ordered {@link TextPart}
 * values (same order as
 * {@code AgenticServices.a2aBuilder(...).inputKeys(...)}). This executor reads
 * them by index:
 * {@code [0] category}, {@code [1] severity}, {@code [2] summary},
 * {@code [3] details}.
 */
@ApplicationScoped
public class KnowledgeBaseExecutorProducer {

    private static final Logger LOG = Logger.getLogger(KnowledgeBaseExecutorProducer.class);

    private final KnowledgeBaseAgentInvoker kbAgentInvoker;

    @Inject
    public KnowledgeBaseExecutorProducer(KnowledgeBaseAgentInvoker kbAgentInvoker) {
        this.kbAgentInvoker = kbAgentInvoker;
    }

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                TaskUpdater updater = new TaskUpdater(context, eventQueue);
                LOG.info("[KBAgent] Received A2A task request");

                if (context.getTask() == null) {
                    updater.submit();
                }
                updater.startWork();

                List<String> inputs = new ArrayList<>();
                Message message = context.getMessage();
                if (message != null && message.getParts() != null) {
                    for (Part<?> part : message.getParts()) {
                        if (part instanceof TextPart textPart) {
                            inputs.add(textPart.getText());
                        }
                    }
                }

                if (inputs.size() < 4) {
                    throw new InvalidParamsError(
                            "Expected 4 text parts: category, severity, summary, details. Got: " + inputs.size());
                }

                String category = inputs.get(0);
                String severity = inputs.get(1);
                String summary = inputs.get(2);
                String details = inputs.get(3);

                LOG.infof("[KBAgent] Looking up remediation — category=%s severity=%s", category, severity);

                String remediation = kbAgentInvoker.findRemediation(category, severity, summary, details);

                LOG.debugf("[KBAgent] Remediation result: %s", remediation);

                updater.addArtifact(List.of(new TextPart(remediation)));
                updater.complete();
            }

            @Override
            public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                new TaskUpdater(context, eventQueue).cancel();
            }
        };
    }
}
