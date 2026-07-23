package com.themainthread.fernbank;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

@QuarkusTest
class ToolFilterRequestContextTest {

    @Inject
    OpaToolFilter filter;

    @Inject
    ToolManager toolManager;

    @Test
    void deniesWhenNoAuthenticatedRequestContextIsAvailable() {
        boolean allowed = CompletableFuture.supplyAsync(() -> {
            assertFalse(Arc.container().requestContext().isActive());
            boolean result = filter.test(toolManager.getTool("docs_generate"), filterContext());
            assertFalse(Arc.container().requestContext().isActive());
            return result;
        }).join();

        assertFalse(allowed);
    }

    private FilterContext filterContext() {
        return new FilterContext() {
            @Override
            public McpMethod method() {
                return McpMethod.TOOLS_LIST;
            }

            @Override
            public McpConnection connection() {
                return transientConnection();
            }

            @Override
            public Meta meta() {
                return null;
            }

            @Override
            public RequestId requestId() {
                return new RequestId("request-context-regression");
            }
        };
    }

    private McpConnection transientConnection() {
        return new McpConnection() {
            @Override
            public String id() {
                return "request-context-regression";
            }

            @Override
            public Status status() {
                return Status.IN_OPERATION;
            }

            @Override
            public InitialRequest initialRequest() {
                return null;
            }

            @Override
            public LogLevel logLevel() {
                return null;
            }

            @Override
            public String serverName() {
                return "default";
            }

            @Override
            public boolean isTransient() {
                return true;
            }
        };
    }
}
