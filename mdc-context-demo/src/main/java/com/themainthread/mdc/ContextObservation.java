package com.themainthread.mdc;

import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

public record ContextObservation(String stage, String thread, String requestId) {

    private static final Logger LOG = Logger.getLogger(ContextObservation.class);

    static ContextObservation observe(String stage) {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        LOG.infov("stage={0}; observed request.id={1}", stage, id);
        return new ContextObservation(stage, Thread.currentThread().getName(), id);
    }
}
