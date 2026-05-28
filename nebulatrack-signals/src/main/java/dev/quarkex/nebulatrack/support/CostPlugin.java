package dev.quarkex.nebulatrack.support;

import dev.quarkex.nebulatrack.model.CostAnomaly;

@FunctionalInterface
public interface CostPlugin {

    void process(CostAnomaly anomaly);
}
