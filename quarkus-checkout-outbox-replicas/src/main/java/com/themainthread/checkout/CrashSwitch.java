package com.themainthread.checkout;

import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class CrashSwitch {

    private static final Logger LOG = Logger.getLogger(CrashSwitch.class);
    private final CheckoutConfig config;
    private final AtomicReference<CrashPoint> armed = new AtomicReference<>(CrashPoint.NONE);

    CrashSwitch(CheckoutConfig config) {
        this.config = config;
    }

    public void arm(CrashPoint point) {
        if (point == null || point == CrashPoint.NONE) {
            return;
        }
        if (!config.demo().enabled()) {
            throw new BadRequestException("Crash injection requires checkout.demo.enabled=true");
        }
        armed.set(point);
    }

    public void reset() {
        armed.set(CrashPoint.NONE);
    }

    public void maybeCrash(CrashPoint current) {
        if (!armed.compareAndSet(current, CrashPoint.NONE)) {
            return;
        }
        LOG.errorf("DEMO crash after %s", current);
        if (config.crash().halt()) {
            Runtime.getRuntime().halt(1);
        }
        throw new CrashWindowException(current);
    }
}
