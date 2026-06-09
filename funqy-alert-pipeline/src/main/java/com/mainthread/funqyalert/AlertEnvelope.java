package com.mainthread.funqyalert;

import java.util.ArrayList;
import java.util.List;

public class AlertEnvelope {

    private String service;
    private String environment;
    private String region;
    private String summary;
    private double errorRatePercent;
    private int impactedCustomers;
    private boolean acknowledged;
    private String severity;
    private int riskScore;
    private String dedupeKey;
    private List<String> checkpoints = new ArrayList<>();

    public AlertEnvelope() {
    }

    public AlertEnvelope(AlertEnvelope other) {
        this.service = other.service;
        this.environment = other.environment;
        this.region = other.region;
        this.summary = other.summary;
        this.errorRatePercent = other.errorRatePercent;
        this.impactedCustomers = other.impactedCustomers;
        this.acknowledged = other.acknowledged;
        this.severity = other.severity;
        this.riskScore = other.riskScore;
        this.dedupeKey = other.dedupeKey;
        this.checkpoints = new ArrayList<>(other.checkpoints);
    }

    public void addCheckpoint(String checkpoint) {
        this.checkpoints.add(checkpoint);
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

    public double getErrorRatePercent() {
        return errorRatePercent;
    }

    public void setErrorRatePercent(double errorRatePercent) {
        this.errorRatePercent = errorRatePercent;
    }

    public int getImpactedCustomers() {
        return impactedCustomers;
    }

    public void setImpactedCustomers(int impactedCustomers) {
        this.impactedCustomers = impactedCustomers;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
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

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public List<String> getCheckpoints() {
        return checkpoints;
    }

    public void setCheckpoints(List<String> checkpoints) {
        this.checkpoints = checkpoints == null ? new ArrayList<>() : new ArrayList<>(checkpoints);
    }
}
