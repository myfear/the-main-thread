package dev.mainthread.bobweb.acp;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionResponse;

public interface AcpConnectionFactory {

    AcpConnection open(Consumer<AcpEvent> eventConsumer,
            Function<RequestPermissionRequest, CompletionStage<RequestPermissionResponse>> permissionHandler);
}
