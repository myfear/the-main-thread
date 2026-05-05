package dev.conduit.workflow.support;

import java.net.HttpURLConnection;
import java.net.URI;

final class OllamaTestSupport {

    private OllamaTestSupport() {}

    /** HEAD is enough to verify something listens on Ollama's HTTP port before we burn tokens in ChatModel calls. */
    static boolean canReachBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.connect();
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
