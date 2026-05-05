package dev.conduit.workflow.mcp;

import dev.conduit.workflow.config.ConduitMcpConfig;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConduitMcpBridge {

    private final McpClient client;

    @Inject
    public ConduitMcpBridge(ConduitMcpConfig config) {
        var transport = StreamableHttpMcpTransport.builder().url(config.serverUrl()).build();
        this.client = new DefaultMcpClient.Builder().transport(transport).build();
    }

    public McpClient client() {
        return client;
    }

    @PreDestroy
    void shutdown() throws Exception {
        client.close();
    }
}