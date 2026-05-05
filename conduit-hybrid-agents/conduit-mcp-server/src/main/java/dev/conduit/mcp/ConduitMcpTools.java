package dev.conduit.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConduitMcpTools {

    @Tool(description = "Normalize a messy inbound record identifier into a canonical uppercase token.")
    public String conduit_normalize_record(@ToolArg(description = "Raw record id from upstream") String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }
        return rawId.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    @Tool(description = "Fingerprint a payload snippet using SHA-256 (hex).")
    public String conduit_fingerprint_payload(
            @ToolArg(description = "Payload body or JSON fragment") String payload_snippet)
            throws NoSuchAlgorithmException {
        String normalized = payload_snippet == null ? "" : payload_snippet;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}