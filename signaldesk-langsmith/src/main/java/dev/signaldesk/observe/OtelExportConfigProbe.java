package dev.signaldesk.observe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Logs resolved OTLP export settings at startup (API key length only, never the secret).
 */
@ApplicationScoped
public class OtelExportConfigProbe {

    private static final Logger LOG = Logger.getLogger(OtelExportConfigProbe.class);
    private static final String HEADER_VALUE_PATTERN = "(?i)(?:^|,)\\s*%s=([^,]+)";

    void onStart(@Observes StartupEvent event) {
        Config config = ConfigProvider.getConfig();
        String tracesEndpoint =
                config.getOptionalValue("quarkus.otel.exporter.otlp.traces.endpoint", String.class)
                        .orElseGet(
                                () ->
                                        config.getOptionalValue(
                                                        "quarkus.otel.exporter.otlp.endpoint", String.class)
                                                .orElse("<unset>"));
        String tracesHeaders =
                config.getOptionalValue("quarkus.otel.exporter.otlp.traces.headers", String.class)
                        .orElseGet(
                                () ->
                                        config.getOptionalValue(
                                                        "quarkus.otel.exporter.otlp.headers", String.class)
                                                .orElse("<unset>"));
        String protocol =
                config.getOptionalValue("quarkus.otel.exporter.otlp.traces.protocol", String.class)
                        .orElse(
                                config.getOptionalValue(
                                                "quarkus.otel.exporter.otlp.protocol", String.class)
                                        .orElse("<unset>"));
        String project = resolveProject(config, tracesHeaders);
        boolean apiKeySet = isApiKeySet(config, tracesHeaders);

        LOG.infof(
                "OTLP traces: protocol=%s endpoint=%s project=%s apiKeySet=%s headerPreview=%s",
                protocol,
                tracesEndpoint,
                project,
                apiKeySet,
                redactHeaders(tracesHeaders));

        if (!apiKeySet) {
            LOG.warn(
                    "No LangSmith API key was detected in config or OTLP headers — spans may export without authentication and LangSmith will drop them.");
        }
        if (tracesEndpoint.contains("api.smith.langchain.com")
                && !tracesEndpoint.contains("eu.api")
                && !tracesEndpoint.contains("apac.api")
                && !tracesEndpoint.contains("aws.api")) {
            LOG.warn(
                    "OTLP endpoint looks like US default. EU accounts need https://eu.api.smith.langchain.com/otel — check OTEL_EXPORTER_OTLP_ENDPOINT.");
        }
        if (tracesEndpoint.contains("/v1/traces")) {
            LOG.warnf(
                    "OTLP endpoint ends with /v1/traces (%s). Quarkus http/protobuf appends v1/traces again — use the base /otel URL only (e.g. https://eu.api.smith.langchain.com/otel).",
                    tracesEndpoint);
        }
        if (!project.startsWith("<") && project.contains(" ")) {
            LOG.warnf(
                    "LANGSMITH_PROJECT contains spaces (%s). Comma-separated OTLP headers may truncate the project name — use a slug (quarkus-test-app) or set LANGSMITH_OTLP_HEADERS with a fully quoted header string.",
                    project);
        }
    }

    static boolean isApiKeyPresentInHeaders(String headers) {
        return extractHeaderValue(headers, "x-api-key").filter(v -> !v.isBlank()).isPresent();
    }

    static boolean isApiKeySet(Config config, String headers) {
        if (config.getOptionalValue("LANGSMITH_API_KEY", String.class).filter(v -> !v.isBlank()).isPresent()) {
            return true;
        }
        return isApiKeyPresentInHeaders(headers);
    }

    static String resolveProject(Config config, String headers) {
        return config.getOptionalValue("LANGSMITH_PROJECT", String.class)
                .filter(v -> !v.isBlank())
                .or(() -> extractHeaderValue(headers, "Langsmith-Project"))
                .orElse("<unset>");
    }

    static Optional<String> extractHeaderValue(String headers, String name) {
        if (headers == null || headers.isBlank() || "<unset>".equals(headers)) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(HEADER_VALUE_PATTERN.formatted(Pattern.quote(name))).matcher(headers);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group(1)).map(String::trim);
    }

    private static String redactHeaders(String headers) {
        if (headers == null || headers.isBlank() || "<unset>".equals(headers)) {
            return headers;
        }
        return headers.replaceAll("(?i)(x-api-key=)[^,]+", "$1***");
    }
}
