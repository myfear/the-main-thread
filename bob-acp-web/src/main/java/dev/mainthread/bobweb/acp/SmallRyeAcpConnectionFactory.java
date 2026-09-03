package dev.mainthread.bobweb.acp;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import dev.mainthread.bobweb.config.BobConfig;
import io.smallrye.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.smallrye.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionResponse;

@ApplicationScoped
class SmallRyeAcpConnectionFactory implements AcpConnectionFactory {

    private static final Logger LOG = Logger.getLogger(SmallRyeAcpConnectionFactory.class);

    private final BobConfig config;

    SmallRyeAcpConnectionFactory(BobConfig config) {
        this.config = config;
    }

    @Override
    public AcpConnection open(Consumer<AcpEvent> eventConsumer,
            Function<RequestPermissionRequest, CompletionStage<RequestPermissionResponse>> permissionHandler) {
        AgentParameters.Builder parameters = AgentParameters.builder(config.binary());
        for (String argument : config.arguments()) {
            if (!argument.isBlank()) {
                parameters.arg(argument.trim());
            }
        }
        config.apiKey().filter(apiKey -> !apiKey.isBlank()).ifPresent(apiKey -> {
            parameters.addEnvVar("BOB_API_KEY", apiKey);
            parameters.addEnvVar("BOBSHELL_API_KEY", apiKey);
        });

        StdioAcpClientTransport transport = new StdioAcpClientTransport(parameters.build());
        transport.setStdErrorHandler(line -> LOG.debugf("Bob stderr: %s", line));
        return new SmallRyeAcpConnection(transport, config.requestTimeout(), config.promptTimeout(), eventConsumer,
                permissionHandler);
    }
}
