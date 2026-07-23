package com.themainthread.releasegate;

import io.github.amadeusitgroup.testcontainers.nats.NatsContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class NatsTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int NATS_PORT = 4222;

    private NatsContainer container;

    @Override
    public Map<String, String> start() {
        container = new NatsContainer("nats:2.14.1-alpine").withExposedPorts(NATS_PORT);
        container.start();
        return Map.of(
                "flamme.nats.url",
                "nats://localhost:" + container.getMappedPort(NATS_PORT),
                "release-gate.node-id",
                "test-node");
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
