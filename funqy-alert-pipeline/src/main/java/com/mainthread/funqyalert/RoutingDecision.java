package com.mainthread.funqyalert;

import java.util.ArrayList;
import java.util.List;

public class RoutingDecision {

    private String service;
    private String environment;
    private String region;
    private String summary;
    private String severity;
    private int riskScore;
    private String destinationTeam;
    private boolean pageImmediately;
    private int acknowledgeWithinMinutes;
    private String runbookUrl;
    private String rationale;
    private String triggeringEventId;
    private String triggeringEventSource;
    private List<String> checkpoints = new ArrayList<>();

    public RoutingDecision() {
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getDestinationTeam() {
        return destinationTeam;
    }

    public void setDestinationTeam(String destinationTeam) {
        this.destinationTeam = destinationTeam;
    }

    public boolean isPageImmediately() {
        return pageImmediately;
    }

    public void setPageImmediately(boolean pageImmediately) {
        this.pageImmediately = pageImmediately;
    }

    public int getAcknowledgeWithinMinutes() {
        return acknowledgeWithinMinutes;
    }

    public void setAcknowledgeWithinMinutes(int acknowledgeWithinMinutes) {
        this.acknowledgeWithinMinutes = acknowledgeWithinMinutes;
    }

    public String getRunbookUrl() {
        return runbookUrl;
    }

    public void setRunbookUrl(String runbookUrl) {
        this.runbookUrl = runbookUrl;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getTriggeringEventId() {
        return triggeringEventId;
    }

    public void setTriggeringEventId(String triggeringEventId) {
        this.triggeringEventId = triggeringEventId;
    }

    public String getTriggeringEventSource() {
        return triggeringEventSource;
    }

    public void setTriggeringEventSource(String triggeringEventSource) {
        this.triggeringEventSource = triggeringEventSource;
    }

    public List<String> getCheckpoints() {
        return checkpoints;
    }

    public void setCheckpoints(List<String> checkpoints) {
        this.checkpoints = checkpoints == null ? new ArrayList<>() : new ArrayList<>(checkpoints);
    }
}
