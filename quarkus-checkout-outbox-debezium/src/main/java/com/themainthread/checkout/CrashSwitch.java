package com.themainthread.checkout;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class CrashSwitch {

    private static final Logger LOG = Logger.getLogger(CrashSwitch.class);

    private final CrashConfig config;

    CrashSwitch(CrashConfig config) {
        this.config = config;
    }

    void checkAllowed(CrashPoint point) {
        if (point != CrashPoint.NONE && !config.enabled()) {
            throw new BadRequestException("Crash controls are disabled");
        }
    }

    void maybeCrash(CrashPoint point) {
        if (point == CrashPoint.NONE) {
            return;
        }
        checkAllowed(point);
        if (config.halt()) {
            LOG.errorf("Halting the JVM after %s", point);
            Runtime.getRuntime().halt(1);
        }
        throw new CrashWindowException(point);
    }
}
