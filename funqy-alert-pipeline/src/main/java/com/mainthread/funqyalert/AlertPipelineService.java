package com.mainthread.funqyalert;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AlertPipelineService {

    private static final Set<String> SUPPORTED_ENVIRONMENTS = Set.of("prod", "staging", "dev");

    public RoutingDecision preview(AlertEnvelope alert) {
        AlertEnvelope ingested = ingest(alert);
        AlertEnvelope scored = score(ingested);
        return route(scored, "preview-alert", "localhost");
    }

    public AlertEnvelope ingest(AlertEnvelope alert) {
        AlertEnvelope normalized = new AlertEnvelope(alert);

        normalized.setService(normalizeRequired("service", alert.getService()));
        normalized.setEnvironment(normalizeEnvironment(alert.getEnvironment()));
        normalized.setRegion(normalizeRequired("region", alert.getRegion()));
        normalized.setSummary(normalizeRequired("summary", alert.getSummary()));

        if (alert.getErrorRatePercent() < 0) {
            throw new IllegalArgumentException("errorRatePercent must be zero or greater.");
        }
        if (alert.getImpactedCustomers() < 0) {
            throw new IllegalArgumentException("impactedCustomers must be zero or greater.");
        }

        normalized.setSeverity(classifySeverity(normalized));
        normalized.setDedupeKey(buildDedupeKey(normalized));
        normalized.setCheckpoints(List.of("validated", "ingested"));
        return normalized;
    }

    public AlertEnvelope score(AlertEnvelope alert) {
        AlertEnvelope scored = alert.getSeverity() == null || alert.getDedupeKey() == null
                ? ingest(alert)
                : new AlertEnvelope(alert);

        int riskScore = switch (scored.getSeverity()) {
            case "critical" -> 85;
            case "high" -> 68;
            case "medium" -> 42;
            default -> 18;
        };

        if (!scored.isAcknowledged()) {
            riskScore += 12;
        }

        riskScore += Math.min(15, scored.getImpactedCustomers() / 200);
        riskScore += Math.min(8, (int) Math.floor(scored.getErrorRatePercent()));
        scored.setRiskScore(Math.min(riskScore, 100));

        List<String> checkpoints = scored.getCheckpoints();
        if (!checkpoints.contains("scored")) {
            checkpoints.add("scored");
        }
        return scored;
    }

    public RoutingDecision route(AlertEnvelope alert, String eventId, String eventSource) {
        AlertEnvelope scored = alert.getRiskScore() == 0 ? score(alert) : new AlertEnvelope(alert);
        RoutingDecision decision = new RoutingDecision();

        decision.setService(scored.getService());
        decision.setEnvironment(scored.getEnvironment());
        decision.setRegion(scored.getRegion());
        decision.setSummary(scored.getSummary());
        decision.setSeverity(scored.getSeverity());
        decision.setRiskScore(scored.getRiskScore());
        decision.setDestinationTeam(selectTeam(scored));
        decision.setPageImmediately(scored.getRiskScore() >= 70);
        decision.setAcknowledgeWithinMinutes(ackDeadline(scored.getRiskScore()));
        decision.setRunbookUrl("https://runbooks.example.com/" + scored.getService() + "/" + scored.getSeverity());
        decision.setRationale(buildRationale(scored));
        decision.setTriggeringEventId(eventId);
        decision.setTriggeringEventSource(eventSource);

        List<String> checkpoints = scored.getCheckpoints();
        if (!checkpoints.contains("routed")) {
            checkpoints.add("routed");
        }
        decision.setCheckpoints(checkpoints);
        return decision;
    }

    private String normalizeRequired(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEnvironment(String value) {
        String environment = normalizeRequired("environment", value);
        if (!SUPPORTED_ENVIRONMENTS.contains(environment)) {
            throw new IllegalArgumentException("environment must be one of prod, staging, or dev.");
        }
        return environment;
    }

    private String classifySeverity(AlertEnvelope alert) {
        if ("prod".equals(alert.getEnvironment())
                && (alert.getErrorRatePercent() >= 5.0 || alert.getImpactedCustomers() >= 1_000)) {
            return "critical";
        }
        if (alert.getErrorRatePercent() >= 2.0 || alert.getImpactedCustomers() >= 250) {
            return "high";
        }
        if (alert.getErrorRatePercent() >= 0.5 || !alert.isAcknowledged()) {
            return "medium";
        }
        return "low";
    }

    private String buildDedupeKey(AlertEnvelope alert) {
        String summarySlug = alert.getSummary().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return alert.getService() + ":" + alert.getRegion() + ":" + summarySlug;
    }

    private String selectTeam(AlertEnvelope alert) {
        return switch (alert.getSeverity()) {
            case "critical" -> alert.getService() + "-oncall";
            case "high" -> alert.getService() + "-primary";
            case "medium" -> alert.getService() + "-triage";
            default -> alert.getService() + "-backlog";
        };
    }

    private int ackDeadline(int riskScore) {
        if (riskScore >= 85) {
            return 5;
        }
        if (riskScore >= 70) {
            return 10;
        }
        if (riskScore >= 45) {
            return 30;
        }
        return 240;
    }

    private String buildRationale(AlertEnvelope alert) {
        return "Route to " + selectTeam(alert)
                + " because " + alert.getEnvironment()
                + " is seeing "
                + alert.getErrorRatePercent()
                + "% errors with "
                + alert.getImpactedCustomers()
                + " impacted customers.";
    }
}
