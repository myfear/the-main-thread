package com.themainthread.fernbank;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FernbankTools {

    @Tool(
            name = "docs_generate",
            description = "Generate internal documentation from approved project context.",
            annotations = @Tool.Annotations(
                    title = "Documentation Generator",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    String generateDocs(@ToolArg(description = "Documentation topic") String topic) {
        return "Generated documentation for: " + topic;
    }

    @Tool(
            name = "pptx_export",
            description = "Export a presentation through the Acme third-party renderer.",
            annotations = @Tool.Annotations(
                    title = "PPTX Exporter",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    String exportPresentation(@ToolArg(description = "Presentation title") String title) {
        return "Exported presentation: " + title;
    }

    @Tool(
            name = "unsigned_status",
            description = "Read deployment status through an unsigned internal lab skill.",
            annotations = @Tool.Annotations(
                    title = "Unsigned Status Reader",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    String readStatus(@ToolArg(description = "Service name") String service) {
        return service + " is healthy";
    }
}
