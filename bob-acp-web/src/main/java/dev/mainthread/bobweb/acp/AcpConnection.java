package dev.mainthread.bobweb.acp;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.InitializeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ListSessionsResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.LoadSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.NewSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PromptResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ResumeSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SetSessionModeResponse;

public interface AcpConnection extends AutoCloseable {

    CompletionStage<InitializeResponse> initialize();

    CompletionStage<NewSessionResponse> newSession(Path workspace);

    CompletionStage<ListSessionsResponse> listSessions(Path workspace);

    CompletionStage<LoadSessionResponse> loadSession(Path workspace, String sessionId);

    CompletionStage<ResumeSessionResponse> resumeSession(Path workspace, String sessionId);

    CompletionStage<SetSessionModeResponse> setMode(String sessionId, String modeId);

    CompletionStage<PromptResponse> prompt(String sessionId, String prompt);

    void cancel(String sessionId);

    @Override
    void close();
}
