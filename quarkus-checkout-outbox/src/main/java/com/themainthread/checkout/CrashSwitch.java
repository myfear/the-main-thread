package com.themainthread.checkout;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CrashSwitch {

    private final boolean halt;

    private volatile CrashPoint armed = CrashPoint.NONE;

    CrashSwitch(@ConfigProperty(name = "checkout.crash.halt", defaultValue = "true") boolean halt) {
        this.halt = halt;
    }

    public void arm(CrashPoint point) {
        this.armed = point == null ? CrashPoint.NONE : point;
    }

    public void reset() {
        this.armed = CrashPoint.NONE;
    }

    public void maybeCrash(CrashPoint current) {
        if (armed != current) {
            return;
        }
        armed = CrashPoint.NONE;
        if (halt) {
            Log.errorf("Halting the JVM after %s", current);
            Runtime.getRuntime().halt(1);
        }
        throw new CrashWindowException(current);
    }
}
