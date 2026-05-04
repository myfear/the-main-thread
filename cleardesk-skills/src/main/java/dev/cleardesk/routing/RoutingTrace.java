package dev.cleardesk.routing;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped record of the last specialist chosen by the supervisor's delegate tools.
 */
@RequestScoped
public class RoutingTrace {

    private Specialist last;

    public void record(Specialist specialist) {
        this.last = specialist;
    }

    public Specialist getLastRoutedSpecialist() {
        return last;
    }

    public void reset() {
        this.last = null;
    }
}
