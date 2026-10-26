package com.themainthread.studio;

import java.util.UUID;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@ApplicationScoped
public class StudioLifecycleListener implements WorkflowExecutionListener {
    private static final Logger LOG = Logger.getLogger(StudioLifecycleListener.class);
    private final EventJournal journal;

    public StudioLifecycleListener(EventJournal journal) {
        this.journal = journal;
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        record(event, "WORKFLOW_STARTED", null);
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        record(event, "WORKFLOW_COMPLETED", event.output().asJavaObject());
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        record(event, "WORKFLOW_FAILED", null);
    }

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        record(event, "TASK_STARTED", null);
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        record(event, "TASK_COMPLETED", null);
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        LOG.warnf("Workflow task %s failed (%s)", event.taskContext().taskName(), event.cause().getClass().getSimpleName());
        record(event, "TASK_FAILED", Map.of("code", "NODE_EXECUTION_FAILED",
                "causeType", event.cause().getClass().getName(), "status", WorkflowError.error(event).status()));
    }

    private void record(WorkflowEvent event, String kind, Object output) {
        var context = event.workflowContext();
        var identity = context.definition().id();
        if (!"studio".equals(identity.namespace())) {
            return;
        }
        var runId = UUID.fromString(context.instanceData().input().asMap().orElseThrow().get("runId").toString());
        var task = event instanceof TaskEvent taskEvent ? taskEvent.taskContext() : null;
        journal.append(new EventJournal.PendingEvent(context.instanceData().id(), runId, identity.namespace(),
                identity.name(), identity.version(), kind, task == null ? null : task.taskName(),
                task == null ? 0 : task.retryAttempt(), event.eventDate(), output));
    }
}
